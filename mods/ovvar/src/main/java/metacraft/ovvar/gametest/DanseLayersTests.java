package metacraft.ovvar.gametest;

import de.tomalbrc.danse.util.MinecraftSkinParser.BodyPart;
import metacraft.ovvar.compat.danse.DanseHooks;
import metacraft.ovvar.compat.danse.DanseLayers;
import metacraft.ovvar.compat.danse.DanseModels;
import metacraft.ovvar.content.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;
import java.util.UUID;

/** Which layer a gesturing stand-in gets for each part, pass and outfit. */
public final class DanseLayersTests {

	@GameTest
	public void theCompanionTopDrawsItsPatchesOnTheBodysOuterPass(GameTestHelper helper) {
		if (!danse()) { helper.succeed(); return; }
		EntityEquipment eq = new EntityEquipment();
		eq.set(EquipmentSlot.LEGS, ovve(Chapter.DATA, true));
		eq.set(EquipmentSlot.CHEST, top(Chapter.DATA, new Placement(Spot.FRONT_TOP_LEFT, Patches.get("itk"))));
		ItemStack layer = DanseLayers.INSTANCE.layer(eq, BodyPart.BODY, false);
		expectModel(helper, layer, Piece.TOP, BodyPart.BODY);
		List<String> s = strings(layer);
		check(helper, s.get(0).equals("data/top"), "base key " + s.get(0) + ", expected data/top");
		check(helper, s.get(1 + Spot.cells(Piece.TOP).indexOf(Spot.FRONT_TOP_LEFT)).equals("itk"), "itk not in FRONT_TOP_LEFT's slot: " + s);
		helper.succeed();
	}

	@GameTest
	public void aRolledDownOvveDrawsTheRolledBottomOnTheLegs(GameTestHelper helper) {
		if (!danse()) { helper.succeed(); return; }
		EntityEquipment eq = new EntityEquipment();
		eq.set(EquipmentSlot.LEGS, ovve(Chapter.DATA, false, new Placement(Spot.LEG_OUT_TOP_L, Patches.get("itk"))));
		ItemStack layer = DanseLayers.INSTANCE.layer(eq, BodyPart.LEFT_LEG, true);
		expectModel(helper, layer, Piece.BOTTOM, BodyPart.LEFT_LEG);
		List<String> s = strings(layer);
		check(helper, s.get(0).equals("data/bottom_nercabbad"), "base key " + s.get(0));
		check(helper, s.get(1 + Spot.cells(Piece.BOTTOM).indexOf(Spot.LEG_OUT_TOP_L)).equals("itk"), "itk not in its slot: " + s);
		helper.succeed();
	}

	@GameTest
	public void aChapterThatCannotRollKeepsItsPlainBottom(GameTestHelper helper) {
		if (!danse()) { helper.succeed(); return; }
		EntityEquipment eq = new EntityEquipment();
		eq.set(EquipmentSlot.LEGS, ovve(Chapter.MEDIA, false));
		List<String> s = strings(DanseLayers.INSTANCE.layer(eq, BodyPart.RIGHT_LEG, true));
		check(helper, s.get(0).equals("media/bottom"), "base key " + s.get(0) + ", expected media/bottom (MEDIA cannot roll)");
		helper.succeed();
	}

	@GameTest
	public void eachPieceAnswersOnlyItsOwnPassAndParts(GameTestHelper helper) {
		if (!danse()) { helper.succeed(); return; }
		EntityEquipment eq = new EntityEquipment();
		eq.set(EquipmentSlot.LEGS, ovve(Chapter.DATA, true));
		eq.set(EquipmentSlot.CHEST, top(Chapter.DATA));
		check(helper, DanseLayers.INSTANCE.layer(eq, BodyPart.HEAD, true).isEmpty(), "a layer on the head (inner)");
		check(helper, DanseLayers.INSTANCE.layer(eq, BodyPart.HEAD, false).isEmpty(), "a layer on the head (outer)");
		check(helper, DanseLayers.INSTANCE.layer(eq, BodyPart.RIGHT_ARM, true).isEmpty(), "the bottom drawn on an arm");
		check(helper, DanseLayers.INSTANCE.layer(eq, BodyPart.RIGHT_LEG, false).isEmpty(), "something on the boots pass");
		check(helper, !DanseLayers.INSTANCE.layer(eq, BodyPart.BODY, true).isEmpty(), "no bottom on the body's inner pass");
		helper.succeed();
	}

	@GameTest
	public void aChestplateOverAnOvveKeepsItsOwnPixels(GameTestHelper helper) {
		if (!danse()) { helper.succeed(); return; }
		ItemStack chestplate = new ItemStack(Items.IRON_CHESTPLATE);
		chestplate.set(ModComponents.WRAPPED_TOP, UUID.randomUUID());
		EntityEquipment eq = new EntityEquipment();
		eq.set(EquipmentSlot.LEGS, ovve(Chapter.DATA, true, new Placement(Spot.SLEEVE_OUT_TOP_R, Patches.get("itk"))));
		eq.set(EquipmentSlot.CHEST, chestplate);
		check(helper, !DanseLayers.INSTANCE.replacesArmor(chestplate), "the chestplate's own pixels were blanked");
		ItemStack layer = DanseLayers.INSTANCE.layer(eq, BodyPart.RIGHT_ARM, false);
		expectModel(helper, layer, Piece.TOP, BodyPart.RIGHT_ARM);
		check(helper, strings(layer).contains("itk"), "the sleeve patch under the chestplate is missing");
		helper.succeed();
	}

	@GameTest
	public void aPlayerWithoutAnOvveGetsNoLayers(GameTestHelper helper) {
		if (!danse()) { helper.succeed(); return; }
		EntityEquipment eq = new EntityEquipment();
		eq.set(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		eq.set(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
		for (BodyPart part : List.of(BodyPart.HEAD, BodyPart.BODY, BodyPart.RIGHT_ARM, BodyPart.LEFT_LEG)) {
			check(helper, DanseLayers.INSTANCE.layer(eq, part, true).isEmpty() && DanseLayers.INSTANCE.layer(eq, part, false).isEmpty(),
					"a layer on " + part + " for plain iron armour");
		}
		check(helper, !DanseLayers.INSTANCE.replacesArmor(new ItemStack(Items.IRON_LEGGINGS)), "iron leggings blanked");
		check(helper, DanseLayers.INSTANCE.replacesArmor(ovve(Chapter.DATA, true)), "the ovve's server-side pixels not blanked");
		helper.succeed();
	}

	/**
	 * Upstream Danse has the same mod id as our fork but no body-layer API: ovvar must keep its
	 * gesture etiquette and leave the layers off, rather than crash reaching for a class that isn't there.
	 */
	@GameTest
	public void upstreamDanseGetsEtiquetteButNoLayers(GameTestHelper helper) {
		check(helper, DanseHooks.mode(true, true, true) == DanseHooks.Mode.LAYERS, "the fork: layers");
		check(helper, DanseHooks.mode(true, true, false) == DanseHooks.Mode.ETIQUETTE, "upstream Danse: etiquette only");
		check(helper, DanseHooks.mode(true, false, false) == DanseHooks.Mode.OFF, "no Danse: off");
		check(helper, DanseHooks.mode(false, true, true) == DanseHooks.Mode.OFF, "switched off by property: off");
		// and the lookup itself finds our fork's API here, where the fork is on the classpath
		check(helper, !danse() || DanseHooks.layers(), "our fork is loaded but DanseHooks did not find its body-layer API");
		helper.succeed();
	}

	// ---- helpers

	private static ItemStack ovve(Chapter chapter, boolean topUp, Placement... placements) {
		ItemStack ovve = new ItemStack(ModContent.ovve(chapter));
		OvveItem.setTopUp(ovve, topUp);
		if (placements.length > 0) Looks.setSewn(ovve, SpotPlacements.fromList(List.of(placements)).getOrThrow());
		return ovve;
	}

	private static ItemStack top(Chapter chapter, Placement... placements) {
		ItemStack top = new ItemStack(ModContent.top(chapter));
		if (placements.length > 0) top.set(ModComponents.PATCHES, SpotPlacements.fromList(List.of(placements)).getOrThrow());
		return top;
	}

	private static void expectModel(GameTestHelper helper, ItemStack layer, Piece piece, BodyPart part) {
		if (!DanseModels.itemModel(piece, part).equals(layer.get(DataComponents.ITEM_MODEL))) {
			throw helper.assertionException(Component.literal("layer model " + layer.get(DataComponents.ITEM_MODEL) + ", expected " + DanseModels.itemModel(piece, part)));
		}
	}

	private static List<String> strings(ItemStack layer) {
		CustomModelData data = layer.get(DataComponents.CUSTOM_MODEL_DATA);
		if (data == null) throw new AssertionError("no custom_model_data on " + layer);
		return data.strings();
	}

	private static void check(GameTestHelper helper, boolean ok, String message) {
		if (!ok) throw helper.assertionException(Component.literal(message));
	}

	private static boolean danse() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("danse");
	}
}
