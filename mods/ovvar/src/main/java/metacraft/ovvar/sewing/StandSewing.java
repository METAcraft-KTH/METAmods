package metacraft.ovvar.sewing;

import metacraft.ovvar.Ovvar;
import metacraft.ovvar.OvvarConfig;
import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.Looks;
import metacraft.ovvar.content.ModContent;
import metacraft.ovvar.content.OvveItem;
import metacraft.ovvar.content.PatchItem;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Spot;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sewing on an armour stand. Put the ovve on a stand, hold a patch, look at the stand: the patch
 * previews on the cell you're looking at, right-click sews it.
 * Empty hand on a sewn patch takes it back. The preview is a transient component on the stand's
 * ovve, so everyone sees it, and nothing is persisted until the click.
 *
 * Aiming is {@link StandAim}: the look ray against the stand's posed armour model — our own ray,
 * not the client's, which only knows the stand's narrow hitbox (clicks on the arms arrive as
 * clicks on air or the block behind and are taken too). Sneaking aims
 * at the far face of the part you look at (the inside of an arm or leg, the back). With the
 * stitching minigame on (config), the click opens {@link SewingGame} instead of sewing at once.
 */
public final class StandSewing {
	private StandSewing() {}

	static final double REACH = 6.0;

	/** {@code /ovvar aimlog}: every click on a stand with a patch, and every aim change, logged with the numbers behind it. */
	public static boolean aimLog;

	private static StandAim.Hit aim(ServerPlayer player, ArmorStand stand) {
		return StandAim.aim(player.getEyePosition(), player.getViewVector(1.0f), stand, player.isShiftKeyDown(), REACH);
	}

	private static void logAim(String what, ServerPlayer player, ArmorStand stand, StandAim.Hit hit, Spot spot) {
		if (!aimLog) return;
		Ovvar.LOGGER.info("[ovvar aim] {} {} eye={} view={} sneak={} | stand {} yaw={} arms R{} L{} legs R{} L{} | hit={} -> {}",
				player.getName().getString(), what, fmt(player.getEyePosition()), fmt(player.getViewVector(1.0f)), player.isShiftKeyDown(),
				fmt(stand.position()), stand.yBodyRot, stand.getRightArmPose(), stand.getLeftArmPose(), stand.getRightLegPose(), stand.getLeftLegPose(),
				hit == null ? "miss" : hit.part() + "/" + (hit.spot() == null ? "no cell" : hit.spot().id()) + "@" + fmt(hit.where()),
				spot == null ? "nothing" : spot.id());
	}

	private static String fmt(Vec3 v) {
		return String.format(java.util.Locale.ROOT, "(%.3f %.3f %.3f)", v.x, v.y, v.z);
	}

	/** What a player is currently previewing: stand and placement. */
	private record Aim(UUID stand, Placement placement) {}
	private static final Map<UUID, Aim> AIMS = new HashMap<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(StandSewing::tick);
		// A stand's hitbox is its 0.5-wide body: the client only reports a click on an entity when
		// its own ray hits that box, so a click on an arm sticking out of it arrives as a click on
		// air or on the block behind. All three are taken; our ray decides what was aimed at.
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer) || !(entity instanceof ArmorStand stand)) {
				return InteractionResult.PASS;
			}
			if (!(stand.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof OvveItem)) return InteractionResult.PASS;
			return click(serverPlayer, stand, aim(serverPlayer, stand));
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
			return clickThrough(serverPlayer, REACH);
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
			return clickThrough(serverPlayer, player.getEyePosition().distanceTo(hit.getLocation()));
		});
	}

	/** A click the client did not attribute to a stand: the nearest stand cell our ray meets within {@code maxDistance}, if any. */
	private static InteractionResult clickThrough(ServerPlayer player, double maxDistance) {
		ItemStack held = player.getMainHandItem();
		if (!(held.getItem() instanceof PatchItem) && !held.isEmpty()) return InteractionResult.PASS;
		ArmorStand best = null;
		StandAim.Hit bestHit = null;
		double bestDistance = maxDistance;
		for (ArmorStand stand : player.level().getEntitiesOfClass(ArmorStand.class, player.getBoundingBox().inflate(REACH))) {
			if (!(stand.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof OvveItem)) continue;
			StandAim.Hit hit = aim(player, stand);
			if (hit == null) continue;
			double distance = player.getEyePosition().distanceTo(hit.where());
			if (distance < bestDistance) { best = stand; bestHit = hit; bestDistance = distance; }
		}
		if (best == null) return InteractionResult.PASS;
		return click(player, best, bestHit);
	}

	/** A right-click on a stand wearing an ovve, aimed as given: sew the held patch, or unpick with an empty hand. */
	private static InteractionResult click(ServerPlayer player, ArmorStand stand, StandAim.Hit aimed) {
		ItemStack ovve = stand.getItemBySlot(EquipmentSlot.LEGS);
		ItemStack held = player.getMainHandItem();
		ServerLevel level = (ServerLevel) player.level();
		if (held.getItem() instanceof PatchItem patchItem) {
			Spot spot = aimed == null ? null : spotFor(aimed.spot(), patchItem.patch);
			logAim("click " + patchItem.patch.id(), player, stand, aimed, spot);
			if (spot == null) return InteractionResult.FAIL;
			Placement placement = new Placement(spot, patchItem.patch);
			if (OvvarConfig.get().sewingMinigame()) {
				SewingGame.start(player, stand, placement, patchItem.patch);
			} else {
				finish(player, stand, placement, patchItem, aimed.where());
			}
			return InteractionResult.SUCCESS;
		}
		if (held.is(ConventionalItemTags.SHEAR_TOOLS) && aimed != null && aimed.spot() != null) {
			Spot spot = aimed.spot();
			Placement there = Looks.at(ovve, spot);
			if (there == null && Spot.SEAT_CELLS.contains(spot)) { spot = Spot.SEAT; there = Looks.at(ovve, spot); }
			if (there == null) return InteractionResult.PASS;
			Looks.unpick(ovve, spot);
			ItemStack back = new ItemStack(ModContent.patchItem(there.patch()));
			if (!player.getInventory().add(back)) player.drop(back, false);
			celebrate(level, aimed.where(), false);
			player.sendOverlayMessage(Component.literal(there.patch().name() + " unpicked"));
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	/**
	 * Sews for real: the placement goes on the stand's ovve, the preview is dropped, one patch
	 * leaves the hand (outside creative), particles at {@code where}.
	 */
	static void finish(ServerPlayer player, ArmorStand stand, Placement placement, PatchItem patchItem, Vec3 where) {
		ItemStack ovve = stand.getItemBySlot(EquipmentSlot.LEGS);
		if (!(ovve.getItem() instanceof OvveItem)) throw new IllegalStateException("[ovvar] finishing a seam on a stand without an ovve");
		Looks.sew(ovve, placement);
		Looks.setPreview(ovve, null);
		AIMS.remove(player.getUUID());
		if (!player.isCreative()) player.getMainHandItem().shrink(1);
		celebrate((ServerLevel) player.level(), where, true);
		player.sendOverlayMessage(Component.literal(patchItem.patch.name() + " sewn on the " + placement.spot().label()));
	}

	/** Where a patch lands when aimed at a cell: a seat patch aimed at either seat cell goes on the seat; else the cell, if it takes the patch. */
	private static Spot spotFor(Spot aimed, Patches.Patch patch) {
		if (aimed == null) return null;
		if (patch.seat()) return Spot.SEAT_CELLS.contains(aimed) ? Spot.SEAT : null;
		return patch.fits(aimed) ? aimed : null;
	}

	private static void celebrate(ServerLevel level, Vec3 where, boolean sewn) {
		level.sendParticles(sewn ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.POOF, where.x, where.y, where.z, 12, 0.15, 0.15, 0.15, 0.02);
		level.playSound(null, where.x, where.y, where.z, SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0f, sewn ? 1.2f : 0.8f);
	}

	/** Every tick: a player holding a patch previews it on the stand cell they look at. */
	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Aim previous = AIMS.get(player.getUUID());
			Aim current = null;
			ItemStack aimedOvve = null;
			ArmorStand aimedStand = null;
			StandAim.Hit lastHit = null;
			if (player.getMainHandItem().getItem() instanceof PatchItem patchItem) {
				for (ArmorStand stand : player.level().getEntitiesOfClass(ArmorStand.class, player.getBoundingBox().inflate(REACH))) {
					ItemStack ovve = stand.getItemBySlot(EquipmentSlot.LEGS);
					if (!(ovve.getItem() instanceof OvveItem)) continue;
					StandAim.Hit hit = aim(player, stand);
					aimedStand = stand;
					lastHit = hit;
					if (hit == null) continue;
					Spot spot = spotFor(hit.spot(), patchItem.patch);
					if (spot != null) {
						current = new Aim(stand.getUUID(), new Placement(spot, patchItem.patch));
						aimedOvve = ovve;
						if (server.getTickCount() % 10 == 0) player.sendOverlayMessage(Component.literal("→ " + spot.label()));
					} else if (server.getTickCount() % 20 == 0) {
						player.sendOverlayMessage(Component.literal(hit.spot() == null
								? "Nothing goes on the " + hit.part()
								: patchItem.patch.name() + " doesn't go on the " + hit.spot().label()));
					}
					break;
				}
			}
			if (previous != null && !previous.equals(current)) {
				// Clear before setting: the new spot may be on the same stand.
				clearPreview(player.level(), previous.stand);
				AIMS.remove(player.getUUID());
			}
			if (current != null && !current.equals(previous)) {
				Looks.setPreview(aimedOvve, current.placement);
				AIMS.put(player.getUUID(), current);
				Ovvar.LOGGER.debug("[ovvar] {} aims {} at {}", player.getName().getString(), current.placement.patch(), current.placement.spot().id());
			}
			if (aimLog && (current == null) != (previous == null) && aimedStand != null) {
				logAim(current == null ? "aim lost" : "aim", player, aimedStand, lastHit, current == null ? null : current.placement.spot());
			}
		}
	}

	private static void clearPreview(net.minecraft.world.level.Level level, UUID standId) {
		if (level instanceof ServerLevel server && server.getEntity(standId) instanceof ArmorStand stand) {
			ItemStack ovve = stand.getItemBySlot(EquipmentSlot.LEGS);
			if (ovve.getItem() instanceof OvveItem) Looks.setPreview(ovve, null);
		}
	}
}
