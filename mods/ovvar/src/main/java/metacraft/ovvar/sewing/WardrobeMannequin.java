package metacraft.ovvar.sewing;

import metacraft.ovvar.ModCommands;
import metacraft.ovvar.OvvarConfig;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Show on mannequin" from the wardrobe screen (design doc §2): a mannequin wearing a copy of the
 * player's ovve, two blocks in front of them. Unlike {@code /ovvar showcase} (a gamemaster
 * command, permanent displays) this button is anyone's to click, so it carries guardrails a
 * gamemaster command does not need:
 * <ul>
 *   <li>refused outright on a minigame server (view-only, like the rest of the screen);</li>
 *   <li>one per player — a second click discards the first, tracked by holding the entity itself
 *	   (not by scanning the world or re-looking it up by UUID, which would need a level tick to
 *	   catch up after it is spawned or discarded);</li>
 *   <li>gone on its own after {@value #LIFETIME_TICKS} ticks, or once the player is more than
 *	   {@value #REACH} blocks from it, or on disconnect — the same reach/idle shape
 *	   {@link StashSession} uses for its stand;</li>
 *   <li>gone at shutdown too ({@code SERVER_STOPPING}, mirroring {@link StashSession#init}), so
 *	   one is never saved into the level as an orphan; and tagged {@value #TAG} as belt and braces
 *	   against exactly that happening anyway (a crash skips the stopping event) — anything still
 *	   carrying the tag is swept from every level on the next {@code SERVER_STARTED};</li>
 *   <li>it can never die (and so never drop what it wears), on top of the copy shown on it having
 *	   had its {@code BUNDLE_CONTENTS} stripped first ({@link #show}) — an ovve is a bundle, and a
 *	   copy of a real one is a copy of whatever a player had stashed in its pockets too.</li>
 * </ul>
 */
public final class WardrobeMannequin {
	private WardrobeMannequin() {}

	private static final double REACH = 8.0;
	private static final long LIFETIME_TICKS = 60 * 20L;
	/** Marks every mannequin this shows, so a crash that skips {@code SERVER_STOPPING} is still cleaned up on the next start. */
	public static final String TAG = "ovvar_wardrobe_mannequin";

	private record Shown(Mannequin mannequin, long spawnedAt) {}

	private static final Map<UUID, Shown> BY_PLAYER = new HashMap<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(WardrobeMannequin::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> despawn(handler.player));
		ServerLifecycleEvents.SERVER_STOPPING.register(WardrobeMannequin::despawnAll);
		// Belt and braces beyond SERVER_STOPPING (a crash never fires it): anything still tagged
		// from a previous run, in any level, is an orphan and goes.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			for (ServerLevel level : server.getAllLevels()) {
				List<Entity> orphans = new ArrayList<>();
				for (Entity entity : level.getAllEntities()) if (entity.entityTags().contains(TAG)) orphans.add(entity);
				for (Entity entity : orphans) entity.discard();
			}
		});
		// Belt and braces beyond the stripped BUNDLE_CONTENTS: it simply cannot die, on this or any
		// other damage source (including /kill), so there is no death event left to drop equipment on.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			for (Shown shown : BY_PLAYER.values()) if (shown.mannequin() == entity) return false;
			return true;
		});
	}

	/** Shows {@code ovve} on a mannequin for {@code player}, or refuses (a minigame server) and returns why. */
	public static @Nullable String show(ServerPlayer player, ItemStack ovve) {
		if (OvvarConfig.get().stash().minigameServer()) return "Ovvar are view-only on this server: sew on a survival server";
		despawn(player);
		ItemStack copy = ovve.copy();
		copy.remove(DataComponents.BUNDLE_CONTENTS);
		Mannequin mannequin = ModCommands.spawnMannequinWearing(player, copy, 2);
		mannequin.setPermanentlyInvulnerable(true);
		mannequin.addTag(TAG);
		BY_PLAYER.put(player.getUUID(), new Shown(mannequin, player.level().getGameTime()));
		return null;
	}

	/** This player's mannequin's UUID, if any is currently tracked (for tests). */
	public static @Nullable UUID mannequinOf(ServerPlayer player) {
		Shown shown = BY_PLAYER.get(player.getUUID());
		return shown == null ? null : shown.mannequin().getUUID();
	}

	/** The mannequin entity itself, if any is currently tracked (for tests). */
	public static @Nullable Mannequin mannequinEntityOf(ServerPlayer player) {
		Shown shown = BY_PLAYER.get(player.getUUID());
		return shown == null ? null : shown.mannequin();
	}

	private static void despawn(ServerPlayer player) {
		Shown shown = BY_PLAYER.remove(player.getUUID());
		if (shown != null) shown.mannequin().discard();
	}

	/** Every tracked mannequin, gone; the map cleared — shutdown, so none is ever saved into the level. */
	private static void despawnAll(MinecraftServer server) {
		for (Shown shown : new ArrayList<>(BY_PLAYER.values())) shown.mannequin().discard();
		BY_PLAYER.clear();
	}

	private static void tick(MinecraftServer server) {
		if (BY_PLAYER.isEmpty()) return;
		for (UUID playerId : List.copyOf(BY_PLAYER.keySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			Shown shown = BY_PLAYER.get(playerId);
			if (player == null || shown == null || shown.mannequin().isRemoved()) {
				BY_PLAYER.remove(playerId);
				continue;
			}
			boolean tooOld = player.level().getGameTime() - shown.spawnedAt() > LIFETIME_TICKS;
			boolean tooFar = player.level() != shown.mannequin().level() || player.distanceToSqr(shown.mannequin()) > REACH * REACH;
			if (tooOld || tooFar) {
				shown.mannequin().discard();
				BY_PLAYER.remove(playerId);
			}
		}
	}
}
