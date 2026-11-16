package se.metacraft.ovvar.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import se.metacraft.ovvar.Ovvar;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.ModContent;
import se.metacraft.ovvar.content.Piece;
import se.metacraft.ovvar.content.Spot;
import se.metacraft.ovvar.datagen.GeneratedAssets;
import se.metacraft.ovvar.datagen.Tex;
import se.metacraft.ovvar.pack.WardrobeArt;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Every chapter is a registered garment with its layers drawn and its name in both languages; the
 * 14 chapters only the wandering trader sells are flagged as such; the trims of CL, Doktorand and
 * TBas make it through datagen onto the worn layer.
 */
public final class ChapterTests {
	private static final int D = Spot.DETAIL;

	@GameTest
	public void everyChapterIsRegisteredWithItsAssets(GameTestHelper helper) {
		for (Chapter c : Chapter.values()) {
			Identifier want = Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, c.itemName());
			if (!ModContent.ovveId(c).equals(want)) helper.fail(c + "'s item id is " + ModContent.ovveId(c) + ", not " + want);
			if (ModContent.ovve(c) == null || !BuiltInRegistries.ITEM.getKey(ModContent.ovve(c)).equals(want)) {
				helper.fail(c + " is not registered as " + want);
			}
			boolean legs = c.slot == EquipmentSlot.LEGS;
			if ((ModContent.top(c) != null) != legs) helper.fail(c + "'s companion top is " + ModContent.top(c));
			if ((ModContent.feet(c) != null) != legs) helper.fail(c + "'s cuffs are " + ModContent.feet(c));
			if (!exists(Piece.TOP.layer + "/" + c.id + "/top")) helper.fail(c + " has no generated top layer");
			if (legs) {
				if (!exists(Piece.BOTTOM.layer + "/" + c.id + "/bottom")) helper.fail(c + " has no generated bottom layer");
				if (exists(Piece.BOTTOM.layer + "/" + c.id + "/bottom_nercabbad") != c.rollable) {
					helper.fail(c + "'s rolled-down layer does not match rollable=" + c.rollable);
				}
			}
		}
		if (!ModContent.ovveId(Chapter.B_FRACK).getPath().equals("b_frack")) helper.fail("the B-frack's item is " + ModContent.ovveId(Chapter.B_FRACK));
		helper.succeed();
	}

	@GameTest
	public void everyGarmentIsNamedInBothLanguages(GameTestHelper helper) {
		JsonObject en = lang("en_us"), sv = lang("sv_se");
		List<String> wrong = new ArrayList<>();
		for (Chapter c : Chapter.values()) {
			String key = "item." + Ovvar.MOD_ID + "." + c.itemName();
			check(wrong, en, key, c.fullName);
			check(wrong, sv, key, c.svName);
			if (c.slot != EquipmentSlot.LEGS) continue;
			check(wrong, en, "item." + Ovvar.MOD_ID + "." + ModContent.topId(c).getPath(), c.fullName + " (top)");
			check(wrong, sv, "item." + Ovvar.MOD_ID + "." + ModContent.topId(c).getPath(), c.svName + " (överdel)");
			check(wrong, en, "item." + Ovvar.MOD_ID + "." + ModContent.feetId(c).getPath(), c.fullName + " (cuffs)");
			check(wrong, sv, "item." + Ovvar.MOD_ID + "." + ModContent.feetId(c).getPath(), c.svName + " (muddar)");
		}
		check(wrong, en, "item.ovvar.arkitektur_ovve", "Arkitektur ovve");
		check(wrong, sv, "item.ovvar.arkitektur_ovve", "Arkitektur-ovve");
		check(wrong, en, "item.ovvar.b_frack", "B-frack");
		check(wrong, sv, "item.ovvar.b_frack", "B-frack");
		check(wrong, sv, "item.ovvar.it_kisel_ovve", "Kiselblå IT-ovve");
		if (!wrong.isEmpty()) helper.fail("garment names: " + wrong);
		helper.succeed();
	}

	@GameTest
	public void theNewChaptersAreTheWanderersStock(GameTestHelper helper) {
		List<String> ids = new ArrayList<>();
		for (Chapter c : Chapter.wandererStock()) ids.add(c.id);
		List<String> want = List.of("arkitektur", "bygg", "cl", "doktorand", "elektro", "energi", "fysik", "indek",
				"kemi", "mit", "open", "samhall", "tbas", "b");
		if (!ids.equals(want)) helper.fail("the wanderer's stock is " + ids + ", not " + want);
		for (Chapter c : List.of(Chapter.DATA, Chapter.IT, Chapter.IT_KISEL, Chapter.MEDIA)) {
			if (c.wandererOnly) helper.fail(c + " is wanderer-only");
		}
		helper.succeed();
	}

	@GameTest
	public void trimsSurviveDatagen(GameTestHelper helper) {
		int clStripe = stripe("cl");
		if (hueDistance(clStripe, 0xC8102E) > 10 || saturation(clStripe) <= 0.5f) {
			helper.fail("CL's leg stripe is " + Integer.toHexString(clStripe) + ", not its red trim");
		}
		int tbasStripe = stripe("tbas");
		if (lum(tbasStripe) >= 60) helper.fail("TBas's leg stripe is " + Integer.toHexString(tbasStripe) + ", not its dark trim");
		int placket = generated(Piece.TOP.layer + "/doktorand/top").get(23 * D + 1, 24 * D + 1);
		if (hueDistance(placket, 0xC9A227) > 10) helper.fail("Doktorand's placket is " + Integer.toHexString(placket) + ", not its gold trim");
		helper.succeed();
	}

	@GameTest
	public void wardrobeColourIsTheChapterColour(GameTestHelper helper) {
		for (Chapter c : Chapter.values()) {
			if (WardrobeArt.colour(c) != c.colour) helper.fail(c + "'s wardrobe colour is " + Integer.toHexString(WardrobeArt.colour(c)));
		}
		int[] old = {WardrobeArt.colour(Chapter.DATA), WardrobeArt.colour(Chapter.IT), WardrobeArt.colour(Chapter.IT_KISEL), WardrobeArt.colour(Chapter.MEDIA)};
		if (old[0] != 0xBD3754 || old[1] != 0x8A57BD || old[2] != 0x769BB0 || old[3] != 0x1B1B1B) {
			helper.fail("the old four chapters' wardrobe colours changed");
		}
		helper.succeed();
	}

	/** The right leg's outer stripe on a chapter's bottom layer, with the right limb put back where it is drawn. */
	private static int stripe(String id) {
		Tex bottom = GeneratedAssets.rightLimbRestored(generated(Piece.BOTTOM.layer + "/" + id + "/bottom"), Piece.BOTTOM);
		return bottom.get(1 * D + 1, 22 * D + 1);
	}

	private static void check(List<String> wrong, JsonObject lang, String key, String want) {
		String got = lang.has(key) ? lang.get(key).getAsString() : null;
		if (!want.equals(got)) wrong.add(key + " = " + got + ", not " + want);
	}

	private static JsonObject lang(String code) {
		return JsonParser.parseString(resource("/assets/" + Ovvar.MOD_ID + "/lang/" + code + ".json")).getAsJsonObject();
	}

	private static boolean exists(String layerAndName) {
		return ChapterTests.class.getResource(path(layerAndName)) != null;
	}

	private static String path(String layerAndName) {
		return "/assets/" + Ovvar.MOD_ID + "/textures/entity/equipment/" + layerAndName + ".png";
	}

	private static Tex generated(String layerAndName) {
		try (InputStream in = ChapterTests.class.getResourceAsStream(path(layerAndName))) {
			if (in == null) throw new IOException("missing " + path(layerAndName) + " — run ./gradlew runDatagen");
			return Tex.read(in);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String resource(String path) {
		try (InputStream in = ChapterTests.class.getResourceAsStream(path)) {
			if (in == null) throw new IOException("missing " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static float[] hsb(int p) {
		return java.awt.Color.RGBtoHSB(Tex.r(p), Tex.g(p), Tex.b(p), null);
	}

	private static float saturation(int p) {
		return hsb(p)[1];
	}

	private static double hueDistance(int p, int rgb) {
		double d = Math.abs(hsb(p)[0] - hsb(rgb)[0]) * 360;
		return Math.min(d, 360 - d);
	}

	private static double lum(int p) {
		return 0.2126 * Tex.r(p) + 0.7152 * Tex.g(p) + 0.0722 * Tex.b(p);
	}
}
