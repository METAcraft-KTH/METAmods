import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import se.metacraft.nether_rifts.mob.GeneralTactics;
import se.metacraft.nether_rifts.mob.GeneralTactics.Move;
import se.metacraft.nether_rifts.mob.PiglinGeneral;
import se.metacraft.nether_rifts.mob.RiftEntities;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** The Piglin General's fight: one move at a time, his mace swing's arc, and his spin and the dizzy after it. */
public class GeneralTests {

	/** Where he stands in the room, and how far his pen's walls are from him: room for a shooter 8 blocks off. */
	private static final BlockPos CENTRE = new BlockPos(12, 1, 12);
	private static final int PEN = 10;
	/** How far off the shooters stand. */
	private static final double SHOT = 8;

	static void registerTests() {
		NetherRiftsTests.register("general/swing_hits_in_front", ctx -> {
			// One player 3 blocks in front of him (south: he faces yaw 0) and one 3 blocks behind; the swing hits only the front.
			var general = general(ctx);
			var front = player(ctx, general, 3);
			var back = player(ctx, general, -3);
			general.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, front);
			general.tactics().start(Move.SWING);
			ctx.assertTrue(general.tactics().move() == Move.SWING, "the swing didn't start: " + general.tactics().move());
			// held only once nothing before finish can fail, so the hold is always let go
			holdNormal(ctx);
			ctx.runAfterDelay(GeneralTactics.SWING_HIT_TICK + 1, () -> finish(ctx, () -> {
				ctx.assertTrue(front.getHealth() < front.getMaxHealth(), "the swing missed the player in front of him");
				ctx.assertTrue(front.getHealth() <= 10.5f, "the player in front has " + front.getHealth() + " of 20 left, on " + ctx.getLevel().getDifficulty());
				var push = front.getDeltaMovement();
				var away = front.position().subtract(general.position()).horizontal().normalize();
				ctx.assertTrue(push.x * away.x + push.z * away.z > 0.5, "the player in front wasn't pushed away from him: " + push);
				ctx.assertTrue(back.getHealth() == back.getMaxHealth(), "the swing hit the player behind him: " + back.getHealth());
				ctx.assertTrue(back.getDeltaMovement().horizontalDistance() < 0.01, "the player behind him was pushed: " + back.getDeltaMovement());
			}, front, back));
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
		NetherRiftsTests.register("general/spin_reflects_arrow", ctx -> {
			// A player 8 blocks in front of him shoots at him mid-spin: the arrow comes back, his now, and hits the player.
			var general = general(ctx);
			var shooter = player(ctx, general, SHOT);
			holdNormal(ctx);
			general.tactics().start(Move.SPIN);
			var arrow = shoot(ctx, general, shooter);
			var firstHurt = new AtomicReference<DamageSource>();
			ctx.onEachTick(() -> {
				if (firstHurt.get() == null && shooter.getHealth() < shooter.getMaxHealth()) {
					firstHurt.set(shooter.getLastDamageSource());
				}
			});
			ctx.runAfterDelay(30, () -> finish(ctx, () -> {
				var source = firstHurt.get();
				ctx.assertTrue(source != null, "the shooter wasn't hurt; the arrow is at " + arrow.position() + ", moving " + arrow.getDeltaMovement());
				ctx.assertTrue(source.getDirectEntity() == arrow, "the shooter was first hurt by " + source.getDirectEntity() + ", not the arrow");
				ctx.assertTrue(source.getEntity() == general, "the arrow that hit the shooter was " + source.getEntity() + "'s, not the General's");
				ctx.assertTrue(general.getHealth() == general.getMaxHealth(), "the General was hurt: " + general.getHealth());
			}, shooter));
		});
		NetherRiftsTests.register("general/arrow_hurts_when_not_spinning", ctx -> {
			// The same arrow, shot while he chases, hurts him.
			var general = general(ctx);
			var shooter = player(ctx, general, SHOT);
			ctx.assertTrue(general.tactics().move() == Move.CHASE, "he isn't chasing: " + general.tactics().move());
			holdNormal(ctx);
			shoot(ctx, general, shooter);
			ctx.runAfterDelay(10, () -> finish(ctx, () -> {
				ctx.assertTrue(general.getHealth() < general.getMaxHealth(), "the arrow didn't hurt him; he is " + general.tactics().move());
				ctx.assertTrue(shooter.getHealth() == shooter.getMaxHealth(), "the shooter was hurt: " + shooter.getHealth());
			}, shooter));
		});
		NetherRiftsTests.register("general/reflect_without_owner", ctx -> {
			// An arrow nobody shot (as from a dispenser) comes at him mid-spin and flies back the way it came.
			var general = general(ctx);
			general.tactics().start(Move.SPIN);
			var from = new Vec3(general.getX(), chest(general), general.getZ() + SHOT);
			var arrow = new Arrow(ctx.getLevel(), from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
			var incoming = aim(arrow, general);
			ctx.getLevel().addFreshEntity(arrow);
			var back = new AtomicBoolean(false);
			ctx.onEachTick(() -> {
				var moving = arrow.getDeltaMovement();
				if (moving.lengthSqr() > 1 && moving.normalize().dot(incoming) < -0.9) {
					back.set(true);
				}
			});
			ctx.runAfterDelay(20, () -> finish(ctx, () -> {
				ctx.assertTrue(back.get(), "the arrow didn't turn back: it is at " + arrow.position() + ", moving " + arrow.getDeltaMovement());
				ctx.assertTrue(general.getHealth() == general.getMaxHealth(), "the General was hurt: " + general.getHealth());
			}));
		});
		NetherRiftsTests.register("general/reflected_trident_stays_the_players", ctx -> {
			// A trident thrown at him mid-spin comes back at full speed, still its thrower's: one trident, theirs to pick up,
			// none lost or copied.
			var general = general(ctx);
			var thrower = player(ctx, general, SHOT);
			general.tactics().start(Move.SPIN);
			var trident = new ThrownTrident(ctx.getLevel(), thrower, new ItemStack(Items.TRIDENT));
			var incoming = aim(trident, general);
			ctx.getLevel().addFreshEntity(trident);
			var back = new AtomicBoolean(false);
			ctx.onEachTick(() -> {
				var moving = trident.getDeltaMovement();
				if (moving.lengthSqr() > 1 && moving.normalize().dot(incoming) < 0) {
					back.set(true);
				}
			});
			// by 20 ticks it is back and down, and his drift hasn't brought the spin onto the thrower yet
			ctx.runAfterDelay(20, () -> finish(ctx, () -> {
				var room = general.getBoundingBox().inflate(32);
				var tridents = ctx.getLevel().getEntitiesOfClass(ThrownTrident.class, room, t -> true);
				var dropped = ctx.getLevel().getEntitiesOfClass(ItemEntity.class, room, item -> item.getItem().is(Items.TRIDENT));
				ctx.assertTrue(back.get(), "the trident didn't turn back: it is at " + trident.position() + ", moving " + trident.getDeltaMovement());
				ctx.assertTrue(tridents.size() == 1 && tridents.getFirst() == trident, "tridents about: " + tridents);
				ctx.assertTrue(dropped.isEmpty(), "trident items about: " + dropped);
				ctx.assertTrue(trident.getOwner() == thrower, "the trident is " + trident.getOwner() + "'s, not its thrower's");
				ctx.assertTrue(trident.pickup == AbstractArrow.Pickup.ALLOWED, "the trident can't be picked up: " + trident.pickup);
			}, thrower));
		});
		NetherRiftsTests.register("general/shot_spins_early", ctx -> {
			// Come in with his spin cooling down, he doesn't spin before spin_early_cooldown; shot just before it, he
			// spins once it has passed (the player can't be hurt, so his swings don't end the test).
			var general = general(ctx);
			var shooter = player(ctx, general, SHOT);
			shooter.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 400, 4));
			var spin = general.tuning().spin();
			long arrived = ctx.getTick();
			var windupAt = new AtomicInteger(-1);
			ctx.onEachTick(() -> {
				if (windupAt.get() < 0 && general.tactics().move() == Move.WINDUP) {
					windupAt.set((int) (ctx.getTick() - arrived));
				}
			});
			int shotAt = spin.earlyCooldown() - 5;
			ctx.runAfterDelay(shotAt, () -> shoot(ctx, general, shooter));
			ctx.runAfterDelay(spin.earlyCooldown() + spin.earlyWindow(), () -> finish(ctx, () -> {
				ctx.assertTrue(windupAt.get() >= 0, "shot at tick " + shotAt + ", he didn't spin; he is " + general.tactics().move()
						+ ", " + general.distanceTo(shooter) + " blocks off, " + general.getHealth() + " health");
				ctx.assertTrue(windupAt.get() >= spin.earlyCooldown(), "he wound up a spin " + windupAt.get() + " ticks in, before spin_early_cooldown");
			}, shooter));
		});
		NetherRiftsTests.register("general/spin_hits_ring", ctx -> {
			// One turn of the spin hurts a player 3 blocks off for spin_damage, and not one 5 blocks off.
			var general = general(ctx);
			var near = player(ctx, general, 3);
			var far = player(ctx, general, -5);
			holdNormal(ctx);
			general.tactics().start(Move.SPIN);
			ctx.runAfterDelay(GeneralTactics.SPIN_LOOP_TICKS, () -> finish(ctx, () -> {
				float damage = general.tuning().spin().damage();
				float lost = near.getMaxHealth() - near.getHealth();
				ctx.assertTrue(Math.abs(lost - damage) <= 0.5f, "the player 3 blocks off lost " + lost + ", not " + damage);
				ctx.assertTrue(far.getHealth() == far.getMaxHealth(), "the player 5 blocks off was hurt: " + far.getHealth());
			}, near, far));
		});
		NetherRiftsTests.register("general/spin_length", ctx -> {
			// After the windup he spins between spin_min_ticks and spin_max_ticks, stands dizzy dizzy_ticks, then chases.
			// Far from every player all the while, he stays: a General never despawns for distance.
			var general = general(ctx);
			var tuning = general.tuning();
			general.tactics().start(Move.WINDUP);
			var moves = new ArrayList<Move>();
			var startedAt = new ArrayList<Long>();
			moves.add(Move.WINDUP);
			startedAt.add(ctx.getTick());
			ctx.onEachTick(() -> {
				var move = general.tactics().move();
				if (move != moves.getLast()) {
					moves.add(move);
					startedAt.add(ctx.getTick());
				}
			});
			ctx.runAfterDelay(GeneralTactics.WINDUP_TICKS + tuning.spin().maxTicks() + tuning.dizzyTicks() + 5, () -> finish(ctx, () -> {
				ctx.assertTrue(moves.size() >= 4 && moves.subList(0, 4).equals(List.of(Move.WINDUP, Move.SPIN, Move.DIZZY, Move.CHASE)),
						"the moves went " + moves);
				long windup = startedAt.get(1) - startedAt.get(0);
				long spin = startedAt.get(2) - startedAt.get(1);
				long dizzy = startedAt.get(3) - startedAt.get(2);
				ctx.assertTrue(windup == GeneralTactics.WINDUP_TICKS, "the windup lasted " + windup + " ticks");
				ctx.assertTrue(spin >= tuning.spin().minTicks() && spin <= tuning.spin().maxTicks(), "the spin lasted " + spin + " ticks");
				ctx.assertTrue(dizzy == tuning.dizzyTicks(), "the dizzy lasted " + dizzy + " ticks");
				ctx.assertTrue(general.isAlive() && !general.isRemoved(), "the General went");
				ctx.assertTrue(!general.isPersistenceRequired() && !general.removeWhenFarAway(1e6 * 1e6), "a General would despawn far from players");
			}));
		});
		NetherRiftsTests.register("general/dizzy_takes_more", ctx -> {
			// The same hit of 10, armour aside, takes 10 from him chasing and 15 dizzy.
			var general = general(ctx);
			var player = player(ctx, general, -3);
			holdNormal(ctx);
			finish(ctx, () -> {
				general.getAttribute(Attributes.ARMOR).setBaseValue(0);
				general.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
				var source = ctx.getLevel().damageSources().playerAttack(player);
				float before = general.getHealth();
				general.hurtServer(ctx.getLevel(), source, 10);
				float chasing = before - general.getHealth();
				general.damageCooldownTime = 0;
				general.tactics().start(Move.DIZZY);
				ctx.assertTrue(general.dizzy(), "he isn't dizzy in DIZZY");
				before = general.getHealth();
				general.hurtServer(ctx.getLevel(), source, 10);
				float dizzy = before - general.getHealth();
				ctx.assertTrue(Math.abs(chasing - 10) <= 0.5f, "chasing, he lost " + chasing + " to a hit of 10");
				ctx.assertTrue(Math.abs(dizzy - 15) <= 0.5f, "dizzy, he lost " + dizzy + " to a hit of 10");
			}, player);
		});
		NetherRiftsTests.register("general/dies_mid_spin", ctx -> {
			// Killed mid-spin, he stops spinning; an arrow shot at his body isn't turned back.
			var general = general(ctx);
			var shooter = player(ctx, general, SHOT);
			holdNormal(ctx);
			general.tactics().start(Move.SPIN);
			// the checks along the way are kept for finish, which lets go of the difficulty whatever they find
			var spinningAlive = new AtomicBoolean(false);
			var afterDeath = new AtomicReference<String>("not looked at");
			ctx.runAfterDelay(5, () -> {
				spinningAlive.set(general.spinning());
				general.hurtServer(ctx.getLevel(), ctx.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
			});
			var back = new AtomicBoolean(false);
			var arrow = new AtomicReference<Arrow>();
			ctx.runAfterDelay(6, () -> {
				afterDeath.set("spinning " + general.spinning() + ", move " + general.tactics().move() + ", animation " + general.move());
				arrow.set(shoot(ctx, general, shooter));
			});
			ctx.onEachTick(() -> {
				var shot = arrow.get();
				if (shot != null && shot.getDeltaMovement().z > 1) {
					back.set(true);
				}
			});
			ctx.runAfterDelay(20, () -> finish(ctx, () -> {
				ctx.assertTrue(spinningAlive.get(), "he wasn't spinning in SPIN");
				ctx.assertTrue(afterDeath.get().equals("spinning false, move CHASE, animation null"), "dead, he is " + afterDeath.get());
				ctx.assertTrue(!back.get(), "the arrow at his body turned back");
				ctx.assertTrue(shooter.getHealth() == shooter.getMaxHealth(), "the shooter was hurt: " + shooter.getHealth());
			}, shooter));
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

	/** An arrow shot by {@code shooter} at the General's chest, as hard as a full bow draw. */
	static Arrow shoot(GameTestHelper ctx, PiglinGeneral general, ServerPlayer shooter) {
		var arrow = new Arrow(ctx.getLevel(), shooter, new ItemStack(Items.ARROW), null);
		aim(arrow, general);
		ctx.getLevel().addFreshEntity(arrow);
		return arrow;
	}

	/** Shoots {@code projectile} from where it is at the General's chest, at full bow speed; the way it goes. */
	static Vec3 aim(Projectile projectile, PiglinGeneral general) {
		var way = new Vec3(general.getX(), chest(general), general.getZ()).subtract(projectile.position()).normalize();
		projectile.shoot(way.x, way.y, way.z, 3.0f, 0f);
		return way;
	}

	static double chest(PiglinGeneral general) {
		return general.getY() + general.getBbHeight() * 0.5;
	}

	/** Tests holding the difficulty at normal, and the difficulty before the first of them: the tests share one batch. */
	private static final Set<GameTestHelper> HOLDING_NORMAL = new HashSet<>();
	private static Difficulty difficultyBefore;

	/**
	 * Holds the server at normal difficulty, where mob damage to players is the config's, till {@link #finish}. Tests
	 * run side by side, so the difficulty goes back only when the last test holding it is done.
	 */
	static void holdNormal(GameTestHelper ctx) {
		var server = ctx.getLevel().getServer();
		if (HOLDING_NORMAL.isEmpty()) {
			difficultyBefore = server.getWorldData().getDifficulty();
		}
		HOLDING_NORMAL.add(ctx);
		server.setDifficulty(Difficulty.NORMAL, true);
	}

	/**
	 * Runs a test's last checks, then, pass or fail, lets go of the difficulty (if it held it), removes these players
	 * and cleans up; the test succeeds if the checks passed.
	 */
	static void finish(GameTestHelper ctx, Runnable checks, ServerPlayer... players) {
		try {
			checks.run();
		} finally {
			if (HOLDING_NORMAL.remove(ctx) && HOLDING_NORMAL.isEmpty()) {
				ctx.getLevel().getServer().setDifficulty(difficultyBefore, true);
			}
			for (var player : players) {
				StageTests.remove(ctx, player);
			}
			cleanUp(ctx);
		}
		ctx.succeed();
	}

	static void cleanUp(GameTestHelper ctx) {
		ctx.killAllEntities();
		for (var pos : BlockPos.betweenClosed(CENTRE.offset(-PEN, 0, -PEN), CENTRE.offset(PEN, 3, PEN))) {
			ctx.setBlock(pos, Blocks.AIR);
		}
	}
}
