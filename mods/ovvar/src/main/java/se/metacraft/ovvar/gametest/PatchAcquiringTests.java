package se.metacraft.ovvar.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.Looks;
import se.metacraft.ovvar.content.ModContent;
import se.metacraft.ovvar.content.PatchLoot;
import se.metacraft.ovvar.content.Patches;
import se.metacraft.ovvar.content.Piece;
import se.metacraft.ovvar.content.Placement;
import se.metacraft.ovvar.content.SpotPlacements;
import se.metacraft.ovvar.content.item.PatchItem;
import se.metacraft.ovvar.pack.Combos;
import se.metacraft.ovvar.store.FileBackend;
import se.metacraft.ovvar.store.Wardrobe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The chapters' patches are crafted from a dye and string; the rest turn up in structure chests. */
public final class PatchAcquiringTests {
	@GameTest
	public void itkIsLimeDyeInString(GameTestHelper helper) {
		helper.assertValueEqual(craft(helper, plus(Items.DYE.pick(DyeColor.LIME))).getItem(), patch("itk"), "lime dye in a cross of string");
		helper.succeed();
	}

	/** IT, Data and Medieteknik take More Dyes' Laserviolet, Cerise and RGB, so their recipes load only beside it. */
	@GameTest
	public void itAndDataNeedMoreDyes(GameTestHelper helper) {
		boolean moreDyes = FabricLoader.getInstance().isModLoaded("moredyes");
		for (String id : List.of("it", "data", "medieteknik")) {
			var key = ResourceKey.create(Registries.RECIPE, ModContent.patchId(Patches.get(id)));
			helper.assertValueEqual(helper.getLevel().getServer().getRecipeManager().byKey(key).isPresent(), moreDyes, id + "'s recipe loaded");
		}
		if (moreDyes) {
			helper.assertValueEqual(craft(helper, plus(dye("laserviolet"))).getItem(), patch("it"), "laserviolet dye in a cross of string");
			helper.assertValueEqual(craft(helper, plus(dye("cerise"))).getItem(), patch("data"), "cerise dye in a cross of string");
			helper.assertValueEqual(craft(helper, plus(dye("rgb"))).getItem(), patch("medieteknik"), "rgb dye in a cross of string");
		}
		helper.succeed();
	}

	@GameTest
	public void inIsItAndABottleOfExperience(GameTestHelper helper) {
		ItemStack[] grid = empty();
		grid[0] = new ItemStack(patch("it"));
		grid[4] = new ItemStack(Items.EXPERIENCE_BOTTLE);
		helper.assertValueEqual(craft(helper, grid).getItem(), patch("in"), "IT and a bottle of experience");
		helper.succeed();
	}

	@GameTest
	public void everyPatchIsLootOrNotLootAndNothingElse(GameTestHelper helper) {
		for (Patches.Patch patch : Patches.all()) {
			helper.assertTrue(PatchLoot.LOOT.contains(patch.id()) != PatchLoot.NON_LOOT.contains(patch.id()), patch.id() + " is not in exactly one of LOOT and NON_LOOT");
		}
		for (String id : PatchLoot.NON_LOOT) helper.assertTrue(Patches.exists(id), "no patch '" + id + "'");
		for (String id : PatchLoot.LOOT) helper.assertTrue(Patches.exists(id), "no patch '" + id + "'");
		helper.succeed();
	}

	/** A dungeon chest turns up the uncraftable patches now and then, and never a crafted or an exclusive one. */
	@GameTest
	public void structureChestsHoldTheOtherPatches(GameTestHelper helper) {
		LootTable table = helper.getLevel().getServer().reloadableRegistries().getLootTable(BuiltInLootTables.SIMPLE_DUNGEON);
		LootParams params = new LootParams.Builder(helper.getLevel())
				.withParameter(LootContextParams.ORIGIN, helper.absoluteVec(new Vec3(1, 1, 1)))
				.create(LootContextParamSets.CHEST);
		Set<String> found = new HashSet<>();
		for (int i = 0; i < 4000; i++) {
			table.getRandomItems(params).forEach(stack -> {
				if (stack.getItem() instanceof PatchItem item) found.add(item.patch.id());
			});
		}
		helper.assertTrue(found.size() > 1, "4000 dungeon chests held only these patches: " + found);
		for (String id : PatchLoot.NON_LOOT) helper.assertFalse(found.contains(id), "a dungeon chest held " + id + ", which is not loot");
		for (String id : List.of("rivals", "tmeit-marshal", "metacrew", "medieteknik")) {
			helper.assertFalse(PatchLoot.looted().stream().anyMatch(patch -> patch.id().equals(id)), id + " is loot");
		}
		helper.succeed();
	}

	@GameTest
	public void onlyStructureChestsGetPatches(GameTestHelper helper) {
		helper.assertTrue(PatchLoot.inStructureChest(BuiltInLootTables.SIMPLE_DUNGEON.identifier()), "a dungeon chest");
		helper.assertTrue(PatchLoot.inStructureChest(Identifier.fromNamespaceAndPath("structory", "outcast/bandit/desert")), "a Structory chest");
		helper.assertFalse(PatchLoot.inStructureChest(BuiltInLootTables.SPAWN_BONUS_CHEST.identifier()), "the bonus chest");
		helper.assertFalse(PatchLoot.inStructureChest(Identifier.withDefaultNamespace("entities/zombie")), "a zombie");
		helper.succeed();
	}

	/**
	 * The dye colour names {@value Looks#INSTANT_DESIGNS} designs, and they are the droppable ones:
	 * {@link PatchLoot#LOOT} takes codes 1.., in catalogue order, and everything else comes after,
	 * so a loot patch shows the moment it is sewn and a new one takes a spare slot by itself.
	 */
	@GameTest
	public void lootFillsTheInstantRangeFirst(GameTestHelper helper) {
		int loot = PatchLoot.LOOT.size();
		List<String> first = Patches.byCode().subList(0, loot).stream().map(Patches.Patch::id).toList();
		helper.assertValueEqual(first, PatchLoot.looted().stream().map(Patches.Patch::id).toList(), "the patches at codes 1.." + loot);
		for (int i = 0; i < Patches.byCode().size(); i++) {
			helper.assertValueEqual(Patches.code(Patches.byCode().get(i)), i + 1, Patches.byCode().get(i).id() + "'s code");
		}
		for (Patches.Patch patch : Patches.all()) {
			boolean isLoot = PatchLoot.LOOT.contains(patch.id());
			if (!isLoot) helper.assertTrue(Patches.code(patch) > loot, patch.id() + " is not loot but has code " + Patches.code(patch));
			helper.assertValueEqual(Looks.instant(patch), isLoot, patch.id() + " instant");
		}
		for (String id : List.of("itk", "it", "data", "rivals", "kommn", "tmeit-marshal", "metacrew")) {
			helper.assertFalse(Looks.instant(Patches.get(id)), id + " is instant");
		}
		// The rule on a catalogue of its own: the line's place in the list is not what decides.
		Patches.Patch a = new Patches.Patch("a", "A"), b = new Patches.Patch("b", "B"), c = new Patches.Patch("c", "C"),
				d = new Patches.Patch("d", "D"), e = new Patches.Patch("e", "E");
		List<Patches.Patch> order = Patches.designOrder(List.of(a, b, c, d), Set.of("b", "d"));
		helper.assertValueEqual(order, List.of(b, d, a, c), "a given, b loot, c crafted, d loot");
		List<Patches.Patch> grown = Patches.designOrder(List.of(a, b, c, d, e), Set.of("b", "d", "e"));
		helper.assertValueEqual(grown.indexOf(e) + 1, 3, "a loot patch appended last takes the next instant code");
		helper.assertValueEqual(grown, List.of(b, d, e, a, c), "after a new loot patch");
		helper.succeed();
	}

	@GameTest
	public void lootOutgrowingTheRangeFailsLoudly(GameTestHelper helper) {
		PatchLoot.checkInstantRoom(Looks.INSTANT_DESIGNS);
		try {
			PatchLoot.checkInstantRoom(Looks.INSTANT_DESIGNS + 1);
			helper.fail("LOOT one past the instant range went through");
		} catch (IllegalStateException expected) {
			helper.assertTrue(expected.getMessage().contains("room for 23 instant designs"), "the message: " + expected.getMessage());
		}
		helper.assertTrue(PatchLoot.LOOT.size() <= Looks.INSTANT_DESIGNS, PatchLoot.LOOT.size() + " loot patches");
		helper.succeed();
	}

	/**
	 * An ovve as it was saved before the codes were re-derived: the patches are stored by id, so it
	 * reads back the same, and what is no longer instant goes through the pack like any overflow.
	 */
	@GameTest
	public void anOvveSewnBeforeTheRecodingLooksTheSame(GameTestHelper helper) {
		String saved = "{\"id\":\"ovvar:data_ovve\",\"count\":1,\"components\":{\"ovvar:top_up\":true,"
				+ "\"ovvar:patches\":[\"seat.rivals\",\"back_top_right.data\",\"front_top_left.itk\",\"sleeve_front_top_l.starkt\"]}}";
		ItemStack stack = ItemStack.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),
				JsonParser.parseString(saved)).getOrThrow();
		List<String> sewn = SpotPlacements.asPlacementList(Looks.sewn(stack)).stream().map(Placement::key).toList();
		helper.assertValueEqual(Set.copyOf(sewn), Set.of("seat.rivals", "back_top_right.data", "front_top_left.itk", "sleeve_front_top_l.starkt"), "the sewn placements");
		helper.assertValueEqual(sewn.size(), 4, "placements");

		Looks.Look top = Looks.look(stack, Piece.TOP, null, false);
		helper.assertValueEqual(top.dye(), Looks.instantDye(Piece.TOP, List.of(Placement.parse("sleeve_front_top_l.starkt"))),
				"the top's dye: Starkt alone, the one instant patch on it");
		helper.assertFalse(top.complete(), "the top is complete without the pack");

		try {
			for (Piece piece : Piece.values()) {
				Combos.markBuiltForTest(Chapter.DATA, piece, Placement.combo(SpotPlacements.asPlacementList(Looks.sewn(stack, piece))));
			}
			for (Piece piece : Piece.values()) {
				helper.assertTrue(Looks.look(stack, piece, null).complete(), piece + " is not complete once the pack holds its combo");
			}
		} finally {
			for (Piece piece : Piece.values()) {
				Combos.unmarkBuiltForTest(Chapter.DATA, piece, Placement.combo(SpotPlacements.asPlacementList(Looks.sewn(stack, piece))));
			}
		}
		helper.succeed();
	}

	/** Wardrobes and combos.json name patches by id, so the re-derived codes leave them alone. */
	@GameTest
	public void storedDesignsAndCombosStillRead(GameTestHelper helper) throws IOException {
		Path dir = Files.createTempDirectory("ovvar-wardrobes-recoding");
		UUID owner = UUID.randomUUID();
		Files.writeString(dir.resolve(owner + ".json"), """
				{
					"designs": {
						"data": [
							"seat.rivals",
							"leg_front_top_l.it"
						]
					},
					"stash": {
						"starkt": 2
					},
					"version": 1
				}""", StandardCharsets.UTF_8);
		Wardrobe read = new FileBackend(dir).load(owner).orElseThrow();
		helper.assertValueEqual(read.design(Chapter.DATA).map(d -> Set.copyOf(d.asPlacementList())).orElse(Set.of()),
				Set.of(Placement.parse("seat.rivals"), Placement.parse("leg_front_top_l.it")), "the stored design");
		helper.assertValueEqual(read.count(Patches.get("starkt")), 2, "Starkt in the stash");
		helper.assertValueEqual(read.stashSize(), 2, "the stash");

		Combos.KeyedCombo combo = Combos.parseKeys(List.of("bottom:leg_front_top_l.it-seat.rivals")).getFirst();
		helper.assertValueEqual(combo.piece(), Piece.BOTTOM, "the combo's half");
		helper.assertValueEqual(Set.copyOf(combo.combo().placements()), Set.of(Placement.parse("seat.rivals"), Placement.parse("leg_front_top_l.it")), "the combo");
		helper.succeed();
	}

	private static Item patch(String id) {
		return ModContent.patchItem(Patches.get(id));
	}

	private static Item dye(String colour) {
		return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("moredyes", colour + "_dye"));
	}

	/** {@code dye} in the middle, string on its four sides. */
	private static ItemStack[] plus(Item dye) {
		ItemStack[] grid = empty();
		for (int i : new int[] {1, 3, 5, 7}) grid[i] = new ItemStack(Items.STRING);
		grid[4] = new ItemStack(dye);
		return grid;
	}

	private static ItemStack[] empty() {
		ItemStack[] grid = new ItemStack[9];
		Arrays.fill(grid, ItemStack.EMPTY);
		return grid;
	}

	private static ItemStack craft(GameTestHelper helper, ItemStack[] grid) {
		CraftingInput input = CraftingInput.of(3, 3, List.of(grid));
		return helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
				.map(holder -> holder.value().assemble(input)).orElse(ItemStack.EMPTY);
	}
}
