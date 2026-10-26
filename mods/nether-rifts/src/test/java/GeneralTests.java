import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.Blocks;
import se.metacraft.nether_rifts.mob.GeneralTactics;
import se.metacraft.nether_rifts.mob.GeneralTactics.Move;
import se.metacraft.nether_rifts.mob.PiglinGeneral;
import se.metacraft.nether_rifts.mob.RiftEntities;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** The Piglin General's fight: one move at a time, and his mace swing's arc. */
public class GeneralTests {

	/** Where he stands in the room, and how far his pen's walls are from him. */
	private static final BlockPos CENTRE = new BlockPos(12, 1, 12);
	private static final int PEN = 5;

	static void registerTests() {
		NetherRiftsTests.register("general/swing_hits_in_front", ctx -> {
			// One player 3 blocks in front of him (south: he faces yaw 0) and one 3 blocks behind; the swing hits only the front.
			var general = general(ctx);
			var front = player(ctx, general, 3);
			var back = player(ctx, general, -3);
			general.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, front);
			// mob damage scales with difficulty: on normal, it is the config's 10
			var difficulty = ctx.getLevel().getDifficulty();
			ctx.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
			general.tactics().start(Move.SWING);
			ctx.assertTrue(general.tactics().move() == Move.SWING, "the swing didn't start: " + general.tactics().move());
			ctx.runAfterDelay(GeneralTactics.SWING_HIT_TICK + 1, () -> {
				ctx.assertTrue(front.getHealth() < front.getMaxHealth(), "the swing missed the player in front of him");
				ctx.assertTrue(front.getHealth() <= 10.5f, "the player in front has " + front.getHealth() + " of 20 left, on " + ctx.getLevel().getDifficulty());
				var push = front.getDeltaMovement();
				var away = front.position().subtract(general.position()).horizontal().normalize();
				ctx.assertTrue(push.x * away.x + push.z * away.z > 0.5, "the player in front wasn't pushed away from him: " + push);
				ctx.assertTrue(back.getHealth() == back.getMaxHealth(), "the swing hit the player behind him: " + back.getHealth());
				ctx.assertTrue(back.getDeltaMovement().horizontalDistance() < 0.01, "the player behind him was pushed: " + back.getDeltaMovement());
				ctx.getLevel().getServer().setDifficulty(difficulty, true);
				StageTests.remove(ctx, front);
				StageTests.remove(ctx, back);
				cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("general/swing_starts_in_range", ctx -> {
			// A player 3.5 blocks in front of him (inside swing_start_range) is his target, and he swings at them.
			var general = general(ctx);
			var player = player(ctx, general, 3.5);
			var swungAt = new AtomicInteger(-1);
			ctx.onEachTick(() -> {
				if (swungAt.get() < 0 && general.tactics().move() == Move.SWING) {
					swungAt.set((int) ctx.getTick());
				}
			});
			ctx.runAfterDelay(40, () -> {
				ctx.assertTrue(general.getTarget() == player, "the player isn't his target: " + general.getTarget());
				ctx.assertTrue(swungAt.get() >= 0, "no swing in 40 ticks; he is " + general.tactics().move() + ", " + general.distanceTo(player) + " blocks off");
				StageTests.remove(ctx, player);
				cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("general/one_move_at_a_time", ctx -> {
			// A roar queued mid-swing waits for the swing to end, then starts straight away.
			var general = general(ctx);
			general.tactics().start(Move.SWING);
			long started = ctx.getTick();
			var after = new AtomicReference<Move>();
			var endedAt = new AtomicInteger(-1);
			ctx.runAfterDelay(2, () -> general.tactics().queueRoar());
			ctx.onEachTick(() -> {
				var move = general.tactics().move();
				if (after.get() == null && move != Move.SWING) {
					after.set(move);
					endedAt.set((int) (ctx.getTick() - started));
				}
			});
			ctx.runAfterDelay(GeneralTactics.SWING_TICKS + 5, () -> {
				ctx.assertTrue(after.get() == Move.ROAR, "after the swing came " + after.get() + ", not the queued roar");
				ctx.assertTrue(endedAt.get() >= GeneralTactics.SWING_TICKS, "the swing gave way after " + endedAt.get() + " ticks, not " + GeneralTactics.SWING_TICKS);
				cleanUp(ctx);
				ctx.succeed();
			});
		});
	}

	/** A General of no rift at the room's centre, facing south (yaw 0), in a barrier pen so he can't walk after a neighbouring test's player. */
	static PiglinGeneral general(GameTestHelper ctx) {
		for (int dx = -PEN; dx <= PEN; dx++) {
			for (int dz = -PEN; dz <= PEN; dz++) {
				if (Math.abs(dx) == PEN || Math.abs(dz) == PEN) {
					for (int y = 0; y < 4; y++) {
						ctx.setBlock(CENTRE.offset(dx, y, dz), Blocks.BARRIER);
					}
				}
			}
		}
		var general = RiftEntities.PIGLIN_GENERAL.create(ctx.getLevel(), EntitySpawnReason.SPAWN_ITEM_USE);
		var at = ctx.absolutePos(CENTRE);
		general.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		general.setYHeadRot(0);
		general.setYBodyRot(0);
		general.finalizeSpawn(ctx.getLevel(), ctx.getLevel().getCurrentDifficultyAt(at), EntitySpawnReason.SPAWN_ITEM_USE, null);
		// a monster with only far players (other tests', over 128 blocks off) despawns
		general.setPersistenceRequired();
		ctx.getLevel().addFreshEntity(general);
		return general;
	}

	/** A survival player, ready to be hurt, this many blocks in front of him (south; behind, if negative). */
	static ServerPlayer player(GameTestHelper ctx, PiglinGeneral general, double ahead) {
		var player = StageTests.player(ctx);
		player.teleportTo(general.getX(), general.getY(), general.getZ() + ahead);
		DrummerTests.ready(player);
		return player;
	}

	static void cleanUp(GameTestHelper ctx) {
		ctx.killAllEntities();
		for (var pos : BlockPos.betweenClosed(CENTRE.offset(-PEN, 0, -PEN), CENTRE.offset(PEN, 3, PEN))) {
			ctx.setBlock(pos, Blocks.AIR);
		}
	}
}
