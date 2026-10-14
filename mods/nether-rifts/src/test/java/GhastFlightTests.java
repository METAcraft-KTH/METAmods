import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import nu.metacraft.nether_rifts.mob.BurningPatches;
import nu.metacraft.nether_rifts.mob.DeckFloatGoal;
import nu.metacraft.nether_rifts.mob.HeldItems;
import nu.metacraft.nether_rifts.mob.RiftEntities;
import nu.metacraft.nether_rifts.mob.WarGhast;
import nu.metacraft.nether_rifts.mob.WarGhast.Crew;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * How a Chained Ghast gets about with its deck: spawned on the ground it lifts its deck clear, it floats in a band over
 * the ground, and a summoned one finds a player on the ground and bombs them. Its deck drummer's War Beat, and how far
 * off it's drawn.
 */
public class GhastFlightTests {

	static void registerTests() {
		NetherRiftsTests.register("ghast_flight/egg_spawn_deck_clear", ctx -> {
			// From a spawn egg on flat ground (its feet on the floor, the deck under it), it lifts till the deck is clear
			// of the floor, and floats about with it clear from then on.
			BombardierTests.stoneFloor(ctx);
			var ghast = RiftEntities.WAR_GHAST.spawn(ctx.getLevel(), ctx.absolutePos(new BlockPos(12, 1, 12)), EntitySpawnReason.SPAWN_ITEM_USE);
			ctx.assertTrue(ghast != null, "the egg spawned nothing");
			double floor = ctx.absolutePos(new BlockPos(12, 1, 12)).getY();
			ctx.assertTrue(ghast.getY() - WarGhast.DECK_DROP >= floor - 1e-6, "the egg's ghast has its deck in the floor: feet at " + ghast.getY());
			var buried = new AtomicInteger();
			ctx.onEachTick(() -> {
				if (!ghast.fitsAt(ghast.position())) {
					buried.incrementAndGet();
				}
			});
			ctx.runAtTickTime(100, () -> {
				ctx.assertTrue(buried.get() == 0, "the deck was in blocks for " + buried.get() + " ticks");
				ctx.assertTrue(ghast.getY() - WarGhast.DECK_DROP >= floor - 1e-6, "the deck sank into the floor: feet at " + ghast.getY());
				ctx.killAllEntities();
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("ghast_flight/unsticks_from_ground", ctx -> {
			// Put with its deck in the ground (a summon at the floor), it lifts clear on its next tick.
			BombardierTests.stoneFloor(ctx);
			var ghast = ctx.spawn(RiftEntities.WAR_GHAST, new BlockPos(12, 2, 12));
			ctx.runAtTickTime(2, () -> {
				ctx.assertTrue(ghast.fitsAt(ghast.position()), "the ghast's deck is still in the floor: feet at " + ghast.getY());
				ctx.killAllEntities();
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("ghast_flight/float_band", ctx -> {
			// Floating about, it picks only points where it and its deck fit, and no more than max_float_height above the
			// ground under them.
			BombardierTests.stoneFloor(ctx);
			var ghast = ctx.spawn(RiftEntities.WAR_GHAST, new BlockPos(12, 12, 12));
			ghast.setNoAi(true);
			var goal = new DeckFloatGoal(ghast);
			int picked = 0;
			for (int i = 0; i < 200; i++) {
				ghast.getMoveControl().setWait();
				goal.start();
				var move = ghast.getMoveControl();
				if (!move.hasWanted()) {
					continue;
				}
				picked++;
				var to = new net.minecraft.world.phys.Vec3(move.getWantedX(), move.getWantedY(), move.getWantedZ());
				ctx.assertTrue(ghast.fitsAt(to), "it floats to " + to + ", where its deck doesn't fit");
				var ground = DeckFloatGoal.groundUnder(ctx.getLevel(), to);
				ctx.assertTrue(ground == null || to.y - ground <= WarGhast.MAX_FLOAT_HEIGHT + 1e-6, "it floats to " + to + ", " + (to.y - ground) + " over the ground");
			}
			ctx.assertTrue(picked > 100, "it found somewhere to float only " + picked + " times");
			ctx.killAllEntities();
			ctx.succeed();
		});
		NetherRiftsTests.register("ghast_flight/summoned_bombs_player", ctx -> {
			// As a player meets one: a summoned ghast (no rift) and a survival player on the ground across the room, nothing
			// set by hand. It takes the player for its target (vanilla's ghast only looks 4 blocks up and down; the deck
			// keeps this one higher) and makes a bombing run, its bombs landing near them.
			BombardierTests.stoneFloor(ctx);
			var player = StageTests.player(ctx);
			player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
			var spot = ctx.absolutePos(new BlockPos(18, 1, 18));
			player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
			var ghast = RiftEntities.WAR_GHAST.spawn(ctx.getLevel(), ctx.absolutePos(new BlockPos(5, 1, 5)), EntitySpawnReason.SPAWN_ITEM_USE);
			var targeted = new AtomicBoolean();
			ctx.onEachTick(() -> {
				player.setHealth(player.getMaxHealth());
				targeted.compareAndSet(false, ghast.getTarget() == player);
			});
			ctx.succeedWhen(() -> {
				ctx.assertTrue(targeted.get(), "the ghast never took the player for its target");
				ctx.assertTrue(BurningPatches.active(ctx.getLevel()).stream().anyMatch(p -> !p.warning()
						&& Math.hypot(p.at().x - player.getX(), p.at().z - player.getZ()) <= 10), "no bomb landed near the player yet");
				StageTests.remove(ctx, player);
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
			});
		});
		NetherRiftsTests.register("ghast_flight/deck_drummer_war_beat", ctx -> {
			// In a fight, the drummer on the deck slams the big beat on the world's drum clock: War Beat for the piglins
			// under the ghast and for the crew. A ghast whose drummer was killed buffs no one.
			BombardierTests.stoneFloor(ctx);
			var player = StageTests.player(ctx);
			var spot = ctx.absolutePos(new BlockPos(12, 1, 22));
			player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
			var drumming = ctx.spawn(RiftEntities.WAR_GHAST, new BlockPos(4, 9, 4));
			var silent = ctx.spawn(RiftEntities.WAR_GHAST, new BlockPos(20, 9, 4));
			drumming.setNoAi(true);
			silent.setNoAi(true);
			var under = ctx.spawn(EntityTypes.PIGLIN, new BlockPos(4, 1, 4));
			var underSilent = ctx.spawn(EntityTypes.PIGLIN, new BlockPos(20, 1, 4));
			under.setNoAi(true);
			underSilent.setNoAi(true);
			ctx.runAtTickTime(3, () -> silent.hitbox(Crew.DRUMMER).kill(ctx.getLevel()));
			// a whole drum cycle and then some
			ctx.runAtTickTime(110, () -> {
				ctx.assertTrue(under.hasEffect(MobEffects.STRENGTH) && under.hasEffect(MobEffects.SPEED), "the piglin under the drumming ghast has no War Beat");
				ctx.assertTrue(drumming.hitbox(Crew.SPEAR).hasEffect(MobEffects.STRENGTH), "the crew have no War Beat");
				ctx.assertFalse(underSilent.hasEffect(MobEffects.STRENGTH), "a ghast without its drummer gave War Beat");
				StageTests.remove(ctx, player);
				ctx.killAllEntities();
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("ghast_flight/seen_from_afar", ctx -> {
			// Every display of a ghast and a drummer (bones, held items, armour) is seen as far as the mob is tracked
			// (10 chunks: view range 2.5), not BIL's 64 blocks.
			var ghast = ctx.spawn(RiftEntities.WAR_GHAST, new BlockPos(12, 8, 12));
			var drummer = ctx.spawn(RiftEntities.WAR_DRUMMER, new BlockPos(4, 1, 4));
			ctx.runAtTickTime(2, () -> {
				for (var holder : java.util.List.of(ghast.getHolder(), drummer.getHolder())) {
					int displays = 0;
					for (var element : holder.getElements()) {
						if (element instanceof DisplayElement display) {
							displays++;
							ctx.assertTrue(display.getViewRange() == 2.5f, holder.getParent() + " has a display seen at only " + display.getViewRange());
						}
					}
					ctx.assertTrue(displays >= holder.getBones().length, holder.getParent() + " has " + displays + " displays");
				}
				ctx.assertTrue(HeldItems.viewRange(RiftEntities.WAR_GHAST) == 2.5f, "the ghast's view range");
				ctx.killAllEntities();
				ctx.succeed();
			});
		});
	}
}
