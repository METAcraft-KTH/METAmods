import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.core.impl.interfaces.EntityAttachedPacket;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.nether_rifts.mob.GhastBombardier;
import nu.metacraft.nether_rifts.mob.RiftEntities;
import nu.metacraft.nether_rifts.mob.WarDrummer;
import nu.metacraft.nether_rifts.raid.RiftMobs;
import nu.metacraft.nether_rifts.raid.Slot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DrummerTests {

	static void registerTests() {
		NetherRiftsTests.register("drummer/drums_in_combat_marching", ctx -> {
			// Out of combat it doesn't drum; with a player near it does, and its legs march under the beat (BIL gives a
			// .bbmodel's animations every bone, so the beat must carry the legs itself).
			FormingTests.lightFrame(ctx, 2, 3);
			var drummer = new java.util.concurrent.atomic.AtomicReference<WarDrummer>();
			var legs = new java.util.ArrayList<org.joml.Vector3f>();
			ctx.runAtTickTime(3, () -> drummer.set((WarDrummer) RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.LEADER,
					ctx.absolutePos(new BlockPos(12, 1, 12)), Vec3.ZERO).getFirst()));
			ctx.runAtTickTime(30, () -> {
				ctx.assertFalse(drummer.get().getHolder().getAnimator().isPlaying("beat"), "it drums with nobody near");
				var near = ctx.absolutePos(new BlockPos(2, 1, 12));
				StageTests.player(ctx).teleportTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
			});
			for (int t = 32; t <= 130; t += 2) {
				ctx.runAtTickTime(t, () -> {
					var holder = drummer.get().getHolder();
					var animator = (de.tomalbrc.bil.core.component.AnimationComponent) holder.getAnimator();
					if (animator.isPlaying("beat")) {
						var pose = animator.findPose(null, holder.getBone("left_leg"));
						legs.add(pose.pose().readOnlyLeftRotation().getEulerAnglesXYZ(new org.joml.Vector3f()));
					}
				});
			}
			ctx.runAtTickTime(132, () -> {
				ctx.assertTrue(legs.size() >= 5, "it hardly drummed with a player near: " + legs.size() + " samples");
				float spread = 0;
				for (var a : legs) {
					for (var b : legs) {
						spread = Math.max(spread, new org.joml.Quaternionf().rotationXYZ(a.x, a.y, a.z)
								.difference(new org.joml.Quaternionf().rotationXYZ(b.x, b.y, b.z), new org.joml.Quaternionf()).angle());
					}
				}
				ctx.assertTrue(spread > Math.toRadians(20), "its legs don't march as it drums: they turn " + Math.toDegrees(spread) + " degrees");
				ctx.getLevel().players().stream().filter(p -> p.blockPosition().closerThan(ctx.absolutePos(new BlockPos(2, 1, 12)), 4)).toList()
						.forEach(p -> StageTests.remove(ctx, p));
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("drummer/beats_on_strikes", ctx -> {
			// Drumming, it is heard as each stick lands in its beat animation: frames 8 and 18 of each 1 s bar, so every
			// 10 ticks, 8 ticks after a beat on the drum clock (or 9: the animation may take its first frame a tick
			// later), only while the beat plays (not in the big beat), and loud enough to carry.
			FormingTests.lightFrame(ctx, 2, 3);
			var drummer = new java.util.concurrent.atomic.AtomicReference<WarDrummer>();
			var strikes = new java.util.concurrent.CopyOnWriteArrayList<long[]>();
			var offBeat = new java.util.concurrent.CopyOnWriteArrayList<String>();
			WarDrummer.StrikeListener listener = (level, x, y, z, volume) -> {
				var d = drummer.get();
				if (d == null || level != ctx.getLevel() || Math.hypot(x - d.getX(), z - d.getZ()) > 0.5) {
					return;
				}
				long now = level.getGameTime();
				if (!d.getHolder().getAnimator().isPlaying("beat")) {
					offBeat.add("a beat heard at " + now + " without the beat playing");
				}
				if (volume < 1) {
					offBeat.add("a beat at volume " + volume);
				}
				strikes.add(new long[] {now, WarDrummer.cycleAt(now)});
			};
			WarDrummer.STRIKE_LISTENERS.add(listener);
			// when it was in a fight, for the failure messages
			var fights = new StringBuilder();
			boolean[] was = {false};
			ctx.onEachTick(() -> {
				var d = drummer.get();
				if (d != null && d.inFight() != was[0]) {
					was[0] = d.inFight();
					fights.append(was[0] ? " fight from " : " to ").append(ctx.getLevel().getGameTime());
				}
			});
			ctx.runAtTickTime(3, () -> {
				drummer.set((WarDrummer) RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.LEADER,
						ctx.absolutePos(new BlockPos(12, 1, 12)), Vec3.ZERO).getFirst());
				var near = ctx.absolutePos(new BlockPos(2, 1, 12));
				StageTests.player(ctx).teleportTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
			});
			ctx.runAtTickTime(280, () -> {
				WarDrummer.STRIKE_LISTENERS.remove(listener);
				ctx.assertTrue(offBeat.isEmpty(), String.join("; ", offBeat));
				// a drum cycle is 90 ticks: three beats, then the big beat
				ctx.assertTrue(strikes.size() >= 3, "only " + strikes.size() + " beats heard in 280 ticks with a player near: " + describe(strikes) + ";" + fights);
				long offset = Math.floorMod(strikes.getFirst()[1], 10);
				ctx.assertTrue(offset == WarDrummer.RIGHT_STRIKE || offset == WarDrummer.RIGHT_STRIKE + 1,
						"the first beat is heard " + offset + " ticks after a beat on the drum clock, not as the stick lands (8): " + describe(strikes));
				int bigBeatStart = WarDrummer.bigBeatStart();
				for (int i = 0; i < strikes.size(); i++) {
					long[] strike = strikes.get(i);
					ctx.assertTrue(Math.floorMod(strike[1], 10) == offset, "beats off the strikes' step: " + describe(strikes));
					ctx.assertFalse(strike[1] >= bigBeatStart && strike[1] < bigBeatStart + WarDrummer.BIG_BEAT_TICKS,
							"a beat heard in the big beat: " + describe(strikes));
					if (i > 0) {
						ctx.assertTrue((strike[0] - strikes.get(i - 1)[0]) % 10 == 0, "beats not 10 ticks apart: " + describe(strikes));
					}
				}
				ctx.getLevel().players().stream().filter(p -> p.blockPosition().closerThan(ctx.absolutePos(new BlockPos(2, 1, 12)), 4)).toList()
						.forEach(p -> StageTests.remove(ctx, p));
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("drummer/leads_waves", ctx -> {
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var spawned = RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.LEADER, ctx.absolutePos(new BlockPos(12, 1, 12)), Vec3.ZERO);
				ctx.assertTrue(spawned.size() == 1 && spawned.getFirst() instanceof WarDrummer, "the leader slot spawned " + spawned);
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("drummer/buffs_only_rift_piglins", ctx -> {
			// Review focus 3.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				var drummer = (WarDrummer) RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.LEADER, ctx.absolutePos(new BlockPos(12, 1, 12)), Vec3.ZERO).getFirst();
				var near = (Piglin) RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.RANGED, ctx.absolutePos(new BlockPos(15, 1, 12)), Vec3.ZERO).getFirst();
				var far = (Piglin) RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.RANGED, ctx.absolutePos(new BlockPos(12, 1, 12)).offset(0, 0, 0), Vec3.ZERO).getFirst();
				far.teleportTo(drummer.getX() + 13.5, drummer.getY(), drummer.getZ());
				var vanilla = ctx.spawn(EntityTypes.PIGLIN, new BlockPos(12, 1, 15));
				drummer.bigBeat(ctx.getLevel());
				ctx.assertTrue(near.hasEffect(MobEffects.SPEED) && near.hasEffect(MobEffects.STRENGTH), "a rift piglin 3 blocks away wasn't buffed");
				ctx.assertFalse(far.hasEffect(MobEffects.SPEED), "a rift piglin 13.5 blocks away was buffed");
				ctx.assertFalse(vanilla.hasEffect(MobEffects.SPEED), "a vanilla piglin was buffed");
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("drummer/buff_range_from_config", ctx -> {
			// The drummer_short preset sets the War Beat's reach to 6 blocks.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				var drummer = (WarDrummer) RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.LEADER, ctx.absolutePos(new BlockPos(12, 1, 12)), Vec3.ZERO).getFirst();
				var near = (Piglin) RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.RANGED, ctx.absolutePos(new BlockPos(15, 1, 12)), Vec3.ZERO).getFirst();
				var far = (Piglin) RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.RANGED, ctx.absolutePos(new BlockPos(12, 1, 12)), Vec3.ZERO).getFirst();
				far.teleportTo(drummer.getX() + 8, drummer.getY(), drummer.getZ());
				drummer.bigBeat(ctx.getLevel());
				ctx.assertTrue(near.hasEffect(MobEffects.SPEED), "a rift piglin 3 blocks away wasn't buffed");
				ctx.assertFalse(far.hasEffect(MobEffects.SPEED), "a rift piglin 8 blocks away was buffed with a buff range of 6");
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("drummer/shock_beat_pushes_players", ctx -> {
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var drummer = (WarDrummer) RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.LEADER, ctx.absolutePos(new BlockPos(12, 1, 12)), Vec3.ZERO).getFirst();
				var close = StageTests.player(ctx);
				close.teleportTo(drummer.getX() + 2, drummer.getY(), drummer.getZ());
				var distant = StageTests.player(ctx);
				distant.teleportTo(drummer.getX() - 6, drummer.getY(), drummer.getZ());
				close.setDeltaMovement(Vec3.ZERO);
				// a fresh player can't be hurt until its client says it has loaded, nor in its spawn protection
				for (var player : java.util.List.of(close, distant)) {
					player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
					player.setInvulnerableTime(0);
				}
				distant.setDeltaMovement(Vec3.ZERO);
				// mob attacks do nothing to players on peaceful, which the test world may be on
				var server = ctx.getLevel().getServer();
				var difficulty = ctx.getLevel().getDifficulty();
				server.setDifficulty(Difficulty.NORMAL, true);
				drummer.bigBeat(ctx.getLevel());
				server.setDifficulty(difficulty, true);
				ctx.assertTrue(close.getDeltaMovement().x > 0.3, "a player 2 blocks east wasn't pushed east: " + close.getDeltaMovement());
				ctx.assertTrue(close.syncVelocity, "the push isn't sent to the player's own client");
				ctx.assertTrue(close.getHealth() < close.getMaxHealth(), "a player 2 blocks off wasn't hurt");
				ctx.assertTrue(distant.getHealth() == distant.getMaxHealth(), "a player 6 blocks away was hurt");
				ctx.assertTrue(distant.getDeltaMovement().horizontalDistance() < 0.01, "a player 6 blocks away was pushed: " + distant.getDeltaMovement());
				StageTests.remove(ctx, close);
				StageTests.remove(ctx, distant);
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("drummer/shock_beat_spares_staff", ctx -> {
			// Review focus 5.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var drummer = (WarDrummer) RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.LEADER, ctx.absolutePos(new BlockPos(12, 1, 12)), Vec3.ZERO).getFirst();
				var creative = StageTests.player(ctx);
				creative.setGameMode(GameType.CREATIVE);
				creative.teleportTo(drummer.getX() + 2, drummer.getY(), drummer.getZ());
				var spectator = StageTests.player(ctx);
				spectator.setGameMode(GameType.SPECTATOR);
				spectator.teleportTo(drummer.getX() - 2, drummer.getY(), drummer.getZ());
				creative.setDeltaMovement(Vec3.ZERO);
				spectator.setDeltaMovement(Vec3.ZERO);
				drummer.bigBeat(ctx.getLevel());
				ctx.assertTrue(creative.getDeltaMovement().horizontalDistance() < 0.01, "a creative player was pushed");
				ctx.assertTrue(spectator.getDeltaMovement().horizontalDistance() < 0.01, "a spectator was pushed");
				StageTests.remove(ctx, creative);
				StageTests.remove(ctx, spectator);
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("drummer/drums_on_its_own", ctx -> {
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.LEADER, ctx.absolutePos(new BlockPos(12, 1, 12)), Vec3.ZERO);
				RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.RANGED, ctx.absolutePos(new BlockPos(14, 1, 12)), Vec3.ZERO);
				// it drums only in combat: a player who can fight, near
				var near = ctx.absolutePos(new BlockPos(2, 1, 12));
				StageTests.player(ctx).teleportTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
			});
			// the tempo is the world clock's: a 90-tick cycle, the slam 66 ticks into it, so one lands within ~130 ticks
			ctx.runAtTickTime(140, () -> {
				var piglin = ctx.getEntities(EntityTypes.PIGLIN).stream().filter(RiftMobs::isRiftMob).findFirst().orElseThrow();
				ctx.assertTrue(piglin.hasEffect(MobEffects.SPEED), "no big beat within 140 ticks");
				ctx.getLevel().players().stream().filter(p -> p.blockPosition().closerThan(ctx.absolutePos(new BlockPos(2, 1, 12)), 4)).toList()
						.forEach(p -> StageTests.remove(ctx, p));
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("drummer/natural_spawn_brings_one", ctx -> {
			// The drummer_natural preset: every natural piglin spawn in the nether brings a drummer.
			var nether = ctx.getLevel().getServer().getLevel(Level.NETHER);
			var centre = netherBox(nether, 2008, 8);
			whenEntitiesLoad(ctx, nether, centre, () -> {
				var piglins = new ArrayList<Piglin>();
				for (var at : PIGLIN_SPOTS) {
					piglins.add(naturalPiglin(nether, centre.offset(at)));
				}
				ctx.assertTrue(nether.getEntitiesOfClass(Piglin.class, new AABB(centre).inflate(8)).size() == piglins.size(), "the piglins can't be seen");
				var drummers = nether.getEntitiesOfClass(WarDrummer.class, new AABB(centre).inflate(8));
				long adults = piglins.stream().filter(Piglin::isAdult).count();
				ctx.assertTrue(adults > 0, "every piglin spawned a baby");
				ctx.assertTrue(drummers.size() == adults, drummers.size() + " drummers came with " + adults + " grown piglins");
				for (var drummer : drummers) {
					ctx.assertTrue(piglins.stream().anyMatch(p -> p.distanceTo(drummer) <= 3.5), "a drummer spawned away from every piglin");
					ctx.assertFalse(RiftMobs.isRiftMob(drummer), "a nether drummer is a rift's");
				}
				clearNetherBox(nether, centre);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("drummer/natural_spawn_none_at_zero", ctx -> {
			// The drummer_never preset: natural_chance 0.
			var nether = ctx.getLevel().getServer().getLevel(Level.NETHER);
			var centre = netherBox(nether, 2040, 8);
			whenEntitiesLoad(ctx, nether, centre, () -> {
				for (var at : PIGLIN_SPOTS) {
					naturalPiglin(nether, centre.offset(at));
				}
				ctx.assertTrue(nether.getEntitiesOfClass(Piglin.class, new AABB(centre).inflate(8)).size() == PIGLIN_SPOTS.size(), "the piglins can't be seen");
				var drummers = nether.getEntitiesOfClass(WarDrummer.class, new AABB(centre).inflate(8));
				ctx.assertTrue(drummers.isEmpty(), drummers.size() + " drummers came at a chance of 0");
				clearNetherBox(nether, centre);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("drummer/rift_piglins_bring_none", ctx -> {
			// The drummer_natural preset. Rift piglins come through (loaded from the roster, an EVENT): never a drummer.
			// Nor does a nether piglin spawned other than naturally, one marked a rift's, or the Bombardier.
			FormingTests.lightFrame(ctx, 2, 3);
			var nether = ctx.getLevel().getServer().getLevel(Level.NETHER);
			var centre = netherBox(nether, 2072, 8);
			whenEntitiesLoad(ctx, nether, centre, () -> {
				var rift = FormingTests.riftIn(ctx);
				for (int i = 0; i < 3; i++) {
					RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.RANGED, ctx.absolutePos(new BlockPos(10 + 2 * i, 1, 12)), Vec3.ZERO);
				}
				ctx.assertTrue(ctx.getEntities(RiftEntities.WAR_DRUMMER).isEmpty(), "rift piglins brought a drummer");

				var event = EntityTypes.PIGLIN.create(nether, EntitySpawnReason.EVENT);
				spawnAt(nether, event, centre.offset(-4, 0, -4), EntitySpawnReason.EVENT);
				var marked = EntityTypes.PIGLIN.create(nether, EntitySpawnReason.NATURAL);
				marked.setAttached(RiftMobs.RIFT, rift.id());
				spawnAt(nether, marked, centre.offset(4, 0, -4), EntitySpawnReason.NATURAL);
				var bombardier = RiftEntities.GHAST_BOMBARDIER.create(nether, EntitySpawnReason.NATURAL);
				spawnAt(nether, bombardier, centre.offset(0, 0, 4), EntitySpawnReason.NATURAL);
				var drummers = nether.getEntitiesOfClass(WarDrummer.class, new AABB(centre).inflate(8));
				ctx.assertTrue(drummers.isEmpty(), drummers.size() + " drummers came with piglins that aren't natural nether ones");
				// and the box works: a plain grown piglin does bring one
				Piglin plain;
				do {
					plain = naturalPiglin(nether, centre);
				} while (!plain.isAdult());
				ctx.assertTrue(nether.getEntitiesOfClass(WarDrummer.class, new AABB(centre).inflate(8)).size() == 1, "a natural piglin brought no drummer");
				clearNetherBox(nether, centre);
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("drummer/drums_for_fighting_piglins", ctx -> {
			// A nether drummer, no player near: it drums once a piglin of its own has a target (here a hoglin), and its
			// War Beat buffs the nether's piglins, not a rift's.
			FormingTests.lightFrame(ctx, 2, 3);
			var drummer = new java.util.concurrent.atomic.AtomicReference<WarDrummer>();
			var piglin = new java.util.concurrent.atomic.AtomicReference<Piglin>();
			var riftPiglin = new java.util.concurrent.atomic.AtomicReference<Piglin>();
			var hoglin = new java.util.concurrent.atomic.AtomicReference<net.minecraft.world.entity.monster.hoglin.Hoglin>();
			var fought = new java.util.concurrent.atomic.AtomicBoolean();
			ctx.runAtTickTime(3, () -> {
				drummer.set(naturalDrummer(ctx, new BlockPos(12, 1, 12)));
				penIn(ctx, new BlockPos(12, 1, 12));
				// still, so their places and memories hold
				piglin.set(ctx.spawn(EntityTypes.PIGLIN, new BlockPos(16, 1, 12)));
				piglin.get().setNoAi(true);
				var r = (Piglin) RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.RANGED, ctx.absolutePos(new BlockPos(12, 1, 16)), Vec3.ZERO).getFirst();
				r.setNoAi(true);
				riftPiglin.set(r);
				hoglin.set(ctx.spawn(EntityTypes.HOGLIN, new BlockPos(20, 1, 12)));
				hoglin.get().setNoAi(true);
			});
			ctx.runAtTickTime(30, () -> {
				ctx.assertFalse(drummer.get().inFight(), "it fights with nothing to fight");
				piglin.get().getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, hoglin.get());
			});
			for (int t = 32; t <= 60; t += 2) {
				ctx.runAtTickTime(t, () -> fought.compareAndSet(false, drummer.get().inFight()));
			}
			// the 90-tick cycle's slam comes within ~130 ticks of the fight starting
			ctx.runAtTickTime(180, () -> {
				ctx.assertTrue(fought.get(), "it didn't fight while its piglin hunted");
				ctx.assertTrue(piglin.get().hasEffect(MobEffects.SPEED) && piglin.get().hasEffect(MobEffects.STRENGTH), "its piglin wasn't buffed");
				ctx.assertFalse(riftPiglin.get().hasEffect(MobEffects.SPEED), "a nether drummer buffed a rift piglin");
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("drummer/never_barters", ctx -> {
			// No drummer, rift or nether, takes gold handed to it or picks it up.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var rift = (WarDrummer) RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.LEADER, ctx.absolutePos(new BlockPos(12, 1, 12)), Vec3.ZERO).getFirst();
				var natural = naturalDrummer(ctx, new BlockPos(4, 1, 4));
				var player = StageTests.player(ctx);
				for (var drummer : List.of(rift, natural)) {
					player.teleportTo(drummer.getX() + 1, drummer.getY(), drummer.getZ());
					player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_INGOT, 4));
					drummer.interact(player, InteractionHand.MAIN_HAND, drummer.position());
					ctx.assertTrue(player.getMainHandItem().is(Items.GOLD_INGOT) && player.getMainHandItem().getCount() == 4,
							"a drummer took gold handed to it: " + player.getMainHandItem());
					ctx.assertFalse(drummer.canPickUpLoot(), "a drummer can pick up loot");
				}
				StageTests.remove(ctx, player);
				ctx.spawnItem(Items.GOLD_INGOT, new BlockPos(12, 1, 12));
				ctx.spawnItem(Items.GOLD_INGOT, new BlockPos(4, 1, 4));
				ctx.runAfterDelay(60, () -> {
					var gold = ctx.getEntities(EntityTypes.ITEM).stream().filter(item -> item.getItem().is(Items.GOLD_INGOT)).count();
					ctx.assertTrue(gold == 2, "a drummer picked up gold: " + gold + " of 2 left");
					for (var drummer : List.of(rift, natural)) {
						for (var slot : EquipmentSlot.values()) {
							ctx.assertTrue(drummer.getItemBySlot(slot).isEmpty(), "a drummer holds " + drummer.getItemBySlot(slot) + " in " + slot);
						}
					}
					ctx.killAllEntities();
					FormingTests.cleanUp(ctx);
					ctx.succeed();
				});
			});
		});
		NetherRiftsTests.register("drummer/natural_zombifies", ctx -> {
			// A nether drummer out of the nether turns zombie on a piglin's clock (300 ticks; set near its end).
			ctx.runAtTickTime(3, () -> {
				var drummer = naturalDrummer(ctx, new BlockPos(12, 1, 12));
				ctx.assertTrue(drummer.isConverting(), "a nether drummer in the overworld isn't turning");
				drummer.setTimeInOverworld(AbstractPiglin.CONVERSION_TIME - 10);
			});
			ctx.runAtTickTime(40, () -> {
				ctx.assertTrue(ctx.getEntities(RiftEntities.WAR_DRUMMER).isEmpty(), "the nether drummer is still a drummer");
				ctx.assertTrue(ctx.getEntities(EntityTypes.ZOMBIFIED_PIGLIN).size() == 1, "the nether drummer didn't become a zombified piglin");
				ctx.killAllEntities();
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("drummer/rift_never_zombifies", ctx -> {
			// A rift's drummer never turns, even with its immunity flag off (a save from before the drummer was a piglin).
			FormingTests.lightFrame(ctx, 2, 3);
			var drummer = new java.util.concurrent.atomic.AtomicReference<WarDrummer>();
			ctx.runAtTickTime(3, () -> {
				drummer.set((WarDrummer) RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.LEADER, ctx.absolutePos(new BlockPos(12, 1, 12)), Vec3.ZERO).getFirst());
				drummer.get().setImmuneToZombification(false);
				drummer.get().setTimeInOverworld(AbstractPiglin.CONVERSION_TIME - 10);
			});
			ctx.runAtTickTime(40, () -> {
				ctx.assertFalse(drummer.get().isRemoved(), "the rift's drummer is gone");
				ctx.assertFalse(drummer.get().isConverting(), "the rift's drummer is turning");
				ctx.assertTrue(ctx.getEntities(EntityTypes.ZOMBIFIED_PIGLIN).isEmpty(), "the rift's drummer became a zombified piglin");
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("drummer/gold_truce", ctx -> {
			// A rift's drummer ignores gold (it fights and shocks a player in gold); a nether one keeps the truce as
			// piglins do, though it still shocks a player without gold.
			FormingTests.lightFrame(ctx, 2, 3);
			var riftDrummer = new java.util.concurrent.atomic.AtomicReference<WarDrummer>();
			var natural = new java.util.concurrent.atomic.AtomicReference<WarDrummer>();
			var golds = new ArrayList<ServerPlayer>();
			ctx.runAtTickTime(3, () -> {
				var level = ctx.getLevel();
				riftDrummer.set((WarDrummer) RiftMobs.spawn(level, FormingTests.riftIn(ctx).id(), Slot.LEADER, ctx.absolutePos(new BlockPos(5, 1, 12)), Vec3.ZERO).getFirst());
				natural.set(naturalDrummer(ctx, new BlockPos(19, 1, 12)));
				var nearRift = goldPlayer(ctx, riftDrummer.get().position().add(2, 0, 0));
				var nearNatural = goldPlayer(ctx, natural.get().position().add(-2, 0, 0));
				var plain = StageTests.player(ctx);
				plain.teleportTo(natural.get().getX() + 2, natural.get().getY(), natural.get().getZ());
				ready(plain);
				golds.add(nearRift);
				golds.add(nearNatural);
				ctx.assertTrue(riftDrummer.get().isFoe(nearRift), "a rift's drummer spares a player in gold");
				ctx.assertFalse(natural.get().isFoe(nearNatural), "a nether drummer breaks the gold truce");
				var difficulty = level.getDifficulty();
				level.getServer().setDifficulty(Difficulty.NORMAL, true);
				riftDrummer.get().bigBeat(level);
				natural.get().bigBeat(level);
				level.getServer().setDifficulty(difficulty, true);
				ctx.assertTrue(nearRift.getHealth() < nearRift.getMaxHealth(), "a rift's Shock Beat spared a player in gold");
				ctx.assertTrue(nearNatural.getHealth() == nearNatural.getMaxHealth(), "a nether Shock Beat hurt a player in gold");
				ctx.assertTrue(nearNatural.getDeltaMovement().horizontalDistance() < 0.01, "a nether Shock Beat pushed a player in gold");
				ctx.assertTrue(plain.getHealth() < plain.getMaxHealth(), "a nether Shock Beat spared a player without gold");
				StageTests.remove(ctx, plain);
			});
			// in sight, near both: the rift's drummer fights; the nether one doesn't
			ctx.runAtTickTime(30, () -> {
				ctx.assertTrue(riftDrummer.get().inFight(), "a rift's drummer doesn't fight a player in gold");
				ctx.assertFalse(natural.get().inFight(), "a nether drummer fights a player in gold");
				golds.forEach(p -> StageTests.remove(ctx, p));
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("drummer/piglin_kin", ctx -> {
			// Piglins count the drummer among their grown piglins and share anger with it both ways.
			var drummer = new java.util.concurrent.atomic.AtomicReference<WarDrummer>();
			var piglin = new java.util.concurrent.atomic.AtomicReference<Piglin>();
			var hitter = new java.util.concurrent.atomic.AtomicReference<ServerPlayer>();
			var other = new java.util.concurrent.atomic.AtomicReference<ServerPlayer>();
			ctx.runAtTickTime(3, () -> {
				drummer.set(naturalDrummer(ctx, new BlockPos(12, 1, 12)));
				// it keeps away from players: penned, its distances to them hold
				penIn(ctx, new BlockPos(12, 1, 12));
				piglin.set(ctx.spawn(EntityTypes.PIGLIN, new BlockPos(15, 1, 12)));
				piglin.get().setImmuneToZombification(true);
				// in gold, so nobody fights them till they hit someone
				hitter.set(goldPlayer(ctx, drummer.get().position().add(0, 0, 8)));
				other.set(goldPlayer(ctx, drummer.get().position().add(-2, 0, 0)));
			});
			// sensors run once a second
			ctx.runAtTickTime(50, () -> {
				var level = ctx.getLevel();
				ctx.assertTrue(piglin.get().getBrain().getMemory(MemoryModuleType.NEARBY_ADULT_PIGLINS).orElse(List.of()).contains(drummer.get()),
						"a piglin doesn't count the drummer among its piglins");
				ctx.assertTrue(drummer.get().getBrain().getMemory(MemoryModuleType.NEARBY_ADULT_PIGLINS).orElse(List.of()).contains(piglin.get()),
						"the drummer doesn't know its piglins");
				ctx.assertFalse(drummer.get().isFoe(hitter.get()), "the drummer fights a player in gold before being hit");
				var difficulty = level.getDifficulty();
				level.getServer().setDifficulty(Difficulty.NORMAL, true);
				drummer.get().hurtServer(level, level.damageSources().playerAttack(hitter.get()), 1f);
				ctx.assertTrue(angryAt(drummer.get(), hitter.get()), "the hit drummer isn't angry at its attacker");
				ctx.assertTrue(angryAt(piglin.get(), hitter.get()), "a piglin isn't angry at the drummer's attacker");
				ctx.assertTrue(drummer.get().isFoe(hitter.get()), "the drummer keeps the truce with someone who hit it");
				// a piglin hit tells the drummer too (it takes the nearer of two)
				piglin.get().hurtServer(level, level.damageSources().playerAttack(other.get()), 1f);
				level.getServer().setDifficulty(difficulty, true);
				ctx.assertTrue(angryAt(drummer.get(), other.get()), "the drummer isn't angry at its piglin's attacker");
				StageTests.remove(ctx, hitter.get());
				StageTests.remove(ctx, other.get());
				ctx.killAllEntities();
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("drummer/vanilla_clients_see_no_piglin_data", ctx -> {
			// The drummer is a piglin on the server (its zombification flag is synced data) but a BIL model to
			// clients: Polymer sends them a block display, and only data a block display has. Encoded as Polymer does
			// for a player, the piglin's flag never reaches them.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var drummer = (WarDrummer) RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.LEADER, ctx.absolutePos(new BlockPos(12, 1, 12)), Vec3.ZERO).getFirst();
				var player = StageTests.player(ctx);
				var clientType = drummer.getPolymerEntityType(PacketContext.get());
				ctx.assertTrue(clientType == EntityTypes.BLOCK_DISPLAY, "clients see a " + clientType);
				int immune = immuneDataId();
				var values = drummer.getEntityData().getNonDefaultValues();
				ctx.assertTrue(values != null && values.stream().anyMatch(v -> v.id() == immune), "a rift drummer's zombification flag isn't set");
				var packet = EntityAttachedPacket.set(new ClientboundSetEntityDataPacket(drummer.getId(), values), drummer);
				var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), ctx.getLevel().registryAccess());
				PacketContext.runWithContext(player, () -> ClientboundSetEntityDataPacket.STREAM_CODEC.encode(buf, packet));
				var sent = ClientboundSetEntityDataPacket.STREAM_CODEC.decode(buf).packedItems();
				var legal = PolymerEntityUtils.getDefaultSynchedEntityData(EntityTypes.BLOCK_DISPLAY);
				for (var value : sent) {
					var item = value.id() < legal.length ? legal[value.id()] : null;
					ctx.assertTrue(item != null && item.getAccessor().serializer() == value.serializer(),
							"clients get data " + value.id() + " (" + value.value() + ") that a block display hasn't");
				}
				ctx.assertFalse(sent.stream().anyMatch(v -> v.id() == immune && v.serializer() == EntityDataSerializers.BOOLEAN),
						"the piglin's zombification flag reaches clients");
				ctx.assertTrue(sent.stream().anyMatch(v -> v.id() == eu.pb4.polymer.virtualentity.api.data.DisplayEntityData.WIDTH.id()),
						"the encoding skipped Polymer: no display width");
				StageTests.remove(ctx, player);
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("mobs/missing_slot_uses_default", ctx -> {
			// Review focus 1: the roster_partial preset has no HEAVY entry.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var spawned = RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.HEAVY, ctx.absolutePos(new BlockPos(12, 6, 12)), Vec3.ZERO);
				ctx.assertTrue(spawned.stream().anyMatch(e -> e.getType() == EntityTypes.GHAST), "a missing HEAVY entry spawned " + spawned);
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
	}

	/** Where the nether spawn tests put piglins, from the box's centre. */
	/** Beats heard, as (game time @ drum cycle tick). */
	static String describe(List<long[]> strikes) {
		var out = new StringBuilder();
		for (var strike : strikes) {
			out.append(strike[0]).append('@').append(strike[1]).append(' ');
		}
		return out.toString().trim();
	}

	private static final List<BlockPos> PIGLIN_SPOTS = List.of(
			new BlockPos(-4, 0, -4), new BlockPos(0, 0, -4), new BlockPos(4, 0, -4),
			new BlockPos(-4, 0, 4), new BlockPos(0, 0, 4), new BlockPos(4, 0, 4)
	);

	/**
	 * A closed obsidian box in the nether, 13 blocks across and 3 high inside, floored at y 99 round (x, z) (a chunk's
	 * middle), its chunk kept loaded; returns the centre of its floor's top. Set without block updates, so no lava
	 * flows in.
	 */
	static BlockPos netherBox(ServerLevel nether, int x, int z) {
		var centre = new BlockPos(x, 100, z);
		// generated here and now, with the ring round it that an entity-ticking chunk needs: left to the worldgen
		// threads, that can take longer than a test's ticks on a test server ticking flat out
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				nether.getChunk((x >> 4) + dx, (z >> 4) + dz);
			}
		}
		nether.setChunkForced(x >> 4, z >> 4, true);
		for (int dx = -7; dx <= 7; dx++) {
			for (int dz = -7; dz <= 7; dz++) {
				for (int dy = -1; dy <= 3; dy++) {
					boolean shell = Math.abs(dx) == 7 || Math.abs(dz) == 7 || dy == -1 || dy == 3;
					nether.setBlock(centre.offset(dx, dy, dz), shell ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState(),
							Block.UPDATE_CLIENTS);
				}
			}
		}
		return centre;
	}

	/**
	 * Runs {@code test} once the box's chunk has its entities loaded (a forced chunk's load from storage takes a few
	 * ticks, and till then entities added to it can't be found).
	 */
	static void whenEntitiesLoad(GameTestHelper ctx, ServerLevel nether, BlockPos centre, Runnable test) {
		var done = new java.util.concurrent.atomic.AtomicBoolean();
		var ticks = new java.util.concurrent.atomic.AtomicInteger();
		ctx.onEachTick(() -> {
			// and not before tick 3, when a lit frame has become a rift
			if (ticks.incrementAndGet() >= 3 && !done.get() && nether.isPositionEntityTicking(centre)) {
				done.set(true);
				test.run();
			}
		});
	}

	static void clearNetherBox(ServerLevel nether, BlockPos centre) {
		for (var entity : nether.getEntities((net.minecraft.world.entity.Entity) null, new AABB(centre).inflate(8), e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
			entity.discard();
		}
		nether.setChunkForced(centre.getX() >> 4, centre.getZ() >> 4, false);
	}

	/** A piglin spawned as the natural spawner does: placed, finalised as NATURAL, added. */
	static Piglin naturalPiglin(ServerLevel nether, BlockPos pos) {
		var piglin = EntityTypes.PIGLIN.create(nether, EntitySpawnReason.NATURAL);
		spawnAt(nether, piglin, pos, EntitySpawnReason.NATURAL);
		return piglin;
	}

	static void spawnAt(ServerLevel level, net.minecraft.world.entity.Mob mob, BlockPos pos, EntitySpawnReason reason) {
		mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), reason, null);
		level.addFreshEntityWithPassengers(mob);
	}

	/** A drummer of no rift, as one that came with nether piglins; kept, as a test's room may be far from any player. */
	static WarDrummer naturalDrummer(GameTestHelper ctx, BlockPos pos) {
		var drummer = ctx.spawn(RiftEntities.WAR_DRUMMER, pos);
		drummer.setPersistenceRequired();
		return drummer;
	}

	/** Glass round {@code pos}, two high, so a mob there stays put (the room is cleared between tests). */
	static void penIn(GameTestHelper ctx, BlockPos pos) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx != 0 || dz != 0) {
					ctx.setBlock(pos.offset(dx, 0, dz), Blocks.GLASS);
					ctx.setBlock(pos.offset(dx, 1, dz), Blocks.GLASS);
				}
			}
		}
	}

	/** A player in a golden helmet at {@code at}, who can be hurt. */
	static ServerPlayer goldPlayer(GameTestHelper ctx, Vec3 at) {
		var player = StageTests.player(ctx);
		player.teleportTo(at.x, at.y, at.z);
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
		ready(player);
		return player;
	}

	/** A fresh player can't be hurt until its client says it has loaded, nor in its spawn protection. */
	static void ready(ServerPlayer player) {
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		player.setInvulnerableTime(0);
		player.setDeltaMovement(Vec3.ZERO);
	}

	static boolean angryAt(AbstractPiglin piglin, ServerPlayer player) {
		return piglin.getBrain().isMemoryValue(MemoryModuleType.ANGRY_AT, player.getUUID());
	}

	/** The id of AbstractPiglin's synced zombification flag (protected). */
	static int immuneDataId() {
		try {
			var field = AbstractPiglin.class.getDeclaredField("DATA_IMMUNE_TO_ZOMBIFICATION");
			field.setAccessible(true);
			return ((EntityDataAccessor<?>) field.get(null)).id();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
