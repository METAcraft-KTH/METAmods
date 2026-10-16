import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.tomalbrc.bil.core.model.Node;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.nether_rifts.NetherRifts;
import nu.metacraft.nether_rifts.mob.ArmourPieces;
import nu.metacraft.nether_rifts.mob.BombingRunGoal;
import nu.metacraft.nether_rifts.mob.BurningPatches;
import nu.metacraft.nether_rifts.mob.GhastBombardier;
import nu.metacraft.nether_rifts.mob.GrenadierSword;
import nu.metacraft.nether_rifts.mob.HeldItems;
import nu.metacraft.nether_rifts.mob.RiftEntities;
import nu.metacraft.nether_rifts.mob.RiftItems;
import nu.metacraft.nether_rifts.mob.WarDrummer;
import nu.metacraft.nether_rifts.mob.WarGhast;
import nu.metacraft.nether_rifts.raid.RiftMobs;
import nu.metacraft.nether_rifts.raid.RiftRaid;
import nu.metacraft.nether_rifts.raid.Slot;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class BombardierTests {

	static void registerTests() {
		NetherRiftsTests.register("bombardier/crew_on_deck", ctx -> {
			// The crew are part of the war ghast's model: it carries no riders, and its model has the four figures.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var spawned = RiftMobs.spawn(ctx.getLevel(), FormingTests.riftIn(ctx).id(), Slot.BOMBARDIER, ctx.absolutePos(new BlockPos(12, 8, 12)), Vec3.ZERO);
				ctx.assertTrue(spawned.size() == 1 && spawned.getFirst() instanceof WarGhast, "the Bombardier slot spawned " + spawned);
				var ghast = (WarGhast) spawned.getFirst();
				ctx.assertFalse(ghast.isVehicle(), "the war ghast carries " + ghast.getPassengers());
				ctx.assertTrue(RiftMobs.isRiftMob(ghast), "the war ghast isn't a rift mob");
				var bones = ghast.getHolder().getModel().nodeMap().values().stream().map(Node::name).collect(Collectors.toSet());
				for (var bone : List.of("drummer_piglin", "spear_piglin", "crossbow_piglin", "bombardier", "magma_bomb", "deck")) {
					ctx.assertTrue(bones.contains(bone), "the war ghast's model has no " + bone + ": " + bones);
				}
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("bombardier/run_drops_bombs", ctx -> {
			stoneFloor(ctx);
			var bombs = new AtomicInteger();
			ctx.onEachTick(() -> bombs.addAndGet(ctx.getEntities(RiftEntities.MAGMA_BOMB).size()));
			FormingTests.lightFrame(ctx, 2, 3);
			var player = StageTests.player(ctx);
			ctx.runAtTickTime(3, () -> {
				var floor = ctx.absolutePos(new BlockPos(16, 1, 16));
				player.teleportTo(floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5);
				var ghast = warGhast(ctx, new BlockPos(8, 10, 8));
				ghast.setTarget(player);
			});
			ctx.succeedWhen(() -> {
				ctx.assertTrue(bombs.get() > 0, "no bomb dropped yet");
				// a dropped bomb is not yet a blast: wait for one to land, or the floor check below proves nothing
				ctx.assertTrue(BurningPatches.active(ctx.getLevel()).stream().anyMatch(p -> !p.warning() && ctx.getBounds().contains(p.at())), "no bomb landed yet");
				for (int x = 0; x < 24; x++) {
					for (int z = 0; z < 24; z++) {
						ctx.assertBlockPresent(Blocks.STONE, new BlockPos(x, 0, z));
					}
				}
				StageTests.remove(ctx, player);
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
			});
		});
		NetherRiftsTests.register("bombardier/deck_clear_of_low_roof", ctx -> {
			// A roof just over the ghast at run height (the deck 4 above the target): room for the ghast and its deck to
			// run, not to climb all the way. The deck never goes into the roof, the floor or anything else.
			stoneFloor(ctx);
			roof(ctx, (int) Math.ceil(1 + BombingRunGoal.DIVE_HEIGHT + WarGhast.DECK_DROP + 4));
			var buried = new AtomicInteger();
			var bombs = new AtomicInteger();
			ctx.onEachTick(() -> {
				bombs.addAndGet(ctx.getEntities(RiftEntities.MAGMA_BOMB).size());
				ctx.getEntities(RiftEntities.WAR_GHAST).forEach(ghast -> {
					if (!ctx.getLevel().noBlockCollision(ghast, ghast.stackBox())) {
						buried.incrementAndGet();
					}
				});
			});
			FormingTests.lightFrame(ctx, 2, 3);
			var player = StageTests.player(ctx);
			ctx.runAtTickTime(3, () -> {
				var floor = ctx.absolutePos(new BlockPos(16, 1, 16));
				player.teleportTo(floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5);
				// the deck just clear of the floor
				warGhast(ctx, new BlockPos(8, 1 + (int) Math.ceil(WarGhast.DECK_DROP), 8)).setTarget(player);
			});
			ctx.runAtTickTime(300, () -> {
				ctx.assertTrue(buried.get() == 0, "the war ghast's deck was in blocks for " + buried.get() + " ticks");
				ctx.assertTrue(bombs.get() > 0, "no bombing run under the roof");
				StageTests.remove(ctx, player);
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("bombardier/idle_ghast_not_stuck_over_floor", ctx -> {
			// A float target just over the floor: room for the ghast alone, not for the deck hanging under it. The ghast
			// stops short of it, its deck clear of the floor, and floats off elsewhere, rather than hovering there for good.
			stoneFloor(ctx);
			var ghast = ctx.spawn(RiftEntities.WAR_GHAST, new BlockPos(12, 8, 12));
			// the ghast's feet a hair over the floor; it stops more than a block short (closer, vanilla would pick anew)
			var wanted = ghast.position().add(0, 1 - 8 + 0.05, 0);
			ghast.getMoveControl().setWantedPosition(wanted.x, wanted.y, wanted.z, 1.0);
			var buried = new AtomicInteger();
			var gaveUp = new AtomicBoolean();
			ctx.onEachTick(() -> {
				ghast.setTarget(null);
				if (!ctx.getLevel().noBlockCollision(ghast, ghast.stackBox())) {
					buried.incrementAndGet();
				}
				var move = ghast.getMoveControl();
				if (!move.hasWanted() || move.getWantedY() != wanted.y) {
					gaveUp.set(true);
				}
			});
			ctx.runAtTickTime(100, () -> {
				ctx.assertTrue(gaveUp.get(), "the ghast still hovers over the floor, flying at a point its deck doesn't fit at");
				ctx.assertTrue(buried.get() == 0, "the war ghast's deck was in the floor for " + buried.get() + " ticks");
				ctx.killAllEntities();
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("bombardier/summoned_ghast_sheds_riders", ctx -> {
			// A /summon with an old-style Bombardier rider, no rift: the rider goes (the crew are the ghast's own), and the
			// ghast still flies itself.
			var at = Vec3.atBottomCenterOf(ctx.absolutePos(new BlockPos(12, 6, 12)));
			var ghast = EntityType.loadEntityRecursive(nbt("{id:\"nether_rifts:war_ghast\",Passengers:[{id:\"nether_rifts:ghast_bombardier\"}]}"),
					ctx.getLevel(), new EntitySpawnRequest(EntitySpawnReason.COMMAND, false), e -> {
						e.snapTo(at.x, at.y, at.z, 0, 0);
						return e;
					});
			ctx.assertTrue(ghast instanceof WarGhast && ghast.isVehicle(), "the summon didn't load a war ghast with a rider");
			ctx.getLevel().tryAddFreshEntityWithPassengers(ghast);
			ctx.runAfterDelay(3, () -> {
				ctx.assertFalse(ghast.isVehicle(), "the war ghast still carries " + ghast.getPassengers());
				ctx.assertTrue(ctx.getEntities(RiftEntities.GHAST_BOMBARDIER).isEmpty(), "the summoned rider is still about");
				ctx.assertFalse(RiftMobs.isRiftMob(ghast), "a summoned war ghast is a rift mob");
				ctx.assertTrue(((WarGhast) ghast).getControllingPassenger() == null, "something steers the war ghast");
				ctx.killAllEntities();
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("bombardier/crew_bail_out", ctx -> {
			// The ghast's death puts its crew where they stood in the model as real mobs, of its rift, gliding down unhurt:
			// the drummer, the Grenadier, the spear piglin with the ghast's spear and the crossbow piglin with its crossbow.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				var ghast = warGhast(ctx, new BlockPos(12, 9, 12));
				// a few ticks into its idle, where the figures stand turned as the artist posed them
				ctx.runAfterDelay(5, () -> {
					var deck = ghast.stackBox().setMaxY(ghast.getY());
					var spots = new java.util.EnumMap<WarGhast.Crew, WarGhast.Spot>(WarGhast.Crew.class);
					for (var figure : WarGhast.Crew.values()) {
						spots.put(figure, ghast.spot(figure));
					}
					ghast.hurtServer(ctx.getLevel(), ctx.getLevel().damageSources().generic(), 1000f);
					var crew = crew(ctx, deck);
					ctx.assertTrue(crew.size() == 4, "the ghast's death let off " + crew);
					for (var figure : WarGhast.Crew.values()) {
						var spot = spots.get(figure);
						var mob = crew.stream().filter(m -> m.position().distanceTo(spot.feet()) < 1e-3).findFirst().orElse(null);
						ctx.assertTrue(mob != null, "no one jumped off where " + figure.figure + " stood, " + spot.feet() + ": " + crew);
						ctx.assertTrue(switch (figure) {
							case DRUMMER -> mob instanceof WarDrummer;
							case BOMBARDIER -> mob instanceof GhastBombardier b && b.getItemBySlot(EquipmentSlot.HEAD).is(RiftItems.BOMBARDIER_HELMET);
							case SPEAR -> mob.getType() == EntityTypes.PIGLIN && mob.getMainHandItem().is(Items.GOLDEN_SPEAR);
							case CROSSBOW -> mob.getType() == EntityTypes.PIGLIN && mob.getMainHandItem().is(Items.CROSSBOW);
						}, figure.figure + " came off as " + mob + " holding " + mob.getMainHandItem());
						ctx.assertTrue(Mth.degreesDifferenceAbs(mob.getYRot(), spot.yaw()) < 1, mob + " doesn't face as " + figure.figure + " did");
						// on the deck; the Grenadier sits on its stern, his feet hanging over (a block at most below it)
						double lowest = ghast.getY() - WarGhast.DECK_DROP - (figure == WarGhast.Crew.BOMBARDIER ? 1 : 0);
						ctx.assertTrue(spot.feet().y >= lowest && spot.feet().y < ghast.getY(), figure.figure + " stood off the deck, at " + spot.feet());
					}
					// the drummer faces the ghast's way, the Grenadier back off the deck's stern (the artist turned him round);
					// the crossbow piglin is turned to its right
					ctx.assertTrue(Mth.degreesDifferenceAbs(spots.get(WarGhast.Crew.DRUMMER).yaw(), ghast.yBodyRot) < 1, "the drummer faces " + spots.get(WarGhast.Crew.DRUMMER).yaw());
					ctx.assertTrue(Mth.degreesDifferenceAbs(spots.get(WarGhast.Crew.BOMBARDIER).yaw(), ghast.yBodyRot + 180) < 1, "the Grenadier faces " + spots.get(WarGhast.Crew.BOMBARDIER).yaw());
					ctx.assertTrue(Math.abs(Mth.degreesDifferenceAbs(spots.get(WarGhast.Crew.CROSSBOW).yaw(), ghast.yBodyRot) - 90) < 1,
							"the crossbow piglin isn't turned a quarter: " + spots.get(WarGhast.Crew.CROSSBOW).yaw() + " on " + ghast.yBodyRot);
					for (var mob : crew) {
						ctx.assertTrue(mob.hasEffect(MobEffects.SLOW_FALLING), mob + " doesn't glide");
						ctx.assertTrue(rift.id().equals(mob.getAttached(RiftMobs.RIFT)), mob + " isn't of the ghast's rift");
					}
					// the ghast's 20-tick death, then a slow-falling glide down
					ctx.runAfterDelay(100, () -> {
						for (var mob : crew) {
							ctx.assertTrue(mob.isAlive(), mob + " died");
							ctx.assertTrue(mob.onGround(), mob + " hangs in the air");
							ctx.assertTrue(mob.getHealth() == mob.getMaxHealth(), mob + " was hurt coming down: " + mob.getHealth());
							ctx.assertTrue(RiftRaid.aliveMobs(ctx.getLevel(), rift).contains(mob), mob + " doesn't count toward the wave");
						}
						ctx.killAllEntities();
						FormingTests.cleanUp(ctx);
						ctx.succeed();
					});
				});
			});
		});
		NetherRiftsTests.register("bombardier/crew_gone_from_model", ctx -> {
			// Once the crew have jumped, their figures (armour, bomb and all) are gone from the ghast's model; the ghast,
			// its harness, faces and deck stay. Showing every bone again (as BIL does when the ghast's flags change)
			// doesn't bring them back.
			var ghast = ctx.spawn(RiftEntities.WAR_GHAST, new BlockPos(12, 8, 12));
			ghast.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			ctx.runAtTickTime(3, () -> {
				var holder = ghast.getHolder();
				for (var figure : WarGhast.Crew.values()) {
					ctx.assertFalse(ghast.crewBones(figure).isEmpty(), figure.figure + " has no bones");
					ctx.assertTrue(ghast.crewBones(figure).stream().anyMatch(bone -> !shown(bone).isEmpty()), figure.figure + " doesn't show");
				}
				ctx.assertFalse(shown(holder.getBone(ArmourPieces.Piece.CHEST.bone)).isEmpty(), "the chestplate doesn't show on the spear piglin");
				ghast.hurtServer(ctx.getLevel(), ctx.getLevel().damageSources().generic(), 1000f);
				ctx.runAfterDelay(2, () -> {
					ghast.setSharedFlagOnFire(true);
					ghast.setSharedFlagOnFire(false);
					for (var figure : WarGhast.Crew.values()) {
						for (var bone : ghast.crewBones(figure)) {
							ctx.assertTrue(shown(bone).isEmpty(), bone.name() + " still shows after the crew jumped");
						}
					}
					ctx.assertTrue(shown(holder.getBone(ArmourPieces.Piece.CHEST.bone)).isEmpty(), "the chestplate stayed on the ghast");
					for (var bone : List.of("chains", "deck", "tentacle1", WarGhast.FACE)) {
						ctx.assertFalse(shown(holder.getBone(bone)).isEmpty(), bone + " went with the crew");
					}
					for (var spot : HeldItems.Spot.values()) {
						ctx.assertTrue(holder.heldItems().display(spot).getItem().isEmpty(), "the ghast still shows " + spot);
					}
					ctx.killAllEntities();
					ctx.succeed();
				});
			});
		});
		NetherRiftsTests.register("bombardier/animations_play", ctx -> {
			// Idle always plays. Charging a fireball plays shoot, and the shooting face shows in place of the sad one
			// (hidden and shown, never scaled) from SHOOTING_FACE_FROM to SHOOTING_FACE_UNTIL ticks into it. The first bomb of a run starts the Grenadier's bombing, which goes on while bombs come and stops after.
			// A bombing run's dive and climb play as it dives and climbs (bombardier/run_drops_bombs flies one).
			stoneFloor(ctx);
			var ghast = ctx.spawn(RiftEntities.WAR_GHAST, new BlockPos(12, 9, 12));
			ghast.setNoAi(true);
			var holder = ghast.getHolder();
			var animator = holder.getAnimator();
			ctx.runAtTickTime(1, () -> {
				ctx.assertFalse(shown(holder.getBone(WarGhast.FACE)).isEmpty(), "the sad face is hidden at rest");
				ctx.assertTrue(shown(holder.getBone(WarGhast.SHOOTING_FACE)).isEmpty(), "the shooting face shows at rest");
				ghast.setCharging(true);
				ctx.assertTrue(animator.isPlaying(WarGhast.SHOOT), "charging doesn't play shoot");
				ghast.setCharging(false);
			});
			ctx.runAtTickTime(2 + WarGhast.SHOOTING_FACE_FROM, () -> {
				ctx.assertTrue(shown(holder.getBone(WarGhast.FACE)).isEmpty(), "the sad face shows at the shot");
				ctx.assertFalse(shown(holder.getBone(WarGhast.SHOOTING_FACE)).isEmpty(), "no shooting face at the shot");
			});
			ctx.runAtTickTime(3 + WarGhast.SHOOTING_FACE_UNTIL, () -> {
				ctx.assertFalse(shown(holder.getBone(WarGhast.FACE)).isEmpty(), "the sad face didn't come back");
				ctx.assertTrue(shown(holder.getBone(WarGhast.SHOOTING_FACE)).isEmpty(), "the shooting face stayed");
			});
			ghast.dropBomb(ctx.getLevel());
			ctx.assertTrue(animator.isPlaying(WarGhast.DROP_BOMB), "a bomb doesn't start the bombing");
			int every = nu.metacraft.nether_rifts.NetherRiftsConfig.getInstance().mobs().bombardier().bombEvery();
			ctx.runAfterDelay(every, () -> {
				ctx.assertTrue(animator.isPlaying(WarGhast.DROP_BOMB), "the bombing stopped between bombs");
				ghast.dropBomb(ctx.getLevel());
			});
			ctx.runAfterDelay(2 * every + 3, () -> {
				ctx.assertFalse(animator.isPlaying(WarGhast.DROP_BOMB), "the bombing goes on without bombs");
				ctx.assertTrue(animator.isPlaying(WarGhast.IDLE), "the idle stopped");
				ctx.killAllEntities();
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("bombardier/gear_on_crew", ctx -> {
			// A summoned ghast has its kit (a golden spear and a crossbow; neither drops, right-handed), shown on the crew
			// where the artist marked it: the main hand in the spear piglin's left fist, the off hand on the crossbow piglin.
			// The Grenadier's goggles are modelled, so the head slot shows nothing; goggles an older ghast wore come off.
			// None of the crew's heads turn with the ghast's look.
			var ghast = RiftEntities.WAR_GHAST.create(ctx.getLevel(), EntitySpawnReason.COMMAND);
			var at = ctx.absolutePos(new BlockPos(12, 8, 12));
			ghast.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
			ghast.setItemSlot(EquipmentSlot.HEAD, new ItemStack(RiftItems.BOMBARDIER_HELMET));
			for (int i = 0; i < 40; i++) {
				ghast.finalizeSpawn(ctx.getLevel(), ctx.getLevel().getCurrentDifficultyAt(at), EntitySpawnReason.COMMAND, null);
				ctx.assertFalse(ghast.isLeftHanded(), "a summoned ghast is left-handed");
			}
			ctx.getLevel().addFreshEntity(ghast);
			ctx.assertTrue(ghast.getMainHandItem().is(Items.GOLDEN_SPEAR), "the ghast holds " + ghast.getMainHandItem());
			ctx.assertTrue(ghast.getOffhandItem().is(Items.CROSSBOW), "the ghast's off hand holds " + ghast.getOffhandItem());
			ctx.assertTrue(ghast.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "the ghast wears " + ghast.getItemBySlot(EquipmentSlot.HEAD));
			for (var slot : EquipmentSlot.values()) {
				ctx.assertTrue(ghast.getDropChances().byEquipment(slot) == 0f, "the ghast's " + slot + " can drop");
			}
			ctx.runAtTickTime(3, () -> {
				var held = ghast.getHolder().heldItems();
				held.tick();
				var model = ghast.getHolder().getModel();
				for (var shownOn : List.of(
						new Object[] {HeldItems.Spot.RIGHT_HAND, "spear_piglin", Items.GOLDEN_SPEAR},
						new Object[] {HeldItems.Spot.LEFT_HAND, "crossbow_piglin", Items.CROSSBOW},
						new Object[] {HeldItems.Spot.HEAD, "bombardier", Items.AIR})) {
					var spot = (HeldItems.Spot) shownOn[0];
					var item = held.display(spot).getItem();
					ctx.assertTrue(item.is((net.minecraft.world.item.Item) shownOn[2]), spot + " shows " + item);
					var marker = HeldItems.marker(model, spot.marker);
					ctx.assertTrue(within(marker, (String) shownOn[1]), spot + " isn't on " + shownOn[1]);
				}
				for (var node : model.nodeMap().values()) {
					ctx.assertFalse(node.tag().isHead(), node.name() + " turns with the ghast's look");
				}
				ctx.killAllEntities();
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("bombardier/armour_for_show", ctx -> {
			// Armour on the ghast shows on the spear piglin, and is only for show: no armour points, no protection from its
			// enchantments, no thorns. When the ghast dies the spear piglin wears it for real (none of it drops).
			var ghast = ctx.spawn(RiftEntities.WAR_GHAST, new BlockPos(12, 8, 12));
			var plain = ctx.spawn(RiftEntities.WAR_GHAST, new BlockPos(4, 8, 4));
			var enchantments = ctx.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
			var chest = new ItemStack(Items.NETHERITE_CHESTPLATE);
			chest.enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.PROTECTION), 4);
			chest.enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.THORNS), 3);
			ghast.setItemSlot(EquipmentSlot.CHEST, chest);
			ghast.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
			ghast.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
			ctx.runAtTickTime(3, () -> {
				var armour = ghast.getHolder().armour();
				armour.tick();
				var bone = armour.bone(ArmourPieces.Piece.CHEST);
				ctx.assertTrue(bone != null && within(bone.node(), "spear_piglin"), "the chestplate isn't on the spear piglin");
				ctx.assertTrue(NetherRifts.getID("war_ghast/armour/netherite/armor_chest").equals(shown(bone).get(net.minecraft.core.component.DataComponents.ITEM_MODEL)),
						"the chestplate shows " + shown(bone));
				ctx.assertTrue(ghast.getArmorValue() == 0, "the ghast has " + ghast.getArmorValue() + " armour");
				ctx.assertTrue(ghast.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS) == 0, "the ghast has armour toughness");
				var zombie = ctx.spawn(EntityTypes.ZOMBIE, new BlockPos(12, 1, 12));
				var hit = ctx.getLevel().damageSources().mobAttack(zombie);
				ghast.hurtServer(ctx.getLevel(), hit, 4f);
				plain.hurtServer(ctx.getLevel(), ctx.getLevel().damageSources().mobAttack(zombie), 4f);
				ctx.assertTrue(ghast.getHealth() == plain.getHealth(), "the armour spared the ghast: " + ghast.getHealth() + " to a bare ghast's " + plain.getHealth());
				ctx.assertTrue(zombie.getHealth() == zombie.getMaxHealth(), "thorns hurt the zombie: " + zombie.getHealth());
				var deck = ghast.stackBox().setMaxY(ghast.getY());
				ghast.hurtServer(ctx.getLevel(), ctx.getLevel().damageSources().generic(), 1000f);
				var spear = crew(ctx, deck).stream().filter(m -> m.getMainHandItem().is(Items.GOLDEN_SPEAR)).findFirst().orElseThrow();
				ctx.assertTrue(spear.getItemBySlot(EquipmentSlot.CHEST).is(Items.NETHERITE_CHESTPLATE) && spear.getItemBySlot(EquipmentSlot.LEGS).is(Items.DIAMOND_LEGGINGS)
						&& spear.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS), "the spear piglin didn't get the armour");
				for (var slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
					ctx.assertTrue(spear.getDropChances().byEquipment(slot) == 0f, "the spear piglin's " + slot + " can drop");
				}
				ctx.assertTrue(ghast.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "the ghast kept the chestplate");
				ctx.killAllEntities();
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("bombardier/crew_lands_softly", ctx -> {
			// Bailing out high up, the crew glide down: a fall that would kill them doesn't.
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				var ghast = warGhast(ctx, new BlockPos(12, 12, 12));
				var deck = ghast.stackBox().setMaxY(ghast.getY());
				ghast.hurtServer(ctx.getLevel(), ctx.getLevel().damageSources().generic(), 1000f);
				var crew = crew(ctx, deck);
				ctx.assertTrue(crew.size() == 4, "the ghast's death let off " + crew);
				// about 8 blocks up: 5 fall damage, so 4 health dies of an unbroken fall
				crew.forEach(mob -> mob.setHealth(4f));
				ctx.runAfterDelay(120, () -> {
					for (var mob : crew) {
						ctx.assertTrue(mob.isAlive(), mob + " died of the fall from its ghast");
						ctx.assertTrue(mob.onGround(), mob + " hangs in the air");
					}
					ctx.killAllEntities();
					FormingTests.cleanUp(ctx);
					ctx.succeed();
				});
			});
		});
		NetherRiftsTests.register("bombardier/summoned_crew_plain", ctx -> {
			// A summoned war ghast (no rift) lets off a plain crew: no rift, no rift rules.
			var ghast = ctx.spawn(RiftEntities.WAR_GHAST, new BlockPos(12, 6, 12));
			var deck = ghast.stackBox().setMaxY(ghast.getY());
			ghast.hurtServer(ctx.getLevel(), ctx.getLevel().damageSources().generic(), 1000f);
			var crew = crew(ctx, deck);
			ctx.assertTrue(crew.size() == 4, "the ghast's death let off " + crew);
			ctx.assertTrue(crew.stream().noneMatch(RiftMobs::isRiftMob), "a summoned ghast's crew joined a rift");
			ctx.killAllEntities();
			ctx.succeed();
		});
		NetherRiftsTests.register("bombardier/warning_leads_impact", ctx -> {
			// The ground warning shows a full second before the blast, where the bomb lands, even from a moving ghast.
			stoneFloor(ctx);
			long[] warnedAt = {-1};
			long[] burntAt = {-1};
			Vec3[] warning = new Vec3[1];
			Vec3[] burn = new Vec3[1];
			ctx.onEachTick(() -> {
				for (var patch : BurningPatches.active(ctx.getLevel())) {
					if (!ctx.getBounds().contains(patch.at())) {
						continue;
					}
					if (patch.warning() && warning[0] == null) {
						warning[0] = patch.at();
					} else if (!patch.warning() && burn[0] == null) {
						burn[0] = patch.at();
						burntAt[0] = ctx.getTick();
					}
				}
			});
			FormingTests.lightFrame(ctx, 2, 3);
			ctx.runAtTickTime(3, () -> {
				// run height: the deck DIVE_HEIGHT above the floor
				var ghast = warGhast(ctx, new BlockPos(12, 1 + (int) Math.ceil(BombingRunGoal.DIVE_HEIGHT + WarGhast.DECK_DROP), 12));
				ghast.setNoAi(true);
				ghast.setDeltaMovement(0.4, 0, 0);
			});
			ctx.runAtTickTime(5, () -> {
				var ghast = ctx.getEntities(RiftEntities.WAR_GHAST).getFirst();
				// the warning goes up with the drop; per-tick checks would see it a tick late (they run before this)
				ghast.dropBomb(ctx.getLevel());
				warnedAt[0] = ctx.getTick();
			});
			ctx.succeedWhen(() -> {
				ctx.assertTrue(warning[0] != null, "no ground warning");
				ctx.assertTrue(burn[0] != null, "the bomb hasn't landed");
				ctx.assertTrue(burntAt[0] - warnedAt[0] >= BurningPatches.WARN_TICKS, "the warning led the blast by only " + (burntAt[0] - warnedAt[0]) + " ticks");
				double off = Math.hypot(burn[0].x - warning[0].x, burn[0].z - warning[0].z);
				ctx.assertTrue(off <= BurningPatches.RADIUS, "the bomb landed " + off + " blocks from its warning");
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
			});
		});
		NetherRiftsTests.register("bombardier/on_foot_kit", ctx -> {
			// On foot the Bombardier is a piglin to clients, with its sword and goggles, neither of which ever drops, and
			// right-handed (the glove is a right arm). A summon (finalizeSpawn) doesn't make it a baby or swap its kit. Of no rift, it still never barters, picks
			// things up or turns zombie, and gold armour doesn't spare a player from it.
			var player = StageTests.player(ctx);
			var spot = ctx.absolutePos(new BlockPos(12, 1, 12));
			player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
			player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
			player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
			var at = ctx.absolutePos(new BlockPos(12, 1, 15));
			var difficulty = ctx.getLevel().getCurrentDifficultyAt(at);
			for (int i = 0; i < 20; i++) {
				var summoned = RiftEntities.GHAST_BOMBARDIER.create(ctx.getLevel(), EntitySpawnReason.COMMAND);
				summoned.finalizeSpawn(ctx.getLevel(), difficulty, EntitySpawnReason.COMMAND, null);
				ctx.assertFalse(summoned.isBaby(), "a summoned Bombardier is a baby");
				ctx.assertTrue(summoned.getMainHandItem().is(RiftItems.GRENADIER_SWORD), "a summoned Bombardier holds " + summoned.getMainHandItem());
				ctx.assertFalse(summoned.isLeftHanded(), "a summoned Bombardier is left-handed");
				ctx.assertTrue(summoned.getItemBySlot(EquipmentSlot.HEAD).is(RiftItems.BOMBARDIER_HELMET), "a summoned Bombardier wears " + summoned.getItemBySlot(EquipmentSlot.HEAD));
			}
			var bombardier = RiftEntities.GHAST_BOMBARDIER.create(ctx.getLevel(), EntitySpawnReason.COMMAND);
			bombardier.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
			bombardier.finalizeSpawn(ctx.getLevel(), difficulty, EntitySpawnReason.COMMAND, null);
			ctx.getLevel().addFreshEntity(bombardier);
			ctx.assertTrue(bombardier.getPolymerEntityType(null) == EntityTypes.PIGLIN, "clients don't see the Bombardier as a piglin");
			for (var slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.MAINHAND)) {
				ctx.assertTrue(bombardier.getDropChances().byEquipment(slot) == 0f, "the Bombardier's " + slot + " can drop");
			}
			ctx.assertFalse(bombardier.canPickUpLoot(), "the Bombardier picks things up");
			ctx.assertFalse(bombardier.isConverting(), "the Bombardier is turning zombie in the overworld");
			ctx.assertFalse(RiftMobs.isRiftMob(bombardier), "a summoned Bombardier is a rift mob");
			bombardier.setBaby(true);
			ctx.assertFalse(bombardier.isBaby(), "the Bombardier became a baby");
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_INGOT));
			player.interactOn(bombardier, InteractionHand.MAIN_HAND, Vec3.ZERO);
			ctx.assertTrue(bombardier.getOffhandItem().isEmpty(), "the Bombardier took the gold");
			ctx.assertTrue(player.getMainHandItem().is(Items.GOLD_INGOT), "the player lost the gold to the Bombardier");
			ctx.runAfterDelay(60, () -> {
				ctx.assertTrue(bombardier.getBrain().getMemory(MemoryModuleType.NEAREST_TARGETABLE_PLAYER_NOT_WEARING_GOLD).isPresent(),
						"the Bombardier respects the gold truce");
				ctx.assertTrue(bombardier.getOffhandItem().isEmpty(), "the Bombardier is holding the gold");
				StageTests.remove(ctx, player);
				ctx.killAllEntities();
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("bombardier/grenadier_sword", ctx -> {
			// The Grenadier's sword is a golden sword on the server: the same components (but its name and model), in
			// every item tag vanilla's is (vanilla's own and the c: convention tags). Clients get a golden sword with the gloved arm's model; through a throw the arm
			// with the bomb, then without, and never the throw's component. An old save's plain golden sword (and
			// left hand) give way to it.
			var ours = new ItemStack(RiftItems.GRENADIER_SWORD);
			var golden = new ItemStack(Items.GOLDEN_SWORD);
			var ops = ctx.getLevel().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
			var types = new java.util.HashSet<net.minecraft.core.component.DataComponentType<?>>(ours.getComponents().keySet());
			types.addAll(golden.getComponents().keySet());
			types.removeAll(List.of(net.minecraft.core.component.DataComponents.ITEM_NAME, net.minecraft.core.component.DataComponents.ITEM_MODEL));
			for (var type : types) {
				ctx.assertTrue(encoded(type, ours, ops).equals(encoded(type, golden, ops)), type + " differs from a golden sword's: "
						+ encoded(type, ours, ops) + " to " + encoded(type, golden, ops));
			}
			Items.GOLDEN_SWORD.builtInRegistryHolder().tags().forEach(tag -> ctx.assertTrue(ours.is(tag), "the sword isn't in " + tag.location()));

			var player = StageTests.player(ctx);
			var context = player.connection.getPacketContext();
			var lookup = ctx.getLevel().registryAccess();
			var shown = new java.util.LinkedHashMap<String, String>();
			for (var at : new GrenadierSword.Throw[] {null, GrenadierSword.Throw.BOMB, GrenadierSword.Throw.THROWN, null}) {
				GrenadierSword.setThrow(ours, at);
				var client = PolymerItemUtils.getPolymerItemStack(ours, context, lookup);
				ctx.assertTrue(client.is(Items.GOLDEN_SWORD), "clients get " + client);
				ctx.assertFalse(client.has(GrenadierSword.THROW), "the throw reaches clients");
				shown.put(String.valueOf(at), String.valueOf(client.get(net.minecraft.core.component.DataComponents.ITEM_MODEL)));
			}
			ctx.assertTrue(shown.equals(java.util.Map.of("null", "nether_rifts:grenadier_sword", "BOMB", "nether_rifts:grenadier_sword_bomb",
					"THROWN", "nether_rifts:grenadier_sword_thrown")), "clients see " + shown);

			var at = Vec3.atBottomCenterOf(ctx.absolutePos(new BlockPos(12, 1, 12)));
			var old = EntityType.loadEntityRecursive(nbt("{id:\"nether_rifts:ghast_bombardier\",LeftHanded:1b,equipment:{mainhand:{id:\"minecraft:golden_sword\",count:1}}}"),
					ctx.getLevel(), new EntitySpawnRequest(EntitySpawnReason.LOAD, false), e -> {
						e.snapTo(at.x, at.y, at.z, 0, 0);
						return e;
					});
			ctx.assertTrue(old instanceof GhastBombardier b && b.getMainHandItem().is(RiftItems.GRENADIER_SWORD) && !b.isLeftHanded(),
					"an old save's Grenadier holds " + (old instanceof Mob m ? m.getMainHandItem() : old));
			StageTests.remove(ctx, player);
			ctx.killAllEntities();
			ctx.succeed();
		});
		NetherRiftsTests.register("bombardier/hand_bombs", ctx -> {
			// On foot, the Bombardier lobs bombs at a player 8 blocks off (slowed to a stop, so it keeps its distance): the
			// ground warning goes up under the player at least the warning time before the blast, even with the player
			// standing in the way, the bomb lands on its warning, belongs to its rift, and no block changes.
			stoneFloor(ctx);
			long[] warnedAt = {-1};
			long[] burntAt = {-1};
			Vec3[] warning = new Vec3[1];
			Vec3[] burn = new Vec3[1];
			UUID[] bombRift = new UUID[1];
			var thrown = new AtomicBoolean();
			// the throw's looks on the sword, in the order they came
			var looks = new java.util.ArrayList<GrenadierSword.Throw>();
			ctx.onEachTick(() -> {
				for (var bombardier : ctx.getEntities(RiftEntities.GHAST_BOMBARDIER)) {
					var look = bombardier.getMainHandItem().get(GrenadierSword.THROW);
					if (look != null && (looks.isEmpty() || looks.getLast() != look)) {
						looks.add(look);
					}
				}
				for (var bomb : ctx.getEntities(RiftEntities.MAGMA_BOMB)) {
					if (bomb.getOwner() instanceof GhastBombardier && !thrown.getAndSet(true)) {
						bombRift[0] = bomb.getAttached(RiftMobs.RIFT);
					}
				}
				for (var patch : BurningPatches.active(ctx.getLevel())) {
					if (!ctx.getBounds().contains(patch.at())) {
						continue;
					}
					if (patch.warning() && warning[0] == null) {
						warning[0] = patch.at();
						warnedAt[0] = ctx.getTick();
					} else if (!patch.warning() && burn[0] == null) {
						burn[0] = patch.at();
						burntAt[0] = ctx.getTick();
					}
				}
			});
			FormingTests.lightFrame(ctx, 2, 3);
			var player = StageTests.player(ctx);
			// A mock player counts as "client not loaded yet", which makes it immune to all damage.
			player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
			Vec3[] stood = new Vec3[1];
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				var spot = ctx.absolutePos(new BlockPos(16, 1, 14));
				player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
				stood[0] = player.position();
				var bombardier = ctx.spawn(RiftEntities.GHAST_BOMBARDIER, new BlockPos(8, 1, 14));
				RiftMobs.enlist(bombardier, rift.id(), Slot.BOMBARDIER);
				bombardier.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 400, 9));
				bombardier.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, player);
			});
			ctx.succeedWhen(() -> {
				ctx.assertTrue(thrown.get(), "no bomb thrown yet");
				// the glove holds the bomb, then lets it go, then the sword is back
				ctx.assertTrue(looks.size() >= 2 && looks.get(0) == GrenadierSword.Throw.BOMB && looks.get(1) == GrenadierSword.Throw.THROWN,
						"the throw looked " + looks);
				ctx.assertTrue(ctx.getEntities(RiftEntities.GHAST_BOMBARDIER).stream().noneMatch(b -> b.getMainHandItem().has(GrenadierSword.THROW)),
						"the sword is still away");
				ctx.assertTrue(warning[0] != null, "no ground warning");
				ctx.assertTrue(burn[0] != null, "the bomb hasn't landed");
				var rift = FormingTests.riftIn(ctx);
				ctx.assertTrue(rift.id().equals(bombRift[0]), "the thrown bomb isn't of the Bombardier's rift");
				ctx.assertTrue(burntAt[0] - warnedAt[0] >= BurningPatches.WARN_TICKS, "the warning led the blast by only " + (burntAt[0] - warnedAt[0]) + " ticks");
				double aimed = Math.hypot(warning[0].x - stood[0].x, warning[0].z - stood[0].z);
				ctx.assertTrue(aimed <= 0.5, "the warning went up " + aimed + " blocks from the player");
				double off = Math.hypot(burn[0].x - warning[0].x, burn[0].z - warning[0].z);
				ctx.assertTrue(off <= BurningPatches.RADIUS, "the bomb landed " + off + " blocks from its warning");
				for (int x = 0; x < 24; x++) {
					for (int z = 0; z < 24; z++) {
						ctx.assertBlockPresent(Blocks.STONE, new BlockPos(x, 0, z));
					}
				}
				StageTests.remove(ctx, player);
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
			});
		});
		NetherRiftsTests.register("bombardier/joins_late_large_waves", ctx -> {
			// A 10x10 frame is Large; wave 3 heavies are plain ghasts, wave 5 heavies are war ghasts.
			var inside = ShapeTests.frame(ctx, 10, 10, Blocks.OBSIDIAN);
			ctx.setBlock(inside, Blocks.FIRE);
			ctx.runAtTickTime(3, () -> {
				var rift = FormingTests.riftIn(ctx);
				rift.tear(ctx.getLevel());
				RiftRaid.startWave(ctx.getLevel(), rift, 3);
				var wave3 = RiftRaid.aliveMobs(ctx.getLevel(), rift);
				ctx.assertTrue(wave3.stream().anyMatch(e -> e.getType() == EntityTypes.GHAST), "no plain ghast in Large wave 3");
				ctx.assertFalse(wave3.stream().anyMatch(e -> e instanceof WarGhast), "a war ghast came in wave 3");
				wave3.forEach(e -> e.discard());
				RiftRaid.startWave(ctx.getLevel(), rift, 5);
				var wave5 = RiftRaid.aliveMobs(ctx.getLevel(), rift);
				ctx.assertTrue(wave5.stream().anyMatch(e -> e instanceof WarGhast w && !w.isVehicle()), "no crewed war ghast in Large wave 5");
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
	}

	/** A component of a stack as its codec writes it ("none" without it), to compare. */
	static <T> String encoded(net.minecraft.core.component.DataComponentType<T> type, ItemStack stack, com.mojang.serialization.DynamicOps<com.google.gson.JsonElement> ops) {
		var value = stack.get(type);
		return value == null ? "none" : type.codecOrThrow().encodeStart(ops, value).getOrThrow().toString();
	}

	/** A war ghast of the room's rift (lit beforehand), its feet at {@code pos}. */
	static WarGhast warGhast(GameTestHelper ctx, BlockPos pos) {
		UUID rift = FormingTests.riftIn(ctx).id();
		var spawned = RiftMobs.spawn(ctx.getLevel(), rift, Slot.BOMBARDIER, ctx.absolutePos(pos), Vec3.ZERO);
		return (WarGhast) spawned.stream().filter(e -> e instanceof WarGhast).findFirst().orElseThrow();
	}

	/** The mobs standing in a war ghast's deck space (the box from its deck's underside to its feet) other than ghasts. */
	static List<Mob> crew(GameTestHelper ctx, net.minecraft.world.phys.AABB deck) {
		return ctx.getLevel().getEntitiesOfClass(Mob.class, deck, mob -> !(mob instanceof WarGhast));
	}

	/** What a bone shows: nothing while it's hidden. */
	static ItemStack shown(de.tomalbrc.bil.core.holder.wrapper.Bone<?> bone) {
		return ((de.tomalbrc.bil.core.element.PerPlayerItemDisplayElement) bone.element()).getItem();
	}

	/** Whether a node is the model's group {@code name} or inside it. */
	static boolean within(Node node, String name) {
		for (; node != null; node = node.parent()) {
			if (node.name().equals(name)) {
				return true;
			}
		}
		return false;
	}

	/** Stone at {@code y} and {@code y + 1} across the room. */
	static void roof(GameTestHelper ctx, int y) {
		for (int x = 0; x < 24; x++) {
			for (int z = 0; z < 24; z++) {
				ctx.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				ctx.setBlock(new BlockPos(x, y + 1, z), Blocks.STONE);
			}
		}
	}

	static net.minecraft.nbt.CompoundTag nbt(String snbt) {
		try {
			return TagParser.parseCompoundFully(snbt);
		} catch (CommandSyntaxException e) {
			throw new IllegalStateException(e);
		}
	}

	/** The test room comes with no floor (the structure's stone row isn't placed; the encasing barrier is the ground), so lay one. */
	static void stoneFloor(GameTestHelper ctx) {
		for (int x = 0; x < 24; x++) {
			for (int z = 0; z < 24; z++) {
				ctx.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}
}
