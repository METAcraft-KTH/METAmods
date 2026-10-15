
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
import nu.metacraft.nether_rifts.NetherRifts;
import nu.metacraft.nether_rifts.mob.RiftEntities;
import nu.metacraft.nether_rifts.mob.RiftItems;
import nu.metacraft.nether_rifts.raid.RiftMobs;
import nu.metacraft.nether_rifts.raid.Slot;
import nu.metacraft.nether_rifts.rift.RiftBlocks;

public class MobTests {

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
				var spawned = RiftMobs.spawn(ctx.getLevel(), rift.id(), Slot.HEAVY, ctx.absolutePos(new BlockPos(12, 6, 12)), Vec3.ZERO);
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
					new Object[] {RiftItems.WAR_GHAST_SPAWN_EGG, RiftEntities.WAR_GHAST}, new Object[] {RiftItems.GHAST_BOMBARDIER_SPAWN_EGG, RiftEntities.GHAST_BOMBARDIER})) {
				var type = SpawnEggItem.getType(new ItemStack((Item) egg[0]));
				ctx.assertTrue(type == egg[1], egg[0] + " spawns " + type);
				// metacraft-core's Spawn Eggs creative tab gathers the eggs of every namespace opted in (RiftItems.init)
				ctx.assertTrue(nu.metacraft.core.item.SpawnEggsCreativeTab.eggs().contains((Item) egg[0]), egg[0] + " isn't in the Spawn Eggs tab");
			}
			// the Grenadier's kit is hidden from the METAcraft tab
			var tab = nu.metacraft.core.item.METAcraftCreativeTab.items();
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
				for (var type : List.of(RiftEntities.WAR_DRUMMER, RiftEntities.WAR_GHAST, RiftEntities.GHAST_BOMBARDIER, RiftEntities.MAGMA_BOMB)) {
					ctx.assertTrue(names.has(type.getDescriptionId()), lang + " doesn't name " + type.getDescriptionId());
				}
				ctx.assertTrue(names.has(RiftBlocks.RIFT.getDescriptionId()), lang + " doesn't name " + RiftBlocks.RIFT.getDescriptionId());
				for (var item : List.of(RiftItems.BOMBARDIER_HELMET, RiftItems.GRENADIER_SWORD, RiftItems.WAR_DRUMMER_SPAWN_EGG, RiftItems.WAR_GHAST_SPAWN_EGG, RiftItems.GHAST_BOMBARDIER_SPAWN_EGG)) {
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
