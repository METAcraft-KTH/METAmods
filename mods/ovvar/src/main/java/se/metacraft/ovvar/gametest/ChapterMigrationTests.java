package se.metacraft.ovvar.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.enchantment.Enchantments;
import se.metacraft.ovvar.Ovvar;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.ModComponents;
import se.metacraft.ovvar.content.ModContent;
import se.metacraft.ovvar.content.Piece;
import se.metacraft.ovvar.content.Placement;
import se.metacraft.ovvar.content.SpotPlacements;
import se.metacraft.ovvar.content.item.OvveItem;
import se.metacraft.ovvar.datagen.BlockbenchManifest;
import se.metacraft.ovvar.store.Wardrobe;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The two PolymITer chapters are retired: Data (PolymITer) became IT, IT (PolymITer) became
 * Silicon-blue IT; Ingenjör was renamed Bygg. An old item, wherever it was saved (a chunk, a player, another ovve's pockets),
 * loads as its successor with every component it had, and a stored design moves to the successor
 * — or, if the successor already has one, its patches go to the stash, none lost or made.
 */
public final class ChapterMigrationTests {
	private static final UUID OWNER = UUID.fromString("0f6c1e2a-3b4d-4e5f-8a9b-0c1d2e3f4a5b");

	@GameTest
	public void retiredOvvarLoadAsTheirSuccessors(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		ItemStack it = loaded(helper, ModContent.ovve(Chapter.IT));
		reloadAs(helper, wrong, it, "data_polymiter_ovve", ModContent.ovve(Chapter.IT));
		reloadAs(helper, wrong, loaded(helper, ModContent.ovve(Chapter.IT_KISEL)), "it_polymiter_ovve", ModContent.ovve(Chapter.IT_KISEL));
		reloadAs(helper, wrong, new ItemStack(ModContent.top(Chapter.IT)), "data_polymiter_ovve_top", ModContent.top(Chapter.IT));
		reloadAs(helper, wrong, new ItemStack(ModContent.feet(Chapter.IT_KISEL)), "it_polymiter_ovve_feet", ModContent.feet(Chapter.IT_KISEL));
		reloadAs(helper, wrong, loaded(helper, ModContent.ovve(Chapter.BYGG)), "ingenjor_ovve", ModContent.ovve(Chapter.BYGG));
		reloadAs(helper, wrong, new ItemStack(ModContent.top(Chapter.BYGG)), "ingenjor_ovve_top", ModContent.top(Chapter.BYGG));
		reloadAs(helper, wrong, new ItemStack(ModContent.feet(Chapter.BYGG)), "ingenjor_ovve_feet", ModContent.feet(Chapter.BYGG));
		if (!wrong.isEmpty()) helper.fail("retired items: " + wrong);
		helper.succeed();
	}

	@GameTest
	public void retiredOvvarLoadFromNbt(GameTestHelper helper) {
		ItemStack original = loaded(helper, ModContent.ovve(Chapter.IT));
		DynamicOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		CompoundTag tag = (CompoundTag) ItemStack.CODEC.encodeStart(ops, original).getOrThrow();
		tag.putString("id", Ovvar.MOD_ID + ":data_polymiter_ovve");
		var parsed = ItemStack.CODEC.parse(ops, tag);
		if (parsed.isError()) {
			helper.fail("the NBT data_polymiter_ovve does not parse: " + parsed.error().orElseThrow().message());
			return;
		}
		ItemStack stack = parsed.getOrThrow();
		helper.assertTrue(stack.getItem() == ModContent.ovve(Chapter.IT), "the NBT data_polymiter_ovve loads as " + stack.getItem());
		helper.assertTrue(ItemStack.isSameItemSameComponents(original, stack), "components from NBT: " + stack.getComponents() + " vs " + original.getComponents());
		helper.succeed();
	}

	@GameTest
	public void nestedRetiredOvveLoadsInsidePockets(GameTestHelper helper) {
		ItemStack inner = new ItemStack(ModContent.ovve(Chapter.IT));
		inner.set(ModComponents.ON_STAND, true);
		inner.set(ModComponents.PATCHES, SpotPlacements.fromKeys(List.of("front_top_left.itk")).getOrThrow());
		ItemStack outer = new ItemStack(ModContent.ovve(Chapter.IT));
		var bundle = outer.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).asMutable();
		if (bundle.tryInsert(inner.copy()) <= 0) {
			helper.fail("an ovve does not go into another ovve's pockets");
			return;
		}
		outer.set(DataComponents.BUNDLE_CONTENTS, bundle.toImmutable());

		DynamicOps<JsonElement> ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
		JsonObject json = ItemStack.CODEC.encodeStart(ops, outer).getOrThrow().getAsJsonObject();
		JsonArray pockets = json.getAsJsonObject("components").getAsJsonArray("minecraft:bundle_contents");
		pockets.get(0).getAsJsonObject().addProperty("id", Ovvar.MOD_ID + ":data_polymiter_ovve");
		var parsed = ItemStack.CODEC.parse(ops, json);
		if (parsed.isError()) {
			helper.fail("an ovve with a data_polymiter_ovve in its pockets does not parse: " + parsed.error().orElseThrow().message());
			return;
		}
		BundleContents contents = parsed.getOrThrow().get(DataComponents.BUNDLE_CONTENTS);
		if (contents == null || contents.isEmpty()) {
			helper.fail("the pockets came back empty");
			return;
		}
		ItemStack got = contents.itemCopies().findFirst().orElseThrow();
		helper.assertTrue(got.getItem() == ModContent.ovve(Chapter.IT), "the pocketed data_polymiter_ovve loads as " + got.getItem());
		helper.assertValueEqual(got.get(ModComponents.ON_STAND), Boolean.TRUE, "the pocketed ovve's on_stand");
		helper.assertValueEqual(got.get(ModComponents.PATCHES), inner.get(ModComponents.PATCHES), "the pocketed ovve's patches");
		helper.succeed();
	}

	@GameTest
	public void retiredDesignMovesToItsSuccessor(GameTestHelper helper) {
		Wardrobe w = Wardrobe.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
				"{\"designs\":{\"data_polymiter\":[\"front_top_left.itk\"],\"it_polymiter\":[\"back_big.it\"],\"ingenjor\":[\"front_top_left.it\"]},\"stash\":{\"spiken\":1},\"version\":7}"))
				.getOrThrow();
		helper.assertValueEqual(keys(w, Chapter.IT), List.of("front_top_left.itk"), "IT's design");
		helper.assertValueEqual(keys(w, Chapter.IT_KISEL), List.of("back_big.it"), "Silicon-blue IT's design");
		helper.assertValueEqual(keys(w, Chapter.BYGG), List.of("front_top_left.it"), "Bygg's design (saved as Ingenjör)");
		helper.assertValueEqual(w.stash(), Map.of("spiken", 1), "the stash");
		helper.assertValueEqual(w.version(), 7L, "the version");
		JsonObject again = Wardrobe.CODEC.encodeStart(JsonOps.INSTANCE, w).getOrThrow().getAsJsonObject();
		helper.assertValueEqual(List.copyOf(again.getAsJsonObject("designs").keySet()), List.of("bygg", "it", "it_kisel"), "the re-encoded design keys");
		helper.succeed();
	}

	@GameTest
	public void retiredDesignCollidesIntoTheStash(GameTestHelper helper) {
		Wardrobe w = Wardrobe.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
				"{\"designs\":{\"it\":[\"front_top_left.itk\"],\"data_polymiter\":[\"front_top_left.spiken\",\"leg_out_top_l.itk\"]},\"stash\":{\"itk\":2}}"))
				.getOrThrow();
		helper.assertValueEqual(keys(w, Chapter.IT), List.of("front_top_left.itk"), "IT's design");
		helper.assertValueEqual(w.stash(), Map.of("itk", 3, "spiken", 1), "the stash");
		int after = w.stashSize();
		for (Chapter chapter : w.designs().keySet()) after += keys(w, chapter).size();
		helper.assertValueEqual(after, 5, "patches after the migration (1 sewn + 2 retired + 2 stashed before)");
		helper.succeed();
	}

	@GameTest
	public void noPolymiterLeftAnywhere(GameTestHelper helper) {
		helper.assertTrue(Chapter.byId("data_polymiter") == null, "data_polymiter is still a chapter");
		helper.assertTrue(Chapter.byId("it_polymiter") == null, "it_polymiter is still a chapter");
		Item aliased = BuiltInRegistries.ITEM.getValue(ModContent.id("data_polymiter_ovve"));
		helper.assertTrue(aliased == ModContent.ovve(Chapter.IT), "ovvar:data_polymiter_ovve resolves to " + aliased);
		List<String> stale = new ArrayList<>();
		for (String retired : Chapter.RETIRED.keySet()) {
			for (Piece piece : Piece.values()) {
				for (String name : List.of("top", "bottom", "bottom_nercabbad")) {
					String path = "/assets/" + Ovvar.MOD_ID + "/textures/entity/equipment/" + piece.layer + "/" + retired + "/" + name + ".png";
					if (Ovvar.class.getResource(path) != null) stale.add(path);
				}
			}
			for (var id : ModContent.retiredItemIds(retired)) {
				String path = "/assets/" + Ovvar.MOD_ID + "/items/" + id.getPath() + ".json";
				if (Ovvar.class.getResource(path) != null) stale.add(path);
			}
		}
		if (!stale.isEmpty()) helper.fail("datagen still writes retired chapters' files: " + stale);

		List<String> got = new ArrayList<>(), want = new ArrayList<>();
		try (InputStream in = BlockbenchManifest.class.getResourceAsStream(BlockbenchManifest.RESOURCE)) {
			if (in == null) {
				helper.fail(BlockbenchManifest.RESOURCE + " missing: run './gradlew :mods:ovvar:runDatagen'");
				return;
			}
			JsonObject m = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (var e : m.getAsJsonArray("chapters")) got.add(e.getAsJsonObject().get("id").getAsString());
		} catch (IOException e) {
			helper.fail("could not read " + BlockbenchManifest.RESOURCE + ": " + e);
			return;
		}
		for (Chapter chapter : Chapter.values()) want.add(chapter.id);
		helper.assertValueEqual(got, want, "the Blockbench manifest's chapters");
		helper.succeed();
	}

	// ----

	/** An ovve with everything an ovve can carry: top up, patches, an owner, full pockets and an enchantment. */
	private static ItemStack loaded(GameTestHelper helper, OvveItem item) {
		ItemStack stack = new ItemStack(item);
		OvveItem.setTopUp(stack, true);
		stack.set(ModComponents.PATCHES, SpotPlacements.fromKeys(List.of("front_top_left.itk", "leg_out_top_l.spiken")).getOrThrow());
		OvveItem.setOwner(stack, OWNER);
		var bundle = stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).asMutable();
		if (bundle.tryInsert(new ItemStack(Items.DIAMOND, 3)) != 3) helper.fail("3 diamonds do not fit an ovve's pockets");
		stack.set(DataComponents.BUNDLE_CONTENTS, bundle.toImmutable());
		stack.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 2);
		return stack;
	}

	/** {@code original} saved, its id rewritten to the retired {@code oldPath}, and read back: it must be {@code want} with the same components. */
	private static void reloadAs(GameTestHelper helper, List<String> wrong, ItemStack original, String oldPath, Item want) {
		DynamicOps<JsonElement> ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
		JsonObject json = ItemStack.CODEC.encodeStart(ops, original).getOrThrow().getAsJsonObject();
		json.addProperty("id", Ovvar.MOD_ID + ":" + oldPath);
		var parsed = ItemStack.CODEC.parse(ops, json);
		if (parsed.isError()) {
			wrong.add(oldPath + " does not parse: " + parsed.error().orElseThrow().message());
			return;
		}
		ItemStack stack = parsed.getOrThrow();
		if (stack.getItem() != want) wrong.add(oldPath + " loads as " + stack.getItem() + ", not " + want);
		else if (!ItemStack.isSameItemSameComponents(original, stack)) wrong.add(oldPath + " lost components: " + stack.getComponents());
	}

	private static List<String> keys(Wardrobe w, Chapter chapter) {
		return w.design(chapter).map(p -> p.asPlacementList().stream().map(Placement::key).toList()).orElse(List.of());
	}
}
