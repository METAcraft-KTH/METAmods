import de.tomalbrc.bil.core.holder.wrapper.ItemBone;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import nu.metacraft.nether_rifts.NetherRifts;
import nu.metacraft.nether_rifts.mob.ArmourPieces;
import nu.metacraft.nether_rifts.mob.HeldItems;
import nu.metacraft.nether_rifts.mob.RiftEntities;
import nu.metacraft.nether_rifts.mob.RiftModels;
import nu.metacraft.nether_rifts.mob.WarDrummer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Set;
import java.util.zip.ZipFile;

/** {@link ArmourPieces} on a live drummer: the worn pieces show in their material, coloured; the rest hide. */
public class ArmourTests {

	private static ItemBone bone(GameTestHelper ctx, WarDrummer drummer, ArmourPieces.Piece piece) {
		var bone = drummer.getHolder().armour().bone(piece);
		ctx.assertTrue(bone != null, "no bone " + piece.bone);
		return bone;
	}

	private static ItemStack shows(GameTestHelper ctx, WarDrummer drummer, ArmourPieces.Piece piece) {
		return bone(ctx, drummer, piece).element().getItem();
	}

	/** Only these pieces show, each in its slot's material (given head first, down to the feet; one for all). */
	private static void assertShown(GameTestHelper ctx, WarDrummer drummer, String what, Set<ArmourPieces.Piece> pieces, String... materials) {
		for (var piece : ArmourPieces.Piece.values()) {
			var item = shows(ctx, drummer, piece);
			if (!pieces.contains(piece)) {
				ctx.assertTrue(item.isEmpty(), what + ": " + piece.bone + " shows " + item);
				continue;
			}
			var model = item.get(DataComponents.ITEM_MODEL);
			// materials head first, or one for all
			var material = materials[materials.length == 1 ? 0 : 3 - piece.slot.getIndex()];
			var expected = NetherRifts.getID("war_drummer/armour/" + material + "/" + piece.bone);
			ctx.assertTrue(expected.equals(model), what + ": " + piece.bone + " shows " + model + ", not " + expected);
		}
	}

	private static Set<ArmourPieces.Piece> piecesOf(EquipmentSlot... slots) {
		var set = java.util.EnumSet.noneOf(ArmourPieces.Piece.class);
		for (var piece : ArmourPieces.Piece.values()) {
			if (Arrays.asList(slots).contains(piece.slot)) {
				set.add(piece);
			}
		}
		return set;
	}

	private static int rgb(ItemStack item) {
		var dyed = item.get(DataComponents.DYED_COLOR);
		return dyed == null ? -1 : dyed.rgb() & 0xFFFFFF;
	}

	static void registerTests() {
		NetherRiftsTests.register("armour/worn_pieces_show", ctx -> {
			var drummer = ctx.spawn(RiftEntities.WAR_DRUMMER, new BlockPos(12, 1, 12));
			var armour = drummer.getHolder().armour();
			var held = drummer.getHolder().heldItems();
			ctx.assertFalse(armour.isEmpty(), "the drummer has no armour bones");
			armour.tick();
			assertShown(ctx, drummer, "bare", Set.of());

			// an iron helmet: the helmet piece in iron, not the helmet as a head item
			drummer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			armour.tick();
			held.tick();
			assertShown(ctx, drummer, "iron helmet", piecesOf(EquipmentSlot.HEAD), "iron");
			ctx.assertTrue(held.display(HeldItems.Spot.HEAD).getItem().isEmpty(), "the helmet shows as a head item too");
			ctx.assertFalse(shows(ctx, drummer, ArmourPieces.Piece.HEAD).has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE), "a plain helmet glints");

			// a gold chestplate as well: the body and both arms in gold; an enchanted helmet glints
			drummer.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
			var glinting = new ItemStack(Items.IRON_HELMET);
			glinting.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			drummer.setItemSlot(EquipmentSlot.HEAD, glinting);
			armour.tick();
			assertShown(ctx, drummer, "helmet and chestplate", piecesOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST), "iron", "gold");
			ctx.assertTrue(Boolean.TRUE.equals(shows(ctx, drummer, ArmourPieces.Piece.HEAD).get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)),
					"an enchanted helmet doesn't glint");

			// every slot, the turtle shell on the head; leggings on the body and both legs, boots on both legs
			drummer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.TURTLE_HELMET));
			drummer.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
			drummer.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
			drummer.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.CHAINMAIL_BOOTS));
			armour.tick();
			assertShown(ctx, drummer, "full set", piecesOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET),
					"turtle_scute", "netherite", "diamond", "chainmail");

			// not armour: an elytra (no armour layers), a pumpkin (a head item), leggings held in the chest slot
			drummer.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
			drummer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
			drummer.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_LEGGINGS));
			armour.tick();
			held.tick();
			assertShown(ctx, drummer, "no armour but leggings", piecesOf(EquipmentSlot.LEGS), "diamond");
			ctx.assertTrue(held.display(HeldItems.Spot.HEAD).getItem().is(Items.CARVED_PUMPKIN), "the pumpkin doesn't show");

			// taken off, it hides; BIL showing every bone again (as it does when the mob's flags change) doesn't last a tick
			drummer.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
			armour.tick();
			assertShown(ctx, drummer, "taken off", Set.of());
			for (var piece : ArmourPieces.Piece.values()) {
				bone(ctx, drummer, piece).setInvisible(false);
			}
			armour.tick();
			assertShown(ctx, drummer, "shown by BIL", Set.of());
			ctx.killAllEntities();
			ctx.succeed();
		});
		NetherRiftsTests.register("armour/dyed_and_hurt", ctx -> {
			// Leather takes its dye (vanilla's brown undyed), the rest white; while hurt every piece goes red with the
			// model, leather's red over its dye, and its overlay red too.
			var drummer = ctx.spawn(RiftEntities.WAR_DRUMMER, new BlockPos(12, 1, 12));
			var holder = drummer.getHolder();
			var armour = holder.armour();
			var dyed = new ItemStack(Items.LEATHER_CHESTPLATE);
			dyed.set(DataComponents.DYED_COLOR, new DyedItemColor(0x3366FF));
			drummer.setItemSlot(EquipmentSlot.CHEST, dyed);
			drummer.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
			drummer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			drummer.hurtTime = 0;
			RiftModels.hurtTint(drummer, holder);
			armour.tick();
			var chest = shows(ctx, drummer, ArmourPieces.Piece.CHEST_LEFT);
			var boot = shows(ctx, drummer, ArmourPieces.Piece.FEET_RIGHT);
			var helmet = shows(ctx, drummer, ArmourPieces.Piece.HEAD);
			ctx.assertTrue(rgb(chest) == 0x3366FF, "the dyed chestplate is " + Integer.toHexString(rgb(chest)));
			ctx.assertTrue(rgb(boot) == (DyedItemColor.LEATHER_COLOR & 0xFFFFFF), "the undyed boots are " + Integer.toHexString(rgb(boot)));
			ctx.assertTrue(rgb(helmet) == 0xFFFFFF, "the iron helmet is " + Integer.toHexString(rgb(helmet)));
			ctx.assertTrue(chest.get(DataComponents.CUSTOM_MODEL_DATA).getColor(0) == -1, "the overlay is tinted unhurt");

			drummer.hurtTime = 10;
			RiftModels.hurtTint(drummer, holder);
			armour.tick();
			int hurt = RiftModels.HURT_COLOR & 0xFFFFFF;
			ctx.assertTrue(rgb(helmet) == hurt, "the hurt iron helmet is " + Integer.toHexString(rgb(helmet)));
			int redBlue = ARGB.multiply(0xFF3366FF, RiftModels.HURT_COLOR) & 0xFFFFFF;
			ctx.assertTrue(rgb(chest) == redBlue, "the hurt dyed chestplate is " + Integer.toHexString(rgb(chest)) + ", not " + Integer.toHexString(redBlue));
			ctx.assertTrue((chest.get(DataComponents.CUSTOM_MODEL_DATA).getColor(0) & 0xFFFFFF) == hurt, "the overlay isn't red");
			var body = (ItemBone) holder.getBone("body");
			ctx.assertTrue(rgb(body.element().getItem()) == hurt, "the body isn't red");

			drummer.hurtTime = 0;
			RiftModels.hurtTint(drummer, holder);
			armour.tick();
			ctx.assertTrue(rgb(chest) == 0x3366FF, "after the hurt the chestplate is " + Integer.toHexString(rgb(chest)));
			ctx.assertTrue(rgb(helmet) == 0xFFFFFF, "after the hurt the helmet is " + Integer.toHexString(rgb(helmet)));
			ctx.killAllEntities();
			ctx.succeed();
		});
		NetherRiftsTests.register("armour/in_the_pack", ctx -> {
			// The resource pack players get has the pieces' models and the atlas sources that reach vanilla's armour
			// textures (Polymer merges every mod's atlas files into one).
			try {
				var zip = Files.createTempFile("nether_rifts_pack", ".zip");
				PolymerResourcePackUtils.getInstance().build(zip);
				try (var pack = new ZipFile(zip.toFile())) {
					for (var path : new String[] {"assets/nether_rifts/items/war_drummer/armour/iron/armor_head.json",
							"assets/nether_rifts/models/item/war_drummer/armour/leather/armor_feet_left.json",
							"assets/nether_rifts/items/war_drummer/armour/turtle_scute/armor_head.json"}) {
						ctx.assertTrue(pack.getEntry(path) != null, "the pack has no " + path);
					}
					var atlas = pack.getEntry("assets/minecraft/atlases/items.json");
					ctx.assertTrue(atlas != null, "the pack has no item atlas sources");
					var json = new String(pack.getInputStream(atlas).readAllBytes(), StandardCharsets.UTF_8);
					for (var source : new String[] {"entity/equipment/humanoid/", "entity/equipment/humanoid_leggings/"}) {
						ctx.assertTrue(json.contains("\"" + source + "\""), "the item atlas doesn't take " + source + ": " + json);
					}
				}
				Files.delete(zip);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			ctx.succeed();
		});
		NetherRiftsTests.register("armour/natural_gold", ctx -> {
			// A natural spawn wears each piece of gold armour at 10 %, as a piglin does; 60 of them have some (all but
			// certain: 0.9^240), all of it gold, and it shows. Spawned for a rift (an EVENT), never.
			var level = ctx.getLevel();
			var pos = ctx.absolutePos(new BlockPos(12, 1, 12));
			int pieces = 0;
			WarDrummer shown = null;
			for (int i = 0; i < 60; i++) {
				var drummer = RiftEntities.WAR_DRUMMER.create(level, EntitySpawnReason.NATURAL);
				drummer.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
				drummer.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
				for (var slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
					var stack = drummer.getItemBySlot(slot);
					if (!stack.isEmpty()) {
						pieces++;
						ctx.assertTrue("gold".equals(ArmourPieces.material(stack, slot)), "a natural drummer wears " + stack + " in " + slot);
						shown = drummer;
					}
				}
				var event = RiftEntities.WAR_DRUMMER.create(level, EntitySpawnReason.EVENT);
				event.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
				for (var slot : EquipmentSlot.values()) {
					ctx.assertTrue(event.getItemBySlot(slot).isEmpty(), "a rift's drummer came with " + event.getItemBySlot(slot));
				}
			}
			ctx.assertTrue(pieces > 0, "60 natural drummers wore no gold");
			ctx.assertTrue(pieces < 60, pieces + " gold pieces on 60 natural drummers: more than 10 %");
			shown.getHolder().armour().tick();
			for (var piece : ArmourPieces.Piece.values()) {
				var item = shows(ctx, shown, piece);
				ctx.assertTrue(shown.getItemBySlot(piece.slot).isEmpty() == item.isEmpty(), piece.bone + " shows " + item);
			}
			ctx.succeed();
		});
	}
}
