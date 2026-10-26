import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.Nullable;
import se.metacraft.nether_rifts.mob.GhastBombardier;
import se.metacraft.nether_rifts.mob.WarDrummer;
import se.metacraft.nether_rifts.raid.RiftMobs;
import se.metacraft.nether_rifts.raid.RiftRaid;
import se.metacraft.nether_rifts.raid.Slot;
import se.metacraft.nether_rifts.rift.Rift;
import se.metacraft.nether_rifts.rift.Stage;
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
import net.minecraft.world.level.GameType;
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
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** The Piglin General's fight: one move at a time, his mace swing's arc, his spin and the dizzy after it, and his roars and guard. */
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
		NetherRiftsTests.register("general/roar_at_thresholds", ctx -> {
			// A Large raid's General, the rest of wave 7 gone. Hurt by a participant below two thirds, he roars, and half a
			// wave of his guard comes through the rift at that participant: ranged, melee and elite halved (Large's last
			// wave's 2, 5 and 2, before the bonus rolls), a Grenadier and a Drummer on a hoglin, and no ghast or strider.
			// Below a third he roars again; there are only two. (Callbacks schedule nothing: the test's own schedule can't
			// take more while it runs one.)
			var inside = ShapeTests.frame(ctx, 10, 10, Blocks.OBSIDIAN);
			ctx.setBlock(inside, Blocks.FIRE);
			penRoom(ctx);
			var player = heldParticipant(ctx);
			Runnable done = () -> riftCleanUp(ctx, player);
			var level = ctx.getLevel();
			var riftRef = new AtomicReference<Rift>();
			var generalRef = new AtomicReference<PiglinGeneral>();
			var before = new HashSet<Entity>();
			var sizeBefore = new AtomicInteger();
			// the guard's targets, as they first come; and whether he roars after the last hit
			var targets = new AtomicReference<String>();
			var roaredAgain = new AtomicBoolean(false);
			var lastHit = new AtomicBoolean(false);
			ctx.onEachTick(() -> {
				var rift = riftRef.get();
				if (rift != null && targets.get() == null && rift.waveSize > sizeBefore.get()) {
					targets.set(misTargeted(RiftRaid.aliveMobs(level, rift), before, player));
				}
				if (lastHit.get() && generalRef.get().tactics().move() == Move.ROAR) {
					roaredAgain.set(true);
				}
			});
			int firstHit = 3;
			int secondHit = firstHit + GeneralTactics.ROAR_TICKS + 6;
			int thirdHit = secondHit + GeneralTactics.ROAR_TICKS + 5;
			ctx.runAtTickTime(firstHit, () -> guarded(done, () -> {
				var rift = FormingTests.riftIn(ctx);
				rift.tear(level);
				rift.setStage(level, Stage.RAID);
				RiftRaid.startWave(level, rift, 7);
				var general = (PiglinGeneral) RiftRaid.aliveMobs(level, rift).stream().filter(e -> e instanceof PiglinGeneral).findFirst()
						.orElseThrow(() -> new AssertionError("no General in Large wave 7"));
				RiftRaid.aliveMobs(level, rift).stream().filter(e -> e != general).forEach(e -> e.discard());
				before.add(general);
				sizeBefore.set(rift.waveSize);
				generalRef.set(general);
				riftRef.set(rift);
				hurtTo(ctx, general, player, 0.6);
			}));
			ctx.runAtTickTime(firstHit + GeneralTactics.ROAR_TICKS + 5, () -> guarded(done, () -> {
				var rift = riftRef.get();
				var general = generalRef.get();
				var guard = added(RiftRaid.aliveMobs(level, rift), before);
				ctx.assertTrue(general.roarsUsed() == 1, "below two thirds he has roared " + general.roarsUsed() + " times");
				ctx.assertTrue("".equals(targets.get()), "the guard isn't all at the participant as it comes: " + targets.get());
				// and still, its brains having ticked a while since: the anger sticks, a mount's as well as its rider's
				var still = misTargeted(RiftRaid.aliveMobs(level, rift), before, player);
				ctx.assertTrue(still.isEmpty(), "the guard isn't all at the participant some " + (GeneralTactics.ROAR_TICKS - GeneralTactics.ROAR_SPAWN_TICK) + " ticks after it came: " + still);
				ctx.assertTrue(rift.waveSize == sizeBefore.get() + guard.size(), "the wave grew from " + sizeBefore.get() + " to " + rift.waveSize + " with " + guard.size() + " come");
				var roots = guard.stream().filter(e -> e.getVehicle() == null).toList();
				long ranged = count(roots, e -> e instanceof Piglin p && !(e instanceof GhastBombardier) && p.getMainHandItem().is(Items.CROSSBOW));
				long melee = count(roots, e -> e instanceof Piglin p && !(e instanceof GhastBombardier) && p.getMainHandItem().is(Items.GOLDEN_SPEAR)
						|| e instanceof Hoglin h && h.getPassengers().stream().noneMatch(r -> r instanceof WarDrummer));
				long elite = count(roots, e -> e instanceof PiglinBrute);
				String all = describe(guard);
				ctx.assertTrue(ranged >= 1 && melee >= 2 && elite >= 1, "the guard brought ranged " + ranged + ", melee " + melee + ", elite " + elite + ": " + all);
				ctx.assertTrue(count(guard, e -> e instanceof GhastBombardier) == 1, "not one Grenadier: " + all);
				var drummers = guard.stream().filter(e -> e instanceof WarDrummer).toList();
				ctx.assertTrue(drummers.size() == 1 && drummers.getFirst().getVehicle() instanceof Hoglin, "not one Drummer on a hoglin: " + all);
				ctx.assertTrue(count(guard, e -> e instanceof Ghast || e instanceof Strider) == 0, "ghasts or striders came: " + all);
			}));
			ctx.runAtTickTime(secondHit, () -> guarded(done, () -> {
				var rift = riftRef.get();
				before.addAll(RiftRaid.aliveMobs(level, rift));
				sizeBefore.set(rift.waveSize);
				hurtTo(ctx, generalRef.get(), player, 0.3);
			}));
			ctx.runAtTickTime(thirdHit, () -> guarded(done, () -> {
				var rift = riftRef.get();
				var general = generalRef.get();
				var guard = added(RiftRaid.aliveMobs(level, rift), before);
				ctx.assertTrue(general.roarsUsed() == 2, "below a third he has roared " + general.roarsUsed() + " times");
				ctx.assertTrue(!guard.isEmpty() && rift.waveSize == sizeBefore.get() + guard.size(),
						"the second roar brought " + guard.size() + "; the wave grew from " + sizeBefore.get() + " to " + rift.waveSize);
				var still = misTargeted(RiftRaid.aliveMobs(level, rift), before, player);
				ctx.assertTrue(still.isEmpty(), "the second guard isn't all at the participant some " + (GeneralTactics.ROAR_TICKS - GeneralTactics.ROAR_SPAWN_TICK) + " ticks after it came: " + still);
				hurtTo(ctx, general, player, 0.1);
				lastHit.set(true);
			}));
			ctx.runAtTickTime(thirdHit + GeneralTactics.ROAR_TICKS, () -> {
				try {
					ctx.assertTrue(!roaredAgain.get() && generalRef.get().roarsUsed() == 2, "at a tenth he roared a third time");
				} finally {
					done.run();
				}
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("general/own_raid_spares_him", ctx -> {
			// His own raid can't hurt him: a crossbow piglin of his rift, blow or bolt, neither hurts him nor brings his
			// spin on early nor makes him roar, though he stands just above two thirds of his health.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var level = ctx.getLevel();
				var rift = FormingTests.riftIn(ctx);
				var general = (PiglinGeneral) RiftMobs.spawn(level, rift.id(), Slot.CAPTAIN, ctx.absolutePos(CENTRE), Vec3.ZERO).getFirst();
				general.setNoAi(true);
				var piglin = (Piglin) RiftMobs.spawn(level, rift.id(), Slot.RANGED, ctx.absolutePos(CENTRE.offset(0, 0, 8)), Vec3.ZERO).getFirst();
				piglin.setNoAi(true);
				general.getAttribute(Attributes.ARMOR).setBaseValue(0);
				general.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
				general.setHealth(0.7f * general.getMaxHealth());
				float health = general.getHealth();
				var sources = level.damageSources();
				var probe = new Arrow(level, piglin, new ItemStack(Items.ARROW), null);
				ctx.assertFalse(general.hurtServer(level, sources.mobAttack(piglin), 0.5f * general.getMaxHealth()), "his rift's piglin's blow hurt him");
				ctx.assertFalse(general.hurtServer(level, sources.arrow(probe, piglin), 0.5f * general.getMaxHealth()), "his rift's piglin's bolt hurt him");
				var bolt = new Arrow(level, piglin, new ItemStack(Items.ARROW), null);
				bolt.setPos(general.getX(), chest(general), general.getZ() + 4);
				aim(bolt, general);
				level.addFreshEntity(bolt);
				ctx.runAfterDelay(10, () -> {
					try {
						ctx.assertTrue(general.getHealth() == health, "he went from " + health + " to " + general.getHealth());
						ctx.assertTrue(general.tactics().lastProjectileHit() == Long.MIN_VALUE, "a shot from his rift counts toward his early spin");
						ctx.assertTrue(general.roarsUsed() == 0 && general.tactics().roarsPending() == 0, "he roared or has a roar queued");
						ctx.assertTrue(bolt.getZ() < general.getZ(), "the bolt didn't fly on past him: it is at " + bolt.position());
					} finally {
						ctx.killAllEntities();
						FormingTests.cleanUp(ctx);
					}
					ctx.succeed();
				});
			});
		});
		NetherRiftsTests.register("general/reflection_passes_his_guard", ctx -> {
			// An arrow he turns back mid-spin is his, so it flies through one of his guard, stepped into its way, and hits the shooter.
			var general = general(ctx);
			var shooter = player(ctx, general, SHOT);
			holdNormal(ctx);
			var side = UUID.randomUUID();
			// of one rift: tagged once in the level, as a rift that doesn't exist would keep them out at load
			general.setAttached(RiftMobs.RIFT, side);
			var guard = ctx.spawn(net.minecraft.world.entity.EntityTypes.PIGLIN, CENTRE.offset(3, 0, 4));
			guard.setNoAi(true);
			guard.setAttached(RiftMobs.RIFT, side);
			general.tactics().start(Move.SPIN);
			var arrow = shoot(ctx, general, shooter);
			var stepped = new AtomicBoolean(false);
			var firstHurt = new AtomicReference<DamageSource>();
			ctx.onEachTick(() -> {
				// out of the way of the arrow coming in; into it once it's his
				if (!stepped.get() && arrow.getOwner() == general) {
					guard.teleportTo(general.getX(), general.getY(), general.getZ() + SHOT - 2);
					stepped.set(true);
				}
				if (firstHurt.get() == null && shooter.getHealth() < shooter.getMaxHealth()) {
					firstHurt.set(shooter.getLastDamageSource());
				}
			});
			ctx.runAfterDelay(30, () -> finish(ctx, () -> {
				ctx.assertTrue(stepped.get(), "the arrow never became his; it is at " + arrow.position());
				ctx.assertTrue(guard.getHealth() == guard.getMaxHealth(), "his arrow hurt his guard: " + guard.getHealth());
				var source = firstHurt.get();
				ctx.assertTrue(source != null && source.getDirectEntity() == arrow && source.getEntity() == general,
						"the shooter wasn't hit by his arrow; it is at " + arrow.position() + ", first hurt by " + (source == null ? null : source.getDirectEntity()));
			}, shooter));
		});
		NetherRiftsTests.register("general/big_hit_queues_two_roars", ctx -> {
			// One hit from full to a quarter crosses both thresholds: two roars, back to back.
			var general = general(ctx);
			var starts = new ArrayList<Long>();
			ctx.onEachTick(() -> {
				if (general.tactics().move() == Move.ROAR && general.tactics().moveTicks() == 0) {
					starts.add(ctx.getTick());
				}
			});
			hurtTo(ctx, general, null, 0.25);
			ctx.runAfterDelay(2 * GeneralTactics.ROAR_TICKS + 10, () -> finish(ctx, () -> {
				ctx.assertTrue(general.roarsUsed() == 2, "he has roared " + general.roarsUsed() + " times");
				ctx.assertTrue(starts.size() == 2 && starts.get(1) - starts.get(0) == GeneralTactics.ROAR_TICKS,
						"the roars started at ticks " + starts + ", not " + GeneralTactics.ROAR_TICKS + " apart");
				ctx.assertTrue(general.tactics().move() != Move.ROAR, "he still roars: " + general.tactics().moveTicks() + " ticks in");
			}));
		});
		NetherRiftsTests.register("general/roar_without_rift", ctx -> {
			// A General of no rift (a spawn egg's), hurt to a half, roars and nothing comes; nor for one whose rift is gone.
			var general = general(ctx);
			var player = player(ctx, general, -3);
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 400, 4));
			var room = general.getBoundingBox().inflate(PEN);
			var roared = new AtomicInteger();
			ctx.onEachTick(() -> {
				if (general.tactics().move() == Move.ROAR && general.tactics().moveTicks() == 0) {
					roared.incrementAndGet();
				}
			});
			hurtTo(ctx, general, player, 0.5);
			ctx.runAfterDelay(GeneralTactics.ROAR_TICKS + 5, () -> guarded(() -> {
				StageTests.remove(ctx, player);
				cleanUp(ctx);
			}, () -> {
				var about = ctx.getLevel().getEntitiesOfClass(LivingEntity.class, room, e -> e != general && e != player);
				ctx.assertTrue(roared.get() == 1, "he roared " + roared.get() + " times");
				ctx.assertTrue(about.isEmpty(), "his roar brought " + about);
				// as if the rift he came through had collapsed
				general.setAttached(RiftMobs.RIFT, UUID.randomUUID());
				hurtTo(ctx, general, player, 0.3);
			}));
			ctx.runAfterDelay(2 * GeneralTactics.ROAR_TICKS + 10, () -> finish(ctx, () -> {
				var about = ctx.getLevel().getEntitiesOfClass(LivingEntity.class, room, e -> e != general && e != player);
				ctx.assertTrue(roared.get() == 2, "of a rift that's gone, he roared " + (roared.get() - 1) + " times");
				ctx.assertTrue(about.isEmpty(), "his roar of a rift that's gone brought " + about);
			}, player));
		});
		NetherRiftsTests.register("general/roars_survive_reload", ctx -> {
			// He roared once, then the server restarted: his roars used come back from his save, so going on below two
			// thirds he doesn't roar again; his move starts over at the chase.
			var general = general(ctx);
			hurtTo(ctx, general, null, 0.6);
			var registries = ctx.getLevel().registryAccess();
			var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
			general.saveWithoutId(saved);
			var tag = saved.buildResult();
			var reloaded = RiftEntities.PIGLIN_GENERAL.create(ctx.getLevel(), EntitySpawnReason.LOAD);
			reloaded.load(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
			reloaded.setUUID(UUID.randomUUID());
			general.discard();
			ctx.getLevel().addFreshEntity(reloaded);
			var moveThen = reloaded.tactics().move();
			var hit = new AtomicBoolean(false);
			var roared = new AtomicBoolean(false);
			ctx.onEachTick(() -> {
				if (hit.get() && reloaded.tactics().move() == Move.ROAR) {
					roared.set(true);
				}
			});
			ctx.runAfterDelay(1, () -> {
				reloaded.setHealth(0.65f * reloaded.getMaxHealth());
				hurtTo(ctx, reloaded, null, 0.55);
				hit.set(true);
			});
			ctx.runAfterDelay(GeneralTactics.ROAR_TICKS, () -> finish(ctx, () -> {
				ctx.assertTrue(tag.getIntOr("roars_used", -1) == 1, "his save holds roars_used " + tag.getIntOr("roars_used", -1));
				ctx.assertTrue(reloaded.roarsUsed() == 1, "reloaded, he has roared " + reloaded.roarsUsed() + " times");
				ctx.assertTrue(moveThen == Move.CHASE, "reloaded, he is in " + moveThen);
				ctx.assertTrue(!roared.get(), "reloaded at 0.65 and hurt to 0.55, he roared again");
			}));
		});
		NetherRiftsTests.register("general/queued_roar_survives_reload", ctx -> {
			// Hurt below two thirds as he spins, his roar waits on the spin and the dizzy; saved dizzy, it's still to play
			// when he's loaded, and he roars it. Saved again mid-roar before his guard comes, it's still to play.
			var general = general(ctx);
			hurtTo(ctx, general, null, 0.6);
			general.tactics().start(Move.DIZZY);
			var tag = saved(ctx, general);
			var reloaded = RiftEntities.PIGLIN_GENERAL.create(ctx.getLevel(), EntitySpawnReason.LOAD);
			reloaded.load(TagValueInput.create(ProblemReporter.DISCARDING, ctx.getLevel().registryAccess(), tag));
			reloaded.setUUID(UUID.randomUUID());
			general.discard();
			ctx.getLevel().addFreshEntity(reloaded);
			var roared = new AtomicBoolean(false);
			var midRoar = new AtomicInteger(-1);
			ctx.onEachTick(() -> {
				var tactics = reloaded.tactics();
				if (tactics.move() == Move.ROAR) {
					roared.set(true);
					if (midRoar.get() < 0 && tactics.moveTicks() > 0 && tactics.moveTicks() < GeneralTactics.ROAR_SPAWN_TICK) {
						midRoar.set(saved(ctx, reloaded).getIntOr("roars_queued", -1));
					}
				}
			});
			ctx.runAfterDelay(GeneralTactics.ROAR_TICKS + 5, () -> finish(ctx, () -> {
				ctx.assertTrue(tag.getIntOr("roars_queued", -1) == 1, "saved dizzy, his save holds roars_queued " + tag.getIntOr("roars_queued", -1));
				ctx.assertTrue(roared.get(), "reloaded with a roar to play, he never roared");
				ctx.assertTrue(midRoar.get() == 1, "saved mid-roar before his guard, his save holds roars_queued " + midRoar.get());
				ctx.assertTrue(reloaded.roarsUsed() == 1, "reloaded, he has roared " + reloaded.roarsUsed() + " times");
				ctx.assertTrue(reloaded.tactics().roarsPending() == 0, "he still has " + reloaded.tactics().roarsPending() + " roars to play");
			}));
		});
		NetherRiftsTests.register("general/guard_falls_back_to_nearest", ctx -> {
			// A General nobody has hurt yet (or one whose attackers went with a restart) sends his guard at the participant
			// nearest him, every one of them.
			FormingTests.lightFrame(ctx, 2, 3);
			penRoom(ctx);
			var near = StageTests.player(ctx);
			var far = StageTests.player(ctx);
			for (var player : List.of(near, far)) {
				DrummerTests.ready(player);
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4));
			}
			ctx.runAtTickTime(3, () -> {
				var level = ctx.getLevel();
				var rift = FormingTests.riftIn(ctx);
				rift.tear(level);
				rift.setStage(level, Stage.RAID);
				var at = ctx.absolutePos(new BlockPos(12, 1, 14));
				var general = RiftEntities.PIGLIN_GENERAL.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
				general.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
				level.addFreshEntity(general);
				near.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 3.5);
				far.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() - 6.5);
				try {
					ctx.assertTrue(general.attackers().isEmpty(), "nobody hurt him, yet he has attackers: " + general.attackers());
					var guard = RiftRaid.spawnGuard(level, rift, general);
					ctx.assertTrue(!guard.isEmpty(), "no guard came");
					var wrong = misTargeted(guard, Set.of(), near);
					ctx.assertTrue(wrong.isEmpty(), "the guard isn't all at the participant nearest him: " + wrong);
				} finally {
					StageTests.remove(ctx, far);
					riftCleanUp(ctx, near);
				}
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("general/guard_skips_creative", ctx -> {
			// A creative player who hurt him and stands nearest him gets none of his guard: it all comes at the survival
			// player farther off.
			FormingTests.lightFrame(ctx, 2, 3);
			penRoom(ctx);
			var creative = StageTests.player(ctx);
			var survival = StageTests.player(ctx);
			DrummerTests.ready(survival);
			survival.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4));
			creative.setGameMode(GameType.CREATIVE);
			ctx.runAtTickTime(3, () -> {
				var level = ctx.getLevel();
				var rift = FormingTests.riftIn(ctx);
				rift.tear(level);
				rift.setStage(level, Stage.RAID);
				var at = ctx.absolutePos(new BlockPos(12, 1, 14));
				var general = RiftEntities.PIGLIN_GENERAL.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
				general.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
				level.addFreshEntity(general);
				creative.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 3.5);
				survival.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() - 6.5);
				try {
					general.hurtServer(level, level.damageSources().playerAttack(creative), 1f);
					ctx.assertTrue(general.attackers().contains(creative.getUUID()), "the creative player's hit didn't count: " + general.attackers());
					var guard = RiftRaid.spawnGuard(level, rift, general);
					ctx.assertTrue(!guard.isEmpty(), "no guard came");
					var wrong = misTargeted(guard, Set.of(), survival);
					ctx.assertTrue(wrong.isEmpty(), "the guard isn't all at the survival player: " + wrong);
				} finally {
					StageTests.remove(ctx, creative);
					riftCleanUp(ctx, survival);
				}
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("general/mounted_drummer_drums", ctx -> {
			// A rift's Drummer on a hoglin (the General's guard) with a player 6 blocks off: through one drum cycle it stays
			// on, never walks, drums, and its big beat buffs a rift piglin near. It sits where an adult piglin would.
			FormingTests.lightFrame(ctx, 2, 3);
			penRoom(ctx);
			var player = StageTests.player(ctx);
			DrummerTests.ready(player);
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4));
			var drummer = new AtomicReference<WarDrummer>();
			var hoglin = new AtomicReference<Hoglin>();
			var piglin = new AtomicReference<Piglin>();
			var strikes = new AtomicInteger();
			var walked = new AtomicBoolean(false);
			var seat = new AtomicReference<Double>();
			WarDrummer.StrikeListener listener = (level, x, y, z, volume) -> {
				var d = drummer.get();
				if (d != null && level == ctx.getLevel() && Math.hypot(x - d.getX(), z - d.getZ()) < 0.5) {
					strikes.incrementAndGet();
				}
			};
			ctx.onEachTick(() -> {
				var d = drummer.get();
				if (d == null) {
					return;
				}
				if (d.getHolder().getAnimator().isPlaying("walk")) {
					walked.set(true);
				}
				if (seat.get() == null && d.getVehicle() == hoglin.get()) {
					seat.set(d.getY() - hoglin.get().getY());
				}
			});
			int cycle = WarDrummer.bigBeatStart() + Mth.positiveCeilDiv(WarDrummer.BIG_BEAT_TICKS, WarDrummer.BEAT_TICKS) * WarDrummer.BEAT_TICKS;
			// it comes 5 ticks before a cycle starts, so one cycle has its beats and its big beat
			int comes = 3 + Math.floorMod(-WarDrummer.cycleAt(ctx.getLevel().getGameTime() + 3) - 5, cycle);
			ctx.runAtTickTime(comes, () -> {
				var level = ctx.getLevel();
				var rift = FormingTests.riftIn(ctx);
				var spawned = RiftRaid.spawnOne(level, rift, Slot.MOUNTED_DRUMMER);
				hoglin.set((Hoglin) spawned.stream().filter(e -> e instanceof Hoglin).findFirst().orElseThrow(() -> new AssertionError("no hoglin in " + spawned)));
				var h = hoglin.get();
				player.teleportTo(h.getX() + 6, h.getY(), h.getZ());
				var near = (Piglin) RiftMobs.spawn(level, rift.id(), Slot.RANGED, BlockPos.containing(h.getX() + 3, h.getY(), h.getZ()), Vec3.ZERO).getFirst();
				near.setNoAi(true);
				piglin.set(near);
				WarDrummer.STRIKE_LISTENERS.add(listener);
				drummer.set((WarDrummer) spawned.stream().filter(e -> e instanceof WarDrummer).findFirst().orElseThrow(() -> new AssertionError("no drummer in " + spawned)));
			});
			ctx.runAtTickTime(comes + 5 + cycle, () -> {
				WarDrummer.STRIKE_LISTENERS.remove(listener);
				try {
					var d = drummer.get();
					ctx.assertTrue(d.isPassenger() && d.getVehicle() == hoglin.get(), "the drummer got off: it rides " + d.getVehicle());
					ctx.assertTrue(!walked.get(), "the drummer walked on the hoglin's back");
					ctx.assertTrue(strikes.get() > 0, "no beat heard in a drum cycle; in a fight: " + d.inFight());
					ctx.assertTrue(piglin.get().hasEffect(MobEffects.STRENGTH), "the rift piglin " + piglin.get().distanceTo(d) + " blocks off has no Strength");
					ctx.assertTrue(seat.get() != null && Math.abs(seat.get() - DRUMMER_SEAT) < 0.05,
							"the drummer sits " + seat.get() + " over the hoglin's feet, not " + DRUMMER_SEAT);
				} finally {
					riftCleanUp(ctx, player);
				}
				ctx.succeed();
			});
		});
	}

	/** Where a drummer's feet are over a hoglin's, riding it: where an adult piglin's would be. */
	private static final double DRUMMER_SEAT = 1.49375 - 0.7;

	/**
	 * A participant two blocks in front of a raid's rift who can't be hurt but is still someone to fight (a player who
	 * can't be hurt is no mob's target), held where they stand so no knockback takes them out of the raid.
	 */
	static ServerPlayer heldParticipant(GameTestHelper ctx) {
		var player = StageTests.player(ctx);
		DrummerTests.ready(player);
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4));
		var spot = ctx.absolutePos(new BlockPos(2, 1, 8));
		ctx.onEachTick(() -> {
			player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
			player.setDeltaMovement(Vec3.ZERO);
		});
		return player;
	}

	/**
	 * Hurts him from where he is to this share of his health, his armour aside, as {@code by}'s attack (or a plain
	 * one), from the chase: a roar it queues starts on his next tick.
	 */
	static void hurtTo(GameTestHelper ctx, PiglinGeneral general, @Nullable ServerPlayer by, double share) {
		general.getAttribute(Attributes.ARMOR).setBaseValue(0);
		general.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
		general.damageCooldownTime = 0;
		general.tactics().start(Move.CHASE);
		var sources = ctx.getLevel().damageSources();
		general.hurtServer(ctx.getLevel(), by != null ? sources.playerAttack(by) : sources.generic(), general.getHealth() - (float) (share * general.getMaxHealth()));
	}

	/** His save, as a chunk's would hold him. */
	static CompoundTag saved(GameTestHelper ctx, PiglinGeneral general) {
		var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, ctx.getLevel().registryAccess());
		general.saveWithoutId(output);
		return output.buildResult();
	}

	/** Runs a step; if it fails, cleans up first. */
	static void guarded(Runnable cleanUp, Runnable step) {
		try {
			step.run();
		} catch (Throwable t) {
			cleanUp.run();
			throw t;
		}
	}

	static List<Entity> added(List<? extends Entity> alive, Set<Entity> before) {
		return alive.stream().filter(e -> !before.contains(e)).map(e -> (Entity) e).toList();
	}

	/** The new mobs, mounts and riders alike, whose target isn't the player; "" when all are at them. */
	static String misTargeted(List<? extends Entity> alive, Set<Entity> before, ServerPlayer player) {
		return added(alive, before).stream()
				.filter(e -> e instanceof Mob mob && mob.getTarget() != player)
				.map(e -> e.getType().toShortString() + (e.getVehicle() != null ? " riding " + e.getVehicle().getType().toShortString() : "")
						+ " at " + ((Mob) e).getTarget())
				.collect(Collectors.joining(", "));
	}

	static long count(List<Entity> entities, Predicate<Entity> which) {
		return entities.stream().filter(which).count();
	}

	static String describe(List<Entity> entities) {
		return entities.stream().map(e -> e.getType().toShortString() + (e instanceof Mob m ? "(" + m.getMainHandItem().getItem() + ")" : ""))
				.collect(Collectors.joining(", "));
	}

	/** Barriers round the room, 4 high (a raid's mobs stay in it, away from the neighbouring tests), where nothing stands. */
	static void penRoom(GameTestHelper ctx) {
		for (var pos : roomEdge()) {
			if (ctx.getBlockState(pos).isAir()) {
				ctx.setBlock(pos, Blocks.BARRIER);
			}
		}
	}

	private static List<BlockPos> roomEdge() {
		var edge = new ArrayList<BlockPos>();
		for (int a = 0; a < ROOM; a++) {
			for (int y = 1; y <= 4; y++) {
				edge.add(new BlockPos(a, y, 0));
				edge.add(new BlockPos(a, y, ROOM - 1));
				edge.add(new BlockPos(0, y, a));
				edge.add(new BlockPos(ROOM - 1, y, a));
			}
		}
		return edge;
	}

	/** The rift room's size across. */
	private static final int ROOM = 24;

	/** A raid test's end: its player gone, its rift ended, its mobs and the room's barriers gone. */
	static void riftCleanUp(GameTestHelper ctx, ServerPlayer player) {
		StageTests.remove(ctx, player);
		FormingTests.cleanUp(ctx);
		ctx.killAllEntities();
		for (var pos : roomEdge()) {
			if (ctx.getBlockState(pos).is(Blocks.BARRIER)) {
				ctx.setBlock(pos, Blocks.AIR);
			}
		}
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
