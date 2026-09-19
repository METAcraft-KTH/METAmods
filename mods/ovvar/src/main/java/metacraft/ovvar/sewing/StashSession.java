package metacraft.ovvar.sewing;

import metacraft.ovvar.Ovvar;
import metacraft.ovvar.OvvarConfig;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.ModComponents;
import metacraft.ovvar.content.ModContent;
import metacraft.ovvar.content.OvveItem;
import metacraft.ovvar.content.PatchItem;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.store.OwnedSewing;
import metacraft.ovvar.store.StashConfig;
import metacraft.ovvar.store.Wardrobes;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Rotations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A stash session: the player picked a patch in {@code /ovvar stash} and gets a private armour
 * stand named after them, in a walking pose, wearing an ovve of theirs, two blocks in front of
 * them; hotbar slot 9 holds the chosen patch as a fake item (as many as the stash has) and slot 8
 * a pair of shears, both marked {@link ModComponents#SESSION} so they cannot be dropped or moved.
 * Sewing with the fake patch takes one from the stash; the shears unpick. The session ends when
 * the player walks off, idles too long, dies, leaves, or asks; the stand goes and the two hotbar
 * slots get their real contents back. Only the owner's clicks reach the stand ({@link StandSewing}).
 */
public final class StashSession {
	private static final int SHEARS_SLOT = 7, PATCH_SLOT = 8;
	private static final Map<UUID, StashSession> SESSIONS = new HashMap<>();
	private static final Map<UUID, UUID> BY_STAND = new HashMap<>();

	private final UUID player;
	private final UUID stand;
	private final ItemStack savedShearsSlot, savedPatchSlot;
	private Patches.Patch patch;
	private long lastAction;

	private StashSession(UUID player, UUID stand, ItemStack savedShearsSlot, ItemStack savedPatchSlot, Patches.Patch patch, long now) {
		this.player = player;
		this.stand = stand;
		this.savedShearsSlot = savedShearsSlot;
		this.savedPatchSlot = savedPatchSlot;
		this.patch = patch;
		this.lastAction = now;
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(StashSession::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> end(handler.player, null));
		// After a crash mid-session the fake items are still in the saved inventory: sweep them.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			var inventory = handler.player.getInventory();
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				if (inventory.getItem(i).has(ModComponents.SESSION)) inventory.setItem(i, ItemStack.EMPTY);
			}
		});
		ServerPlayerEvents.ALLOW_DEATH.register((player, source, amount) -> {
			end(player, "The thread snapped");
			return true;
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (server.getPlayerList() == null) return;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) end(player, null);
		});
		// The fake items never become items on the ground.
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, loadedFromDisk) ->
				!(entity instanceof ItemEntity item && item.getItem().has(ModComponents.SESSION)));
	}

	private static StashConfig config() {
		return OvvarConfig.get().stash();
	}

	// ---- lookup

	public static @Nullable StashSession of(ServerPlayer player) {
		return SESSIONS.get(player.getUUID());
	}

	public static boolean isSessionStand(ArmorStand stand) {
		return BY_STAND.containsKey(stand.getUUID());
	}

	/** May this player sew on this stand: their own session stand, or any stand when the config allows that. */
	public static boolean mayUse(ServerPlayer player, ArmorStand stand) {
		UUID owner = BY_STAND.get(stand.getUUID());
		if (owner != null) return owner.equals(player.getUUID());
		return config().anyStand();
	}

	public Patches.Patch patch() {
		return patch;
	}

	/** Something happened at the stand: the idle clock restarts, and the fake patch's count follows the stash. */
	public void touched(ServerPlayer p) {
		lastAction = p.level().getGameTime();
		refreshFake(p);
	}

	// ---- starting

	/** Opens a session on this patch (or switches a running one to it). */
	public static void start(ServerPlayer player, Patches.Patch patch, java.util.function.Consumer<String> refused) {
		if (!config().sessions()) {
			refused.accept("Private sewing sessions are off on this server");
			return;
		}
		String refusal = OwnedSewing.editingRefusal(player);
		if (refusal != null) {
			refused.accept(refusal);
			return;
		}
		if (!Wardrobes.loaded(player.getUUID())) {
			Wardrobes.fetch(player.getUUID());
			refused.accept("Your wardrobe is still loading, try again in a moment");
			return;
		}
		if (Wardrobes.current(player.getUUID()).count(patch) == 0) {
			refused.accept("No " + patch.name() + " in the stash");
			return;
		}
		StashSession running = of(player);
		if (running != null) {
			running.patch = patch;
			running.touched(player);
			player.sendOverlayMessage(Component.literal("Now sewing: " + patch.name()));
			return;
		}
		ServerLevel level = player.level();
		ArmorStand stand = new ArmorStand(EntityTypes.ARMOR_STAND, level);
		Vec3 forward = Vec3.directionFromRotation(0, player.getYRot()).normalize().scale(2);
		Vec3 at = player.position().add(forward);
		stand.setPos(at.x, at.y, at.z);
		stand.setYRot(player.getYRot() + 180);
		stand.setYBodyRot(player.getYRot() + 180);
		stand.setShowArms(true);
		stand.setNoBasePlate(true);
		stand.setPermanentlyInvulnerable(true);
		stand.setNoGravity(true);
		stand.setCustomName(Component.literal(player.getName().getString() + "'s ovve"));
		stand.setCustomNameVisible(true);
		// Mid-stride: one arm forward, one back, legs apart. Every cell stays reachable.
		stand.setHeadPose(new Rotations(-5, 0, 0));
		stand.setBodyPose(new Rotations(0, 0, 0));
		stand.setLeftArmPose(new Rotations(25, 0, -10));
		stand.setRightArmPose(new Rotations(-30, 0, 10));
		stand.setLeftLegPose(new Rotations(-22, 0, -3));
		stand.setRightLegPose(new Rotations(22, 0, 3));
		ItemStack ovve = new ItemStack(ModContent.ovve(chapterFor(player)));
		OvveItem.setOwner(ovve, player.getUUID());
		OvveItem.setTopUp(ovve, true);
		ovve.set(ModComponents.ON_STAND, true);
		OvveItem.refresh(ovve);
		stand.setItemSlot(EquipmentSlot.LEGS, ovve);
		level.addFreshEntity(stand);

		var inventory = player.getInventory();
		StashSession session = new StashSession(player.getUUID(), stand.getUUID(),
				inventory.getItem(SHEARS_SLOT).copy(), inventory.getItem(PATCH_SLOT).copy(), patch, level.getGameTime());
		SESSIONS.put(player.getUUID(), session);
		BY_STAND.put(stand.getUUID(), player.getUUID());
		ItemStack shears = new ItemStack(Items.SHEARS);
		shears.set(ModComponents.SESSION, true);
		shears.set(DataComponents.CUSTOM_NAME, Component.literal("Unpick (right-click a patch on the stand)").withStyle(ChatFormatting.YELLOW));
		shears.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true));
		inventory.setItem(SHEARS_SLOT, shears);
		session.refreshFake(player);
		inventory.setSelectedSlot(PATCH_SLOT);
		player.sendSystemMessage(Component.literal("Aim the patch at your ovve on the stand and right-click to sew; the shears unpick. Walk away or use /ovvar stash done to finish.")
				.withStyle(ChatFormatting.GRAY));
	}

	/** The chapter of the ovve they wear, else one in their inventory, else one they have a design for, else the first. */
	private static Chapter chapterFor(ServerPlayer player) {
		if (player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof OvveItem worn) return worn.chapter;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).getItem() instanceof OvveItem carried) return carried.chapter;
		}
		var designs = Wardrobes.current(player.getUUID()).designs();
		return designs.isEmpty() ? Chapter.values()[0] : designs.keySet().iterator().next();
	}

	/** The fake patch in slot 9: as many as the stash holds of the chosen patch, none when it is out. */
	private void refreshFake(ServerPlayer p) {
		int count = Wardrobes.current(player).count(patch);
		ItemStack fake = ItemStack.EMPTY;
		if (count > 0) {
			fake = new ItemStack(ModContent.patchItem(patch), Math.min(count, 64));
			fake.set(ModComponents.SESSION, true);
		}
		p.getInventory().setItem(PATCH_SLOT, fake);
		if (count == 0) p.sendOverlayMessage(Component.literal("No " + patch.name() + " left in the stash; pick another in /ovvar stash"));
	}

	// ---- ending

	/** Ends the player's session if they have one; {@code why} is told to them (null: silently). */
	public static void end(ServerPlayer player, @Nullable String why) {
		StashSession session = SESSIONS.remove(player.getUUID());
		if (session == null) return;
		BY_STAND.remove(session.stand);
		ServerLevel level = player.level();
		if (level.getEntity(session.stand) instanceof ArmorStand stand) stand.discard();
		var inventory = player.getInventory();
		if (inventory.getItem(SHEARS_SLOT).has(ModComponents.SESSION)) inventory.setItem(SHEARS_SLOT, session.savedShearsSlot);
		if (inventory.getItem(PATCH_SLOT).has(ModComponents.SESSION) || inventory.getItem(PATCH_SLOT).isEmpty()) inventory.setItem(PATCH_SLOT, session.savedPatchSlot);
		// A fake that was moved elsewhere (it cannot be, but belt and braces) is swept out.
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).has(ModComponents.SESSION)) inventory.setItem(i, ItemStack.EMPTY);
		}
		if (why != null) player.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.GRAY));
	}

	private static void tick(MinecraftServer server) {
		if (SESSIONS.isEmpty()) return;
		for (StashSession session : new ArrayList<>(SESSIONS.values())) {
			ServerPlayer player = server.getPlayerList().getPlayer(session.player);
			if (player == null) {
				SESSIONS.remove(session.player);
				BY_STAND.remove(session.stand);
				continue;
			}
			ArmorStand stand = player.level().getEntity(session.stand) instanceof ArmorStand s ? s : null;
			if (stand == null) {
				end(player, "Your stand is gone; the session is over");
				continue;
			}
			double reach = config().sessionReach();
			if (player.distanceToSqr(stand) > reach * reach) {
				end(player, "You walked away from your ovve; the session is over");
				continue;
			}
			if (player.level().getGameTime() - session.lastAction > config().sessionSeconds() * 20L) {
				end(player, "Sewing session over (idle)");
				continue;
			}
			String refusal = OwnedSewing.editingRefusal(player);
			if (refusal != null) {
				end(player, refusal + "; the session is over");
				continue;
			}
			// The hotbar is the session's: only the patch (9) and the shears (8) can be selected.
			int selected = player.getInventory().getSelectedSlot();
			if (selected != PATCH_SLOT && selected != SHEARS_SLOT) {
				player.getInventory().setSelectedSlot(PATCH_SLOT);
				player.connection.send(new ClientboundSetHeldSlotPacket(PATCH_SLOT));
			}
			// The fakes stay where they were put: cleared off the cursor by the click mixin, they come back here.
			ItemStack inSlot = player.getInventory().getItem(PATCH_SLOT);
			if (!inSlot.isEmpty() && !inSlot.has(ModComponents.SESSION)) {
				Ovvar.LOGGER.debug("[ovvar] stash: {} put something in the session slot; ending", player.getName().getString());
				end(player, "The sewing slot was used for something else; the session is over");
				continue;
			}
			if (inSlot.isEmpty() && Wardrobes.current(session.player).count(session.patch) > 0) session.refreshFake(player);
			if (!player.getInventory().getItem(SHEARS_SLOT).has(ModComponents.SESSION) && player.getInventory().getItem(SHEARS_SLOT).isEmpty()) {
				ItemStack shears = new ItemStack(Items.SHEARS);
				shears.set(ModComponents.SESSION, true);
				player.getInventory().setItem(SHEARS_SLOT, shears);
			}
		}
	}

	/** For the status command. */
	public static List<String> describeAll(MinecraftServer server) {
		List<String> out = new ArrayList<>();
		for (StashSession s : SESSIONS.values()) {
			ServerPlayer p = server.getPlayerList().getPlayer(s.player);
			out.add((p == null ? s.player.toString() : p.getName().getString()) + " sewing " + s.patch.name());
		}
		return out;
	}
}
