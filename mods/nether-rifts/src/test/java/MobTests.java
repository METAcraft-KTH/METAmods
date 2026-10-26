
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import se.metacraft.nether_rifts.NetherRifts;
import se.metacraft.nether_rifts.mob.PiglinGeneral;
import se.metacraft.nether_rifts.mob.RiftEntities;
import se.metacraft.nether_rifts.mob.RiftItems;
import se.metacraft.nether_rifts.mob.WarDrummer;
import se.metacraft.nether_rifts.raid.RiftMobs;
import se.metacraft.nether_rifts.raid.Slot;
import se.metacraft.nether_rifts.rift.RiftBlocks;
import se.metacraft.core.item.METAcraftCreativeTab;
import se.metacraft.core.item.SpawnEggsCreativeTab;

public class MobTests {

	/** What a roster that still gives its ghasts and support a crossbow rider, or its support a strider, would say; the defaults no longer do. */
	static final String RIDDEN_GHAST = "{id:\"minecraft:ghast\",IgnoreYCheck:1b,Passengers:[{id:\"minecraft:piglin\",equipment:{mainhand:{id:\"minecraft:crossbow\",count:1}}}]}";
	static final String RIDDEN_STRIDER = "{id:\"minecraft:strider\",attributes:[{id:\"minecraft:movement_speed\",base:0.25d}],"
			+ "Passengers:[{id:\"minecraft:piglin\",equipment:{mainhand:{id:\"minecraft:crossbow\",count:1}}}]}";

	/** A mob of this entity NBT, enlisted in the rift in the slot's role as if its roster entry had said so: for what only a configured roster brings. */
	static List<net.minecraft.world.entity.Entity> spawnConfigured(net.minecraft.gametest.framework.GameTestHelper ctx, UUID rift, Slot slot, String snbt, BlockPos pos) {
		var spawned = new ArrayList<net.minecraft.world.entity.Entity>();
		try {
			se.metacraft.portalopening.raid.MobEntry.EntityEntry.fromData(net.minecraft.nbt.TagParser.parseCompoundFully(snbt))
					.spawnMobFromNBT(ctx.getLevel(), pos, entity -> {
						RiftMobs.enlist(entity, rift, slot);
						spawned.add(entity);
					});
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new AssertionError("bad test entity " + snbt, e);
		}
		return spawned;
	}

	static void registerTests() {
		NetherRiftsTests.register("mobs/rules_applied", ctx -> {
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				var at = ctx.absolutePos(new BlockPos(12, 1, 12));
				var piglin = (Piglin) RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.RANGED, at, Vec3.ZERO).getFirst();
				ctx.assertTrue(RiftMobs.riftOf(piglin).orElseThrow().equals(rift.id()), "not tagged with its rift");
				ctx.assertTrue(piglin.isPersistenceRequired(), "not persistent");
				ctx.assertFalse(piglin.canPickUpLoot(), "can pick up loot");
				ctx.assertTrue(piglin.getDropChances().byEquipment(EquipmentSlot.MAINHAND) == 0f, "can drop its crossbow");
			});
			// zombification happens after 300 ticks in the overworld
			ctx.runAtTickTime(330, () -> {
				ctx.assertTrue(ctx.getEntities(EntityTypes.ZOMBIFIED_PIGLIN).isEmpty(), "the rift piglin zombified");
				ctx.assertTrue(ctx.getEntities(EntityTypes.PIGLIN).size() == 1, "the rift piglin is gone");
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("mobs/rift_strider_stays_warm", ctx -> {
			// Off lava in the overworld a strider goes cold and slows; a rift's doesn't, walks faster than one, and its
			// crossbow piglin steers it. The defaults bring no striders now: this one is a configured roster's.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var spawned = spawnConfigured(ctx, FormingTests.riftIn(ctx).id(), Slot.SUPPORT, RIDDEN_STRIDER, ctx.absolutePos(new BlockPos(10, 1, 12)));
				var strider = (net.minecraft.world.entity.monster.Strider) spawned.stream()
						.filter(e -> e instanceof net.minecraft.world.entity.monster.Strider).findFirst()
						.orElseThrow(() -> new AssertionError("no strider in " + spawned));
				var plain = ctx.spawn(EntityTypes.STRIDER, new BlockPos(14, 1, 12));
				ctx.runAfterDelay(5, () -> {
					ctx.assertTrue(plain.isSuffocating(), "a plain strider off lava isn't cold: the test can't tell");
					ctx.assertFalse(strider.isSuffocating(), "the rift's strider went cold off lava");
					ctx.assertTrue(strider.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) == 0.25,
							"the rift's strider walks at " + strider.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
					ctx.assertTrue(strider.getControllingPassenger() instanceof Piglin, "its rider doesn't steer it: " + strider.getControllingPassenger());
					ctx.killAllEntities();
					FormingTests.cleanUp(ctx);
					ctx.succeed();
				});
			});
		});
		NetherRiftsTests.register("mobs/support_rides_hoglins", ctx -> {
			// The default support slot is a hoglin with a rider: a spear piglin, who steers it, or a Piglin Drummer, who only
			// drums while the hoglin goes its own way. No striders.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				var mounts = new ArrayList<Hoglin>();
				for (int i = 0; i < 24; i++) {
					var spawned = RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.SUPPORT, ctx.absolutePos(new BlockPos(6 + i % 12, 1, 8 + i / 12 * 8)), Vec3.ZERO);
					ctx.assertTrue(spawned.size() == 2, "the support slot brought " + spawned);
					ctx.assertFalse(spawned.stream().anyMatch(e -> e instanceof net.minecraft.world.entity.monster.Strider), "a strider came: " + spawned);
					var hoglin = spawned.stream().filter(e -> e instanceof Hoglin).map(e -> (Hoglin) e).findFirst().orElseThrow(() -> new AssertionError("no hoglin in " + spawned));
					ctx.assertTrue(hoglin.getPassengers().size() == 1, "the hoglin has riders " + hoglin.getPassengers());
					var rider = hoglin.getPassengers().getFirst();
					ctx.assertTrue(RiftMobs.isRiftMob(hoglin) && RiftMobs.isRiftMob(rider), "the rift's rules aren't on " + spawned);
					if (rider instanceof WarDrummer) {
						ctx.assertTrue(hoglin.getControllingPassenger() == null, "the Drummer steers its hoglin");
					} else {
						ctx.assertTrue(rider instanceof Piglin p && !p.isBaby() && p.getMainHandItem().is(Items.GOLDEN_SPEAR), "the rider is " + rider);
						ctx.assertTrue(hoglin.getControllingPassenger() == rider, "the spear piglin doesn't steer its hoglin: " + hoglin.getControllingPassenger());
					}
					mounts.add(hoglin);
				}
				ctx.assertTrue(mounts.stream().anyMatch(h -> h.getFirstPassenger() instanceof WarDrummer), "24 picks and no Drummer");
				ctx.assertTrue(mounts.stream().anyMatch(h -> h.getFirstPassenger() instanceof Piglin), "24 picks and no spear piglin");
				// and still ridden a while on
				ctx.runAfterDelay(20, () -> {
					for (var hoglin : mounts) {
						ctx.assertTrue(hoglin.isAlive() && hoglin.getPassengers().size() == 1 && hoglin.getFirstPassenger().getVehicle() == hoglin,
								"the rider got off: " + hoglin.getPassengers());
						// both kinds sit as low as an adult piglin on a hoglin: the hoglin's passenger point less the seat
						var rider = hoglin.getFirstPassenger();
						ctx.assertTrue(rider.getVehicleAttachmentPoint(hoglin).distanceTo(WarDrummer.HOGLIN_SEAT) < 1e-3,
								"the " + rider.getType().toShortString() + "'s seat is " + rider.getVehicleAttachmentPoint(hoglin));
						double sitsAt = rider.getY() - hoglin.getY();
						ctx.assertTrue(Math.abs(sitsAt - 0.79375) < 0.05, "the " + rider.getType().toShortString() + " sits " + sitsAt + " above its hoglin");
					}
					ctx.killAllEntities();
					FormingTests.cleanUp(ctx);
					ctx.succeed();
				});
			});
		});
		NetherRiftsTests.register("mobs/spear_piglin_rides_at_player", ctx -> {
			// A spear piglin on a hoglin keeps its seat and its target, and the pair closes on a player.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var player = StageTests.player(ctx);
				var spot = ctx.absolutePos(new BlockPos(18, 1, 12));
				player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
				var from = ctx.absolutePos(new BlockPos(8, 1, 12));
				var spawned = spawnConfigured(ctx, FormingTests.riftIn(ctx).id(), Slot.SUPPORT,
						"{id:\"minecraft:hoglin\",Passengers:[{id:\"minecraft:piglin\",equipment:{mainhand:{id:\"minecraft:golden_spear\",count:1}}}]}", from);
				var hoglin = (Hoglin) spawned.stream().filter(e -> e instanceof Hoglin).findFirst().orElseThrow();
				var rider = (Piglin) hoglin.getFirstPassenger();
				double before = hoglin.distanceTo(player);
				ctx.runAfterDelay(60, () -> {
					ctx.assertTrue(hoglin.isAlive() && rider.isAlive(), "one of the pair died");
					ctx.assertTrue(rider.getVehicle() == hoglin, "the spear piglin got off its hoglin");
					ctx.assertTrue(hoglin.distanceTo(player) < before - 2, "the pair went from " + before + " to " + hoglin.distanceTo(player) + " blocks of the player");
					ctx.assertTrue(rider.getTarget() == player || rider.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isPresent(),
							"the spear piglin has no target: " + rider.getTarget());
					StageTests.remove(ctx, player);
					ctx.killAllEntities();
					FormingTests.cleanUp(ctx);
					ctx.succeed();
				});
			});
		});
		NetherRiftsTests.register("mobs/heavy_ghast_has_no_rider", ctx -> {
			// The default heavy slot is a ghast on its own; a chained ghast replaces it in a large raid's late waves.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var spawned = RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.HEAVY, ctx.absolutePos(new BlockPos(12, 6, 12)), Vec3.ZERO);
				ctx.assertTrue(spawned.size() == 1 && spawned.getFirst() instanceof Ghast ghast && ghast.getPassengers().isEmpty(),
						"the heavy slot brought " + spawned);
				ctx.assertTrue(RiftMobs.isRiftMob(spawned.getFirst()), "the ghast isn't the rift's");
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("mobs/general_is_big_and_tough", ctx -> {
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var spawned = RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.CAPTAIN,
						ctx.absolutePos(new BlockPos(10, 1, 12)), Vec3.ZERO).getFirst();
				ctx.assertTrue(spawned instanceof PiglinGeneral, "the Captain's slot spawned " + spawned);
				var general = (PiglinGeneral) spawned;
				ctx.assertTrue(general.getMaxHealth() == 300 && general.getHealth() == 300, "the General has " + general.getHealth() + " / " + general.getMaxHealth());
				ctx.assertTrue(general.getArmorValue() == 16, "the General has armour " + general.getArmorValue());
				ctx.assertTrue(Math.abs(general.getBbHeight() - 3.3f) < 0.05f, "the General is " + general.getBbHeight() + " tall");
				ctx.assertTrue(general.isImmuneToZombification(), "the General can turn zombie");
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("mobs/general_never_turns_zombie", ctx -> {
			// A General of no rift (spawn egg, /summon) stays himself in the overworld, where a piglin turns zombie after 300 ticks.
			// penned in, so he can't walk after a neighbouring test's player out of the room
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
						for (int y = 1; y <= 4; y++) {
							ctx.setBlock(new BlockPos(12 + dx, y, 12 + dz), Blocks.BARRIER);
						}
					}
				}
			}
			var general = RiftEntities.PIGLIN_GENERAL.create(ctx.getLevel(), net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE);
			var at = ctx.absolutePos(new BlockPos(12, 1, 12));
			general.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
			general.finalizeSpawn(ctx.getLevel(), ctx.getLevel().getCurrentDifficultyAt(at), net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE, null);
			// no player of this test is near him: a monster with only far players (other tests', over 128 blocks off) despawns
			general.setPersistenceRequired();
			ctx.getLevel().addFreshEntity(general);
			ctx.assertFalse(RiftMobs.isRiftMob(general), "a summoned General belongs to a rift: the test proves nothing");
			ctx.assertTrue(general.isImmuneToZombification(), "a summoned General can turn zombie");
			ctx.runAtTickTime(330, () -> {
				ctx.assertTrue(ctx.getEntities(EntityTypes.ZOMBIFIED_PIGLIN).isEmpty(), "the summoned General turned zombie");
				ctx.assertTrue(general.isAlive() && !general.isRemoved(), "the summoned General is gone: " + general.getRemovalReason()
						+ ", health " + general.getHealth() + ", difficulty " + ctx.getLevel().getDifficulty() + ", at " + general.blockPosition().toShortString());
				ctx.killAllEntities();
				for (var pos : BlockPos.betweenClosed(new BlockPos(10, 1, 10), new BlockPos(14, 4, 14))) {
					ctx.setBlock(pos, Blocks.AIR);
				}
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("mobs/rift_ghast_rider_glides_down", ctx -> {
			// A rift ghast fires at players above and below it (IgnoreYCheck), and when it dies its piglin glides down.
			// The default ghast has no rider: this one is a configured roster's.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var spawned = spawnConfigured(ctx, FormingTests.riftIn(ctx).id(), Slot.HEAVY, RIDDEN_GHAST, ctx.absolutePos(new BlockPos(12, 6, 12)));
				var ghast = (Ghast) spawned.stream().filter(e -> e instanceof Ghast).findFirst().orElseThrow(() -> new AssertionError("no ghast in " + spawned));
				var rider = (Piglin) spawned.stream().filter(e -> e instanceof Piglin).findFirst().orElseThrow(() -> new AssertionError("no rider in " + spawned));
				var saved = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, ctx.getLevel().registryAccess());
				ghast.saveWithoutId(saved);
				ctx.assertTrue(saved.buildResult().getBooleanOr("IgnoreYCheck", false), "the rift's ghast keeps vanilla's 4-block y check");
				ghast.kill(ctx.getLevel());
				ctx.assertTrue(rider.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING), "the ghast's rider won't glide down");
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("mobs/no_boat_traps", ctx -> {
			// A rift mob won't stay in a boat (PreventEnterVehicle); a strider's rider (a configured roster's) still rides it.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var piglin = (Piglin) RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.RANGED, ctx.absolutePos(new BlockPos(10, 1, 12)), Vec3.ZERO).getFirst();
				var strider = spawnConfigured(ctx, FormingTests.riftIn(ctx).id(), Slot.SUPPORT, RIDDEN_STRIDER, ctx.absolutePos(new BlockPos(14, 1, 12))).stream()
						.filter(e -> e instanceof net.minecraft.world.entity.monster.Strider).findFirst().orElseThrow();
				var boat = ctx.spawn(EntityTypes.OAK_BOAT, new BlockPos(10, 1, 10));
				ctx.assertTrue(piglin.startRiding(boat, true, false), "the piglin couldn't be put in the boat");
				ctx.runAfterDelay(3, () -> {
					ctx.assertTrue(piglin.getVehicle() == null, "the rift piglin stays in a boat");
					ctx.assertTrue(!strider.getPassengers().isEmpty(), "the strider's rider got off");
					ctx.killAllEntities();
					FormingTests.cleanUp(ctx);
					ctx.succeed();
				});
			});
		});
		NetherRiftsTests.register("mobs/ignore_gold_armour", ctx -> {
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var player = StageTests.player(ctx);
				var spot = ctx.absolutePos(new BlockPos(14, 1, 12));
				player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
				player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
				player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
				var rift = FormingTests.riftIn(ctx);
				var riftPiglin = (Piglin) RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.RANGED, ctx.absolutePos(new BlockPos(10, 1, 12)), Vec3.ZERO).getFirst();
				var plainPiglin = ctx.spawn(EntityTypes.PIGLIN, new BlockPos(10, 1, 14));
				ctx.runAfterDelay(60, () -> {
					ctx.assertTrue(riftPiglin.getBrain().getMemory(MemoryModuleType.NEAREST_TARGETABLE_PLAYER_NOT_WEARING_GOLD).isPresent(),
							"the rift piglin respects the gold truce");
					ctx.assertTrue(plainPiglin.getBrain().getMemory(MemoryModuleType.NEAREST_TARGETABLE_PLAYER_NOT_WEARING_GOLD).isEmpty(),
							"a plain piglin ignores gold too: the mixin is too broad");
					StageTests.remove(ctx, player);
					ctx.killAllEntities();
					FormingTests.cleanUp(ctx);
					ctx.succeed();
				});
			});
		});
		NetherRiftsTests.register("mobs/no_pickup", ctx -> {
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				var piglin = (Piglin) RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.RANGED, ctx.absolutePos(new BlockPos(12, 1, 12)), Vec3.ZERO).getFirst();
				ctx.spawnItem(Items.GOLD_INGOT, new BlockPos(12, 1, 12));
				ctx.runAfterDelay(60, () -> {
					ctx.assertItemEntityPresent(Items.GOLD_INGOT);
					ctx.assertTrue(piglin.getOffhandItem().isEmpty(), "the rift piglin took the gold");
					ctx.killAllEntities();
					FormingTests.cleanUp(ctx);
					ctx.succeed();
				});
			});
		});
		NetherRiftsTests.register("mobs/fireball_no_fire", ctx -> {
			FormingTests.lightFrame(ctx, 2, 3);
			for (int x = 8; x < 16; x++) {
				for (int z = 8; z < 16; z++) {
					ctx.setBlock(new BlockPos(x, 1, z), Blocks.OAK_PLANKS);
				}
			}
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				var ghast = (Ghast) RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.HEAVY, ctx.absolutePos(new BlockPos(12, 8, 12)), Vec3.ZERO).getFirst();
				var fireball = new LargeFireball(ctx.getLevel(), ghast, new Vec3(0, -1, 0), 1);
				var from = ctx.absolutePos(new BlockPos(12, 4, 12));
				fireball.setPos(from.getX() + 0.5, from.getY(), from.getZ() + 0.5);
				ctx.getLevel().addFreshEntity(fireball);
			});
			ctx.runAtTickTime(40, () -> {
				for (var pos : BlockPos.betweenClosed(ctx.absolutePos(new BlockPos(6, 1, 6)), ctx.absolutePos(new BlockPos(18, 5, 18)))) {
					ctx.assertFalse(ctx.getLevel().getBlockState(pos).is(Blocks.FIRE), "fire at " + pos.toShortString());
				}
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("mobs/ghast_steers_itself", ctx -> {
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				// a configured roster's ridden ghast: the defaults' has no rider
				var spawned = spawnConfigured(ctx, rift.id(), Slot.HEAVY, RIDDEN_GHAST, ctx.absolutePos(new BlockPos(12, 6, 12)));
				var ghast = (Ghast) spawned.stream().filter(e -> e instanceof Ghast).findFirst().orElseThrow();
				ctx.assertTrue(ghast.getFirstPassenger() instanceof Piglin, "the rift ghast has no piglin rider");
				ctx.assertTrue(ghast.getControllingPassenger() == null, "the rider steers the rift ghast");
				var plain = ctx.spawn(EntityTypes.GHAST, new BlockPos(4, 6, 18));
				var rider = ctx.spawn(EntityTypes.PIGLIN, new BlockPos(4, 6, 18));
				rider.startRiding(plain, true, false);
				ctx.assertTrue(plain.getControllingPassenger() == rider, "vanilla changed: a plain ghast's mob rider doesn't steer it");
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("mobs/spawn_eggs", ctx -> {
			for (var egg : List.of(new Object[] {RiftItems.WAR_DRUMMER_SPAWN_EGG, RiftEntities.WAR_DRUMMER},
					new Object[] {RiftItems.WAR_GHAST_SPAWN_EGG, RiftEntities.WAR_GHAST}, new Object[] {RiftItems.GHAST_BOMBARDIER_SPAWN_EGG, RiftEntities.GHAST_BOMBARDIER},
					new Object[] {RiftItems.PIGLIN_GENERAL_SPAWN_EGG, RiftEntities.PIGLIN_GENERAL})) {
				var type = SpawnEggItem.getType(new ItemStack((Item) egg[0]));
				ctx.assertTrue(type == egg[1], egg[0] + " spawns " + type);
				// metacraft-core's Spawn Eggs creative tab gathers the eggs of every namespace opted in (RiftItems.init)
				ctx.assertTrue(SpawnEggsCreativeTab.eggs().contains((Item) egg[0]), egg[0] + " isn't in the Spawn Eggs tab");
			}
			// the Grenadier's kit is hidden from the METAcraft tab
			var tab = METAcraftCreativeTab.items();
			ctx.assertFalse(tab.contains(RiftItems.BOMBARDIER_HELMET) || tab.contains(RiftItems.GRENADIER_SWORD), "the Grenadier's kit is in the METAcraft tab");
			ctx.succeed();
		});
		NetherRiftsTests.register("mobs/names_translated", ctx -> {
			// Death messages and the like name the mob by its description id: each needs a line in the lang files.
			for (var lang : new String[] {"en_us", "sv_se"}) {
				var path = "/assets/nether_rifts/lang/" + lang + ".json";
				var in = NetherRifts.class.getResourceAsStream(path);
				ctx.assertTrue(in != null, "no " + path);
				JsonObject names;
				try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
					names = JsonParser.parseReader(reader).getAsJsonObject();
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
				for (var type : List.of(RiftEntities.WAR_DRUMMER, RiftEntities.WAR_GHAST, RiftEntities.GHAST_BOMBARDIER, RiftEntities.MAGMA_BOMB, RiftEntities.PIGLIN_GENERAL)) {
					ctx.assertTrue(names.has(type.getDescriptionId()), lang + " doesn't name " + type.getDescriptionId());
				}
				ctx.assertTrue(names.has(RiftBlocks.RIFT.getDescriptionId()), lang + " doesn't name " + RiftBlocks.RIFT.getDescriptionId());
				for (var item : List.of(RiftItems.BOMBARDIER_HELMET, RiftItems.GRENADIER_SWORD, RiftItems.WAR_DRUMMER_SPAWN_EGG, RiftItems.WAR_GHAST_SPAWN_EGG, RiftItems.GHAST_BOMBARDIER_SPAWN_EGG,
						RiftItems.PIGLIN_GENERAL_SPAWN_EGG)) {
					ctx.assertTrue(names.has(item.getDescriptionId()), lang + " doesn't name " + item.getDescriptionId());
				}
			}
			ctx.succeed();
		});
		NetherRiftsTests.register("mobs/orphan_discarded", ctx -> {
			// Review focus 3: a rift mob whose rift no longer exists never enters the level. It is vetoed at load, not
			// discarded afterwards, which would leave it registered with the entity manager and ticking forever.
			var piglin = EntityTypes.PIGLIN.create(ctx.getLevel(), net.minecraft.world.entity.EntitySpawnReason.EVENT);
			piglin.setAttached(RiftMobs.RIFT, UUID.randomUUID());
			var at = ctx.absolutePos(new BlockPos(12, 1, 12));
			piglin.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
			ctx.assertFalse(ctx.getLevel().addFreshEntity(piglin), "a mob of a missing rift was added");
			ctx.runAtTickTime(5, () -> {
				ctx.assertTrue(ctx.getLevel().getEntity(piglin.getUUID()) == null, "a mob of a missing rift is in the level");
				ctx.assertTrue(piglin.tickCount == 0, "a mob of a missing rift ticked");
				ctx.assertTrue(ctx.getEntities(EntityTypes.PIGLIN).isEmpty(), "a mob of a missing rift is in the room");
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("mobs/fireball_from_dead_ghast_no_fire", ctx -> {
			FormingTests.lightFrame(ctx, 2, 3);
			for (int x = 8; x < 16; x++) {
				for (int z = 8; z < 16; z++) {
					ctx.setBlock(new BlockPos(x, 1, z), Blocks.OAK_PLANKS);
				}
			}
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				var spawned = RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.HEAVY, ctx.absolutePos(new BlockPos(12, 8, 12)), Vec3.ZERO);
				var ghast = (Ghast) spawned.stream().filter(e -> e instanceof Ghast).findFirst().orElseThrow();
				var fireball = new LargeFireball(ctx.getLevel(), ghast, new Vec3(0, -1, 0), 1);
				var from = ctx.absolutePos(new BlockPos(12, 4, 12));
				fireball.setPos(from.getX() + 0.5, from.getY(), from.getZ() + 0.5);
				ctx.getLevel().addFreshEntity(fireball);
				ctx.assertTrue(RiftMobs.isRiftMob(fireball), "the fireball of a rift ghast isn't marked");
				// the shooter dies before the fireball lands, so the owner can no longer vouch for it
				spawned.forEach(net.minecraft.world.entity.Entity::discard);
				ctx.assertTrue(fireball.getOwner() == null, "the owner is still around: the test proves nothing");
			});
			ctx.runAtTickTime(40, () -> {
				for (var pos : BlockPos.betweenClosed(ctx.absolutePos(new BlockPos(6, 1, 6)), ctx.absolutePos(new BlockPos(18, 5, 18)))) {
					ctx.assertFalse(ctx.getLevel().getBlockState(pos).is(Blocks.FIRE), "fire at " + pos.toShortString());
				}
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("mobs/piglins_leave_hoglins_alone", ctx -> {
			FormingTests.lightFrame(ctx, 2, 3);
			var piglin = new Piglin[1];
			var hoglins = new ArrayList<Hoglin>();
			var healths = new ArrayList<Float>();
			var done = new boolean[1];
			// Up front: onEachTick from inside a scheduled runnable crashes 26.3.
			ctx.onEachTick(() -> {
				if (piglin[0] == null || done[0]) {
					return;
				}
				var brain = piglin[0].getBrain();
				ctx.assertTrue(piglin[0].getTarget() == null && brain.getMemory(MemoryModuleType.ATTACK_TARGET).isEmpty(),
						"the rift piglin is going for something");
				ctx.assertTrue(brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_HUNTABLE_HOGLIN).isEmpty(),
						"the rift piglin sees a hoglin to hunt");
				for (int i = 0; i < hoglins.size(); i++) {
					ctx.assertTrue(hoglins.get(i).getHealth() >= healths.get(i), "a rift hoglin was hurt");
				}
			});
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				// The melee roster picks at random: keep spawning until there is a plain hoglin and a jockey one.
				Hoglin plain = null;
				Hoglin jockey = null;
				for (int i = 0; i < 300 && (plain == null || jockey == null); i++) {
					var spawned = RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.MELEE, ctx.absolutePos(new BlockPos(12, 1, 16)), Vec3.ZERO);
					var hoglin = spawned.stream().filter(e -> e instanceof Hoglin).map(e -> (Hoglin) e).findFirst().orElse(null);
					if (hoglin != null && hoglin.isVehicle() && jockey == null) {
						jockey = hoglin;
					} else if (hoglin != null && !hoglin.isVehicle() && plain == null) {
						plain = hoglin;
					} else {
						spawned.forEach(net.minecraft.world.entity.Entity::discard);
					}
				}
				ctx.assertTrue(plain != null && jockey != null, "the melee roster never gave both a plain and a jockey hoglin");
				ctx.assertTrue(jockey.getFirstPassenger() instanceof Piglin, "the jockey hoglin has no piglin rider");
				for (var hoglin : List.of(plain, jockey)) {
					ctx.assertTrue(RiftMobs.isRiftMob(hoglin), "a hoglin isn't a rift mob");
					ctx.assertFalse(hoglin.canBeHunted(), "a rift hoglin can be hunted");
					hoglins.add(hoglin);
					healths.add(hoglin.getHealth());
				}
				var at = ctx.absolutePos(new BlockPos(12, 1, 10));
				var hunter = (Piglin) RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.RANGED, at, Vec3.ZERO).getFirst();
				piglin[0] = hunter;
			});
			// Vanilla piglins pick a hoglin within a second or two of seeing it.
			ctx.runAtTickTime(200, () -> {
				ctx.assertTrue(piglin[0].isAlive(), "the rift piglin died");
				// the check above runs every tick, including after the cleanup kills the hoglins
				done[0] = true;
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("mobs/rift_mobs_spare_each_other", ctx -> {
			// A rift's mobs can't hurt each other, so none turns on another: a crossbow piglin's blow on its rift's hoglin,
			// and the hoglin's back, do nothing.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var level = ctx.getLevel();
				var rift = FormingTests.riftIn(ctx);
				var piglin = (Piglin) RiftMobs.spawn(level, rift.id(), Slot.RANGED, ctx.absolutePos(new BlockPos(10, 1, 12)), Vec3.ZERO).getFirst();
				var hoglin = (Hoglin) spawnConfigured(ctx, rift.id(), Slot.MELEE, "{id:\"minecraft:hoglin\"}", ctx.absolutePos(new BlockPos(14, 1, 12))).getFirst();
				var sources = level.damageSources();
				ctx.assertFalse(hoglin.hurtServer(level, sources.mobAttack(piglin), 4), "the piglin's blow hurt its rift's hoglin");
				ctx.assertFalse(piglin.hurtServer(level, sources.mobAttack(hoglin), 4), "the hoglin's blow hurt its rift's piglin");
				ctx.assertTrue(hoglin.getHealth() == hoglin.getMaxHealth(), "the hoglin is at " + hoglin.getHealth());
				ctx.assertTrue(piglin.getHealth() == piglin.getMaxHealth(), "the piglin is at " + piglin.getHealth());
				ctx.assertTrue(hoglin.getLastHurtByMob() == null && piglin.getLastHurtByMob() == null, "one of them counts the other as its attacker");
				ctx.runAfterDelay(40, () -> {
					ctx.assertTrue(hoglin.getTarget() != piglin && !hoglin.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, piglin),
							"the hoglin turned on the piglin");
					ctx.assertTrue(piglin.getTarget() != hoglin && !piglin.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, hoglin),
							"the piglin turned on the hoglin");
					ctx.assertFalse(piglin.getBrain().isMemoryValue(MemoryModuleType.AVOID_TARGET, hoglin), "the piglin is fleeing the hoglin");
					ctx.killAllEntities();
					FormingTests.cleanUp(ctx);
					ctx.succeed();
				});
			});
		});
		NetherRiftsTests.register("mobs/rift_bolt_flies_through_own_side", ctx -> {
			// A rift piglin's bolt, shot through its rift's hoglin at a player behind it, flies through the hoglin and hits the player.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var level = ctx.getLevel();
				var rift = FormingTests.riftIn(ctx);
				var player = StageTests.player(ctx);
				DrummerTests.ready(player);
				var spot = ctx.absolutePos(new BlockPos(12, 1, 20));
				player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
				var piglin = (Piglin) RiftMobs.spawn(level, rift.id(), Slot.RANGED, ctx.absolutePos(new BlockPos(12, 1, 9)), Vec3.ZERO).getFirst();
				piglin.setNoAi(true);
				// still, so it stays in the bolt's way and goes for nobody
				var hoglin = (Hoglin) spawnConfigured(ctx, rift.id(), Slot.MELEE, "{id:\"minecraft:hoglin\",NoAI:1b}", ctx.absolutePos(new BlockPos(12, 1, 15))).getFirst();
				var bolt = new net.minecraft.world.entity.projectile.arrow.Arrow(level, piglin, new ItemStack(Items.ARROW), null);
				var from = ctx.absolutePos(new BlockPos(12, 1, 11));
				bolt.setPos(from.getX() + 0.5, from.getY() + 0.9, from.getZ() + 0.5);
				bolt.shoot(0, 0, 1, 3.0f, 0f);
				level.addFreshEntity(bolt);
				ctx.runAfterDelay(10, () -> {
					try {
						ctx.assertTrue(hoglin.getHealth() == hoglin.getMaxHealth(), "the bolt hurt its rift's hoglin: " + hoglin.getHealth());
						var source = player.getLastDamageSource();
						ctx.assertTrue(source != null && source.getDirectEntity() == bolt, "the bolt didn't hit the player: it is at " + bolt.position()
								+ "; the player was last hurt by " + (source == null ? null : source.getDirectEntity()));
					} finally {
						StageTests.remove(ctx, player);
						ctx.killAllEntities();
						FormingTests.cleanUp(ctx);
					}
					ctx.succeed();
				});
			});
		});
		NetherRiftsTests.register("mobs/other_rift_still_hurts", ctx -> {
			// Sparing is within one rift: a rift piglin's blow still hurts the nether's own hoglin and another rift's.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var level = ctx.getLevel();
				var rift = FormingTests.riftIn(ctx);
				var piglin = (Piglin) RiftMobs.spawn(level, rift.id(), Slot.RANGED, ctx.absolutePos(new BlockPos(10, 1, 12)), Vec3.ZERO).getFirst();
				piglin.setNoAi(true);
				var stranger = ctx.spawn(EntityTypes.HOGLIN, new BlockPos(14, 1, 10));
				stranger.setNoAi(true);
				var other = spawnConfigured(ctx, rift.id(), Slot.MELEE, "{id:\"minecraft:hoglin\",NoAI:1b}", ctx.absolutePos(new BlockPos(14, 1, 14))).getFirst();
				// another rift's: tagged once in the level, as a rift that doesn't exist would keep it out at load
				other.setAttached(RiftMobs.RIFT, UUID.randomUUID());
				var sources = level.damageSources();
				ctx.assertTrue(stranger.hurtServer(level, sources.mobAttack(piglin), 4), "the piglin's blow didn't hurt a hoglin of no rift");
				ctx.assertTrue(stranger.getHealth() < stranger.getMaxHealth(), "the hoglin of no rift is at full health");
				var otherHoglin = (Hoglin) other;
				ctx.assertTrue(otherHoglin.hurtServer(level, sources.mobAttack(piglin), 4), "the piglin's blow didn't hurt another rift's hoglin");
				ctx.assertTrue(otherHoglin.getHealth() < otherHoglin.getMaxHealth(), "another rift's hoglin is at full health");
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("mobs/no_bartering", ctx -> {
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var player = StageTests.player(ctx);
				var spot = ctx.absolutePos(new BlockPos(12, 1, 12));
				player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
				var rift = FormingTests.riftIn(ctx);
				var riftPiglin = (Piglin) RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.RANGED, ctx.absolutePos(new BlockPos(12, 1, 13)), Vec3.ZERO).getFirst();
				var plainPiglin = ctx.spawn(EntityTypes.PIGLIN, new BlockPos(8, 1, 13));
				var ingot = new ItemStack(Items.GOLD_INGOT);
				player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ingot);
				player.interactOn(plainPiglin, net.minecraft.world.InteractionHand.MAIN_HAND, Vec3.ZERO);
				ctx.assertTrue(plainPiglin.getOffhandItem().is(Items.GOLD_INGOT), "control: a plain piglin didn't take the gold, so the interaction proves nothing");
				var ingot2 = new ItemStack(Items.GOLD_INGOT);
				player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ingot2);
				player.interactOn(riftPiglin, net.minecraft.world.InteractionHand.MAIN_HAND, Vec3.ZERO);
				ctx.assertTrue(riftPiglin.getOffhandItem().isEmpty(), "the rift piglin took the gold");
				ctx.assertTrue(player.getMainHandItem().is(Items.GOLD_INGOT), "the player lost the gold to a rift piglin");
				ctx.runAfterDelay(10, () -> {
					ctx.assertTrue(riftPiglin.getOffhandItem().isEmpty(), "the rift piglin took the gold");
					ctx.assertTrue(player.getMainHandItem().is(Items.GOLD_INGOT), "the player lost the gold to a rift piglin");
					ctx.assertTrue(riftPiglin.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ADMIRING_ITEM).isEmpty(),
							"the rift piglin is admiring");
					StageTests.remove(ctx, player);
					ctx.killAllEntities();
					FormingTests.cleanUp(ctx);
					ctx.succeed();
				});
			});
		});
	}
}
