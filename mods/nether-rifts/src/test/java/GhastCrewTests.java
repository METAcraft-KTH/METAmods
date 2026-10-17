import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import nu.metacraft.nether_rifts.mob.CrewHitbox;
import nu.metacraft.nether_rifts.mob.HeldItems;
import nu.metacraft.nether_rifts.mob.RiftEntities;
import nu.metacraft.nether_rifts.mob.RiftModels;
import nu.metacraft.nether_rifts.mob.WarDrummer;
import nu.metacraft.nether_rifts.mob.WarGhast;
import nu.metacraft.nether_rifts.mob.WarGhast.Crew;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A Chained Ghast's crew and deck as hitboxes: hit for real, each on its own, and what happens when one dies (its figure
 * and its work go, it doesn't jump); the crossbow piglin's bolts.
 */
public class GhastCrewTests {

	static void registerTests() {
		NetherRiftsTests.register("ghast_crew/hitboxes_follow", ctx -> {
			// Each crew member has a hitbox where its figure stands, its mob's size and health, an interaction to clients;
			// the deck one is under them, the deck floor's size. They go when the ghast goes.
			var ghast = ctx.spawn(RiftEntities.WAR_GHAST, new BlockPos(12, 8, 12));
			ctx.runAtTickTime(5, () -> {
				for (var crew : Crew.values()) {
					var hitbox = ghast.hitbox(crew);
					ctx.assertTrue(hitbox != null && hitbox.isAlive(), "no hitbox for " + crew.figure);
					var spot = ghast.spot(crew);
					ctx.assertTrue(hitbox.position().distanceTo(spot.feet()) < 0.01, crew.figure + "'s hitbox is at " + hitbox.position() + ", not " + spot.feet());
					// roomier than the mob: 1 x 2.3 for a piglin-sized one
					var size = net.minecraft.world.entity.EntityDimensions.scalable((float) CrewHitbox.WIDTH, (float) CrewHitbox.HEIGHT);
					ctx.assertTrue(Math.abs(hitbox.getBbWidth() - size.width()) < 1e-4 && Math.abs(hitbox.getBbHeight() - size.height()) < 1e-4,
							crew.figure + "'s hitbox is " + hitbox.getDimensions(hitbox.getPose()));
					ctx.assertTrue(hitbox.getMaxHealth() == CrewHitbox.maxHealth(crew) && hitbox.getHealth() == hitbox.getMaxHealth(), crew.figure + " has " + hitbox.getHealth() + " health");
					ctx.assertTrue(hitbox.getPolymerEntityType(null) == EntityTypes.INTERACTION, "clients don't see an interaction");
					// clients get the interaction's size, and none of the hitbox's own data (read as the interaction's)
					var data = new java.util.ArrayList<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>>(
							java.util.Objects.requireNonNullElse(hitbox.getEntityData().getNonDefaultValues(), java.util.List.of()));
					hitbox.modifyRawTrackedData(data, null, true);
					ctx.assertTrue(data.stream().anyMatch(v -> v.id() == 8 && Float.valueOf(size.width()).equals(v.value()))
							&& data.stream().anyMatch(v -> v.id() == 9 && Float.valueOf(size.height()).equals(v.value()))
							&& data.stream().filter(v -> v.id() >= 8).count() == 2, crew.figure + "'s hitbox sends " + data);
				}
				ctx.assertTrue(CrewHitbox.maxHealth(Crew.DRUMMER) == 30 && CrewHitbox.maxHealth(Crew.BOMBARDIER) == 20 && CrewHitbox.maxHealth(Crew.SPEAR) == 16,
						"the crew's health isn't their mobs'");
				var deck = ghast.deckHitbox();
				ctx.assertTrue(deck != null && deck.getBbWidth() == (float) WarGhast.DECK_WIDTH, "no deck hitbox");
				ctx.assertTrue(Math.abs(deck.getY() - (ghast.getY() - WarGhast.DECK_DROP)) < 0.25, "the deck hitbox is at " + deck.getY());
				ctx.assertTrue(deck.getBoundingBox().maxY <= ghast.hitbox(Crew.DRUMMER).getY() + 1e-3, "the deck hitbox reaches into the crew's");
				ctx.assertTrue(deck.getBoundingBox().maxY < ghast.getY(), "the deck hitbox reaches into the ghast's");
				ghast.discard();
				ctx.runAfterDelay(2, () -> {
					ctx.assertTrue(ctx.getEntities(RiftEntities.CREW_HITBOX).isEmpty() && ctx.getEntities(RiftEntities.DECK_HITBOX).isEmpty(),
							"the hitboxes outlived the ghast");
					ctx.killAllEntities();
					ctx.succeed();
				});
			});
		});
		NetherRiftsTests.register("ghast_crew/arrow_hurts_crew_not_ghast", ctx -> {
			// An arrow at the crossbow piglin hurts its hitbox, not the ghast; its figure alone flashes red.
			var ghast = ctx.spawn(RiftEntities.WAR_GHAST, new BlockPos(12, 8, 12));
			ghast.setNoAi(true);
			ctx.runAtTickTime(5, () -> {
				var hitbox = ghast.hitbox(Crew.CROSSBOW);
				var at = hitbox.getBoundingBox().getCenter();
				var out = at.subtract(ghast.position()).multiply(1, 0, 1).normalize();
				var arrow = new Arrow(ctx.getLevel(), at.x + out.x * 4, at.y, at.z + out.z * 4, new ItemStack(Items.ARROW), null);
				arrow.setNoGravity(true);
				arrow.shoot(-out.x, 0, -out.z, 2f, 0);
				ctx.getLevel().addFreshEntity(arrow);
				ctx.runAfterDelay(4, () -> {
					ctx.assertTrue(hitbox.getHealth() < hitbox.getMaxHealth(), "the arrow didn't hurt the crossbow piglin");
					ctx.assertTrue(ghast.getHealth() == ghast.getMaxHealth(), "the arrow hurt the ghast");
					for (var crew : Crew.values()) {
						for (var bone : ghast.crewBones(crew)) {
							if (bone.name().startsWith("armor_")) {
								continue;
							}
							var dyed = BombardierTests.shown(bone).get(DataComponents.DYED_COLOR);
							int colour = dyed == null ? -1 : dyed.rgb() & 0xFFFFFF;
							int expected = crew == Crew.CROSSBOW ? RiftModels.HURT_COLOR : 0xFFFFFF;
							if (crew == Crew.CROSSBOW || colour != -1) {
								ctx.assertTrue(colour == expected, bone.name() + " is " + Integer.toHexString(colour) + ", not " + Integer.toHexString(expected));
							}
						}
					}
					ctx.killAllEntities();
					ctx.succeed();
				});
			});
		});
		NetherRiftsTests.register("ghast_crew/deck_deflects_arrows", ctx -> {
			// An arrow shot up at the deck from below glances off it and hurts nothing.
			var ghast = ctx.spawn(RiftEntities.WAR_GHAST, new BlockPos(12, 9, 12));
			ghast.setNoAi(true);
			ctx.runAtTickTime(5, () -> {
				var under = ghast.position().add(0, -WarGhast.DECK_DROP - 2, 0);
				var arrow = new Arrow(ctx.getLevel(), under.x, under.y, under.z, new ItemStack(Items.ARROW), null);
				arrow.shoot(0, 1, 0, 2f, 0);
				ctx.getLevel().addFreshEntity(arrow);
				ctx.runAfterDelay(10, () -> {
					ctx.assertTrue(arrow.isRemoved() || arrow.getY() < ghast.deckHitbox().getY(), "the arrow went through the deck to " + arrow.position());
					ctx.assertTrue(ghast.getHealth() == ghast.getMaxHealth(), "the arrow hurt the ghast");
					for (var crew : Crew.values()) {
						ctx.assertTrue(ghast.hitbox(crew).getHealth() == ghast.hitbox(crew).getMaxHealth(), "the arrow hurt " + crew.figure);
					}
					ctx.killAllEntities();
					ctx.succeed();
				});
			});
		});
		NetherRiftsTests.register("ghast_crew/armour_protects_spear", ctx -> {
			// The deck's cover: the crew take a share of the damage (crew.damage_taken). The ghast's armour, worn by the spear
			// piglin's hitbox, spares it more of it than the crossbow piglin.
			var ghast = ctx.spawn(RiftEntities.WAR_GHAST, new BlockPos(12, 8, 12));
			ghast.setNoAi(true);
			var chest = new ItemStack(Items.NETHERITE_CHESTPLATE);
			chest.enchant(ctx.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
			ghast.setItemSlot(EquipmentSlot.CHEST, chest);
			ghast.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
			ghast.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
			ctx.runAtTickTime(5, () -> {
				var spear = ghast.hitbox(Crew.SPEAR);
				var crossbow = ghast.hitbox(Crew.CROSSBOW);
				ctx.assertTrue(spear.getItemBySlot(EquipmentSlot.CHEST).is(Items.NETHERITE_CHESTPLATE), "the spear piglin's hitbox wears " + spear.getItemBySlot(EquipmentSlot.CHEST));
				ctx.assertTrue(spear.getArmorValue() > 0, "the spear piglin's hitbox has no armour");
				var zombie = ctx.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
				spear.hurtServer(ctx.getLevel(), ctx.getLevel().damageSources().mobAttack(zombie), 6f);
				crossbow.hurtServer(ctx.getLevel(), ctx.getLevel().damageSources().mobAttack(zombie), 6f);
				float spearLost = spear.getMaxHealth() - spear.getHealth();
				float crossbowLost = crossbow.getMaxHealth() - crossbow.getHealth();
				ctx.assertTrue(Math.abs(crossbowLost - 6 * CrewHitbox.DAMAGE_TAKEN) < 1e-3, "the crossbow piglin lost " + crossbowLost + " of 6");
				ctx.assertTrue(spearLost > 0 && spearLost < crossbowLost / 2, "armoured, the spear piglin lost " + spearLost + " to the crossbow piglin's " + crossbowLost);
				ctx.assertTrue(ghast.getHealth() == ghast.getMaxHealth(), "the ghast was hurt");
				ctx.killAllEntities();
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("ghast_crew/grenadier_killed", ctx -> {
			// Killed on the deck, the Grenadier's figure goes (goggles and all: they're modelled, the head slot stays
			// empty); the ghast makes no more bombing runs, though it has a target under it the whole time.
			BombardierTests.stoneFloor(ctx);
			var bombs = new AtomicInteger();
			ctx.onEachTick(() -> bombs.addAndGet(ctx.getEntities(RiftEntities.MAGMA_BOMB).size()));
			FormingTests.lightFrame(ctx, 2, 3);
			var player = StageTests.player(ctx);
			ctx.runAtTickTime(3, () -> {
				var floor = ctx.absolutePos(new BlockPos(16, 1, 16));
				player.teleportTo(floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5);
				var ghast = BombardierTests.warGhast(ctx, new BlockPos(8, 10, 8));
				ghast.setTarget(player);
				ctx.runAfterDelay(2, () -> {
					var hitbox = ghast.hitbox(Crew.BOMBARDIER);
					hitbox.hurtServer(ctx.getLevel(), ctx.getLevel().damageSources().playerAttack(player), 1000f);
					ctx.assertFalse(hitbox.isAlive(), "the Grenadier's hitbox lives");
					ctx.assertFalse(ghast.hasCrew(Crew.BOMBARDIER), "the Grenadier is still on the deck");
					ctx.assertTrue(ghast.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "the ghast's head slot holds " + ghast.getItemBySlot(EquipmentSlot.HEAD));
					for (var bone : ghast.crewBones(Crew.BOMBARDIER)) {
						ctx.assertTrue(BombardierTests.shown(bone).isEmpty(), bone.name() + " still shows");
					}
				});
			});
			ctx.runAtTickTime(300, () -> {
				var ghast = ctx.getEntities(RiftEntities.WAR_GHAST).getFirst();
				ctx.assertTrue(bombs.get() == 0, "a bomb dropped with the Grenadier dead");
				ctx.assertTrue(ghast.hitbox(Crew.BOMBARDIER) == null, "the Grenadier's hitbox came back");
				ctx.assertTrue(ghast.getHolder().heldItems().display(HeldItems.Spot.HEAD).getItem().isEmpty(), "a head item shows on the Grenadier's head");
				StageTests.remove(ctx, player);
				ctx.killAllEntities();
				FormingTests.cleanUp(ctx);
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("ghast_crew/spear_killed_no_fireballs", ctx -> {
			// The spear piglin stabbing the ghast is what makes it shoot: with it, a ghast charges a fireball at a player
			// it sees; with it killed, the ghast never does.
			BombardierTests.stoneFloor(ctx);
			var player = StageTests.player(ctx);
			var spot = ctx.absolutePos(new BlockPos(12, 1, 20));
			player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
			var armed = ctx.spawn(RiftEntities.WAR_GHAST, new BlockPos(6, 9, 6));
			var unarmed = ctx.spawn(RiftEntities.WAR_GHAST, new BlockPos(18, 9, 6));
			var charged = new AtomicBoolean();
			var unarmedCharged = new AtomicBoolean();
			ctx.onEachTick(() -> {
				if (!armed.isNoAi()) {
					armed.setTarget(player);
				}
				unarmed.setTarget(player);
				if (armed.isCharging()) {
					charged.set(true);
					// seen charging: stop it before it fires
					armed.setNoAi(true);
				}
				unarmedCharged.compareAndSet(false, unarmed.isCharging());
			});
			ctx.runAtTickTime(3, () -> unarmed.hitbox(Crew.SPEAR).kill(ctx.getLevel()));
			ctx.runAtTickTime(80, () -> {
				ctx.assertFalse(unarmed.shoots(), "the ghast without its spear piglin would shoot");
				ctx.assertTrue(charged.get(), "the ghast with its spear piglin never charged a fireball");
				ctx.assertFalse(unarmedCharged.get(), "the ghast without its spear piglin charged a fireball");
				ctx.assertTrue(ctx.getEntities(EntityTypes.FIREBALL).stream().noneMatch(f -> f.getOwner() == unarmed), "the ghast without its spear piglin shot");
				ctx.assertTrue(unarmed.getMainHandItem().isEmpty(), "the dead spear piglin's spear still shows");
				StageTests.remove(ctx, player);
				ctx.killAllEntities();
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("ghast_crew/survivors_bail_with_health", ctx -> {
			// The drummer killed on the deck and the crossbow piglin hurt: the ghast's death lets off the other three, the
			// crossbow piglin with the health it had left.
			var ghast = ctx.spawn(RiftEntities.WAR_GHAST, new BlockPos(12, 9, 12));
			ghast.setNoAi(true);
			ctx.runAtTickTime(5, () -> {
				ghast.hitbox(Crew.DRUMMER).kill(ctx.getLevel());
				var crossbow = ghast.hitbox(Crew.CROSSBOW);
				crossbow.hurtServer(ctx.getLevel(), ctx.getLevel().damageSources().generic(), 5f);
				ctx.runAfterDelay(1, () -> {
					var deck = ghast.stackBox().setMaxY(ghast.getY());
					ghast.hurtServer(ctx.getLevel(), ctx.getLevel().damageSources().generic(), 1000f);
					var crew = BombardierTests.crew(ctx, deck);
					ctx.assertTrue(crew.size() == 3, "the ghast's death let off " + crew);
					ctx.assertTrue(crew.stream().noneMatch(m -> m instanceof WarDrummer), "the dead drummer jumped");
					var piglin = crew.stream().filter(m -> m.getMainHandItem().is(Items.CROSSBOW)).findFirst().orElseThrow();
					ctx.assertTrue(Math.abs(piglin.getHealth() - (piglin.getMaxHealth() - 5 * CrewHitbox.DAMAGE_TAKEN)) < 1e-3, "the crossbow piglin jumped with " + piglin.getHealth());
					ctx.assertTrue(crew.stream().filter(m -> !m.getMainHandItem().is(Items.CROSSBOW)).allMatch(m -> m.getHealth() == m.getMaxHealth()),
							"an unhurt crew member jumped hurt");
					ctx.killAllEntities();
					ctx.succeed();
				});
			});
		});
		NetherRiftsTests.register("ghast_crew/crossbow_bolts", ctx -> {
			// The crossbow piglin shoots bolts at a player it can see in range: its hitbox's, from its crossbow.
			// A second ghast, its crossbow piglin killed, shoots none.
			BombardierTests.stoneFloor(ctx);
			var player = StageTests.player(ctx);
			var spot = ctx.absolutePos(new BlockPos(12, 1, 20));
			player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
			var shooting = ctx.spawn(RiftEntities.WAR_GHAST, new BlockPos(6, 9, 6));
			var silent = ctx.spawn(RiftEntities.WAR_GHAST, new BlockPos(18, 9, 6));
			shooting.setNoAi(true);
			silent.setNoAi(true);
			ctx.runAtTickTime(3, () -> silent.hitbox(Crew.CROSSBOW).kill(ctx.getLevel()));
			var fired = new AtomicBoolean();
			ctx.onEachTick(() -> {
				for (var arrow : ctx.getEntities(EntityTypes.ARROW)) {
					ctx.assertFalse(arrow.getOwner() instanceof CrewHitbox h && h.ghast() == silent, "the dead crossbow piglin shot");
					if (arrow.getOwner() == shooting.hitbox(Crew.CROSSBOW)) {
						fired.set(true);
					}
				}
			});
			ctx.runAtTickTime(WarGhast.CROSSBOW_EVERY + 20, () -> {
				ctx.assertTrue(fired.get(), "no bolt within " + WarGhast.CROSSBOW_EVERY + " ticks");
				StageTests.remove(ctx, player);
				ctx.killAllEntities();
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("ghast_crew/crossbow_never", ctx -> {
			// The crossbow_never preset: crossbow_every 0, so no bolts.
			BombardierTests.stoneFloor(ctx);
			var player = StageTests.player(ctx);
			var spot = ctx.absolutePos(new BlockPos(12, 1, 20));
			player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
			var ghast = ctx.spawn(RiftEntities.WAR_GHAST, new BlockPos(12, 9, 6));
			ghast.setNoAi(true);
			ctx.onEachTick(() -> ctx.assertTrue(ctx.getEntities(EntityTypes.ARROW).isEmpty(), "a bolt with crossbow_every 0"));
			ctx.runAtTickTime(WarGhast.CROSSBOW_EVERY * 2, () -> {
				StageTests.remove(ctx, player);
				ctx.killAllEntities();
				ctx.succeed();
			});
		});
	}
}
