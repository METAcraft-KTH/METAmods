package se.metacraft.skins.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import se.metacraft.skins.ReloadReport;
import se.metacraft.skins.Skins;
import se.metacraft.skins.SkinsPack;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static se.metacraft.skins.gametest.SkinFixtures.assertThat;
import static se.metacraft.skins.gametest.SkinFixtures.text;
import static se.metacraft.skins.gametest.SkinFixtures.unzip;

/** The skins resource pack, built from skin folders through {@link Skins#reload()}. */
public final class SkinsPackTests {

	/** A Look-only folder becomes an item definition, its model and its texture under namespace skins. */
	@GameTest
	public void aLookOnlySkinIsInThePack(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().lookOnly("candy_cane_test", "sword");
		ReloadReport report = fx.reload();
		assertThat(report.rejections().isEmpty(), "rejected: " + report.rejections());
		assertThat(report.loaded().equals(List.of("candy_cane_test")), "loaded: " + report.loaded());

		Map<String, byte[]> files = unzip(fx.pack().zip());
		JsonObject item = json(files, "assets/skins/items/candy_cane_test.json");
		assertThat(item.getAsJsonObject("model").get("type").getAsString().equals("minecraft:model"), "not a plain model: " + item);
		assertThat(item.getAsJsonObject("model").get("model").getAsString().equals("skins:item/candy_cane_test/candy_cane_test"), "points elsewhere: " + item);
		json(files, "assets/skins/models/item/candy_cane_test/candy_cane_test.json");
		assertThat(Arrays.equals(files.get("assets/skins/textures/item/candy_cane_test/candy_cane_test.png"), SkinFixtures.pngBytes("candy_cane_test", "candy_cane_test")),
				"the texture was not copied as it is: " + files.keySet());
		var meta = PackMetadataSection.CLIENT_TYPE.codec().parse(JsonOps.INSTANCE,
				json(files, "pack.mcmeta").get(PackMetadataSection.CLIENT_TYPE.name()));
		assertThat(meta.isSuccess(), "pack.mcmeta does not read as a client pack: " + text(files, "pack.mcmeta"));
		helper.succeed();
	}

	/** Bare Blockbench names and foreign namespaces alike end up as skins:item/<id>/<file name>. */
	@GameTest
	public void textureReferencesAreRewrittenToTheSkinsFolder(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create()
				.file("crown_test", "skin.json5", "{ name: \"Crown\", kind: \"helmet\", look: { model: \"crown_test.json\" } }")
				.file("crown_test", "crown_test.json", """
						{ "textures": { "0": "block/crown_main", "1": "minecraft:block/crown_side", "2": "crown_bottom.png",
						"particle": "block/crown_main" },
						"elements": [], "display": %s }""".formatted(SkinFixtures.ALL_DISPLAY))
				.png("crown_test", "crown_main").png("crown_test", "crown_side").png("crown_test", "crown_bottom");
		ReloadReport report = fx.reload();
		assertThat(report.rejections().isEmpty(), "rejected: " + report.rejections());

		Map<String, byte[]> files = unzip(fx.pack().zip());
		JsonObject textures = json(files, "assets/skins/models/item/crown_test/crown_test.json").getAsJsonObject("textures");
		assertThat(textures.get("0").getAsString().equals("skins:item/crown_test/crown_main"), "0 is " + textures.get("0"));
		assertThat(textures.get("1").getAsString().equals("skins:item/crown_test/crown_side"), "1 is " + textures.get("1"));
		assertThat(textures.get("2").getAsString().equals("skins:item/crown_test/crown_bottom"), "2 is " + textures.get("2"));
		assertThat(textures.get("particle").getAsString().equals("skins:item/crown_test/crown_main"), "particle is " + textures.get("particle"));
		for (String name : List.of("crown_main", "crown_side", "crown_bottom")) {
			assertThat(files.containsKey("assets/skins/textures/item/crown_test/" + name + ".png"), name + ".png is not in the pack");
		}
		helper.succeed();
	}

	/** A model without a particle texture gets its first texture as one, so breaking particles aren't the missing texture. */
	@GameTest
	public void aMissingParticleTextureIsFilledIn(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create()
				.file("emerald_pick_test", "skin.json5", "{ name: \"Emerald Pick\", kind: \"pickaxe\", look: { model: \"emerald_pick_test.json\" } }")
				.file("emerald_pick_test", "emerald_pick_test.json", """
						{ "textures": { "4": "emerald_pick_test", "5": "emerald_handle" }, "elements": [], "display": %s }""".formatted(SkinFixtures.ALL_DISPLAY))
				.png("emerald_pick_test", "emerald_pick_test").png("emerald_pick_test", "emerald_handle");
		ReloadReport report = fx.reload();
		assertThat(report.rejections().isEmpty(), "rejected: " + report.rejections());

		JsonObject textures = json(unzip(fx.pack().zip()), "assets/skins/models/item/emerald_pick_test/emerald_pick_test.json").getAsJsonObject("textures");
		assertThat(textures.has("particle"), "no particle texture: " + textures);
		assertThat(textures.get("particle").getAsString().equals("skins:item/emerald_pick_test/emerald_pick_test"), "particle is " + textures.get("particle"));
		helper.succeed();
	}

	/** An animated texture keeps its animation: the .png.mcmeta next to it is copied along. */
	@GameTest
	public void animatedTextureMetadataIsCopied(GameTestHelper helper) {
		String mcmeta = "{ \"animation\": { \"frametime\": 4 } }";
		SkinFixtures fx = SkinFixtures.create().lookOnly("glow_sword", "sword").file("glow_sword", "glow_sword.png.mcmeta", mcmeta);
		fx.reload();
		Map<String, byte[]> files = unzip(fx.pack().zip());
		assertThat(text(files, "assets/skins/textures/item/glow_sword/glow_sword.png.mcmeta").equals(mcmeta), "the .mcmeta changed on the way");
		helper.succeed();
	}

	/**
	 * A spear's item definition shows the icon model in the inventory, on the ground, in frames and
	 * on shelves and the in-hand model everywhere else, and swaps at the vanilla spear's speed.
	 */
	@GameTest
	public void aSpearSwitchesModelOnDisplayContext(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create()
				.file("lance", "skin.json5", "{ name: \"Lance\", kind: \"spear\", look: { icon: \"lance_icon.json\", in_hand: \"lance_hand.json\" } }")
				.file("lance", "lance_icon.json", SkinFixtures.model("lance"))
				.file("lance", "lance_hand.json", SkinFixtures.model("lance_long"))
				.png("lance", "lance").png("lance", "lance_long");
		ReloadReport report = fx.reload();
		assertThat(report.rejections().isEmpty(), "rejected: " + report.rejections());

		Map<String, byte[]> files = unzip(fx.pack().zip());
		JsonObject item = json(files, "assets/skins/items/lance.json");
		assertThat(item.has("swap_animation_scale") && item.get("swap_animation_scale").getAsDouble() == 1.95,
				"swap_animation_scale is not 1.95: " + item);
		JsonObject model = item.getAsJsonObject("model");
		assertThat(model.get("type").getAsString().equals("minecraft:select"), "not a select: " + model);
		assertThat(model.get("property").getAsString().equals("minecraft:display_context"), "selects on " + model.get("property"));
		var cases = model.getAsJsonArray("cases");
		assertThat(cases.size() == 1, "expected one case: " + cases);
		JsonObject icon = cases.get(0).getAsJsonObject();
		List<String> when = icon.getAsJsonArray("when").asList().stream().map(e -> e.getAsString()).sorted().toList();
		assertThat(when.equals(List.of("fixed", "ground", "gui", "on_shelf")), "the icon shows in " + when);
		assertThat(icon.getAsJsonObject("model").get("model").getAsString().equals("skins:item/lance/lance_icon"), "icon case: " + icon);
		assertThat(model.getAsJsonObject("fallback").get("model").getAsString().equals("skins:item/lance/lance_hand"), "fallback: " + model.get("fallback"));
		json(files, "assets/skins/models/item/lance/lance_icon.json");
		json(files, "assets/skins/models/item/lance/lance_hand.json");
		helper.succeed();
	}

	/** Rebuilding the same skins gives the same bytes, so the same SHA-1, and nothing is pushed. */
	@GameTest
	public void theSameSkinsAlwaysGiveTheSameSha1(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().lookOnly("candy_cane_test", "sword").lookOnly("poop_hat_test", "helmet");
		ReloadReport first = fx.reload();
		assertThat(first.changed(), "the first build did not count as a change");
		ReloadReport second = fx.reload();
		assertThat(second.sha1().equals(first.sha1()), "rebuilt the same skins into another SHA-1");
		assertThat(!second.changed(), "an unchanged rebuild says it changed");

		// A server restart (a fresh Skins on the same folders) gives the same pack too.
		ReloadReport restarted = Skins.at(fx.root).reload();
		assertThat(restarted.sha1().equals(first.sha1()), "a restart built another SHA-1");

		// The name is not in the pack: renaming a skin needs no download.
		fx.file("poop_hat_test", "skin.json5", "{ name: \"Renamed hat\", kind: \"helmet\", look: { model: \"poop_hat_test.json\" } }");
		ReloadReport renamed = fx.reload();
		assertThat(renamed.sha1().equals(first.sha1()) && !renamed.changed(), "renaming a skin changed the pack");
		helper.succeed();
	}

	/** Materials aren't part of a Look: limiting a skin, or changing its list, needs no download. */
	@GameTest
	public void aMaterialsOnlyEditLeavesThePackAlone(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().lookOnly("poop_hat_test", "helmet");
		ReloadReport unlimited = fx.reload();
		fx.file("poop_hat_test", "skin.json5", "{ name: \"poop_hat_test skin\", kind: \"helmet\", look: { model: \"poop_hat_test.json\" }, materials: [\"leather\"] }");
		ReloadReport limited = fx.reload();
		assertThat(limited.loaded().equals(List.of("poop_hat_test")), "the limited hat didn't load: " + limited);
		assertThat(limited.sha1().equals(unlimited.sha1()) && !limited.changed(), "limiting a skin to Materials changed the pack");
		fx.file("poop_hat_test", "skin.json5", "{ name: \"poop_hat_test skin\", kind: \"helmet\", look: { model: \"poop_hat_test.json\" }, materials: [\"turtle\", \"iron\"] }");
		ReloadReport changed = fx.reload();
		assertThat(changed.sha1().equals(unlimited.sha1()) && !changed.changed(), "changing a skin's Materials changed the pack");
		helper.succeed();
	}

	/** Adding a skin, changing a texture or breaking a skin changes the SHA-1, and the report says so. */
	@GameTest
	public void changedContentChangesTheSha1(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().lookOnly("candy_cane_test", "sword");
		String one = fx.reload().sha1();

		ReloadReport added = fx.lookOnly("poop_hat_test", "helmet").reload();
		assertThat(added.changed() && !added.sha1().equals(one), "adding a skin did not change the pack");

		ReloadReport retextured = fx.file("poop_hat_test", "poop_hat_test.png", new byte[] {1, 2, 3}).reload();
		assertThat(retextured.changed() && !retextured.sha1().equals(added.sha1()), "a new texture did not change the pack");

		ReloadReport broken = fx.file("poop_hat_test", "skin.json5", "{ name: \"Hat\", kind: \"hat\", look: { model: \"poop_hat_test.json\" } }").reload();
		assertThat(broken.changed() && broken.sha1().equals(one), "a skin gone Dormant should leave the pack it had before it was added");
		helper.succeed();
	}

	/**
	 * A Dyeable Hat adds to its natural model and textures: a grey copy of each texture a Tint face
	 * uses (its .mcmeta beside it) under dyed/, a dyed model that is a full copy in which only the
	 * Tint faces point at the grey copies (with tintindex 0), and an item definition that shows the
	 * natural model undyed and the dyed one, taking the dye, once the helmet has a colour.
	 */
	@GameTest
	public void aDyeableHatGetsGreyCopiesADyedModelAndATwoWayDefinition(GameTestHelper helper) {
		String mcmeta = "{ \"animation\": { \"frametime\": 4 } }";
		SkinFixtures fx = SkinFixtures.create().dyeableHat("cowboy_hat_test").file("cowboy_hat_test", "felt.png.mcmeta", mcmeta);
		ReloadReport report = fx.reload();
		assertThat(report.loaded().equals(List.of("cowboy_hat_test")) && report.warnings().isEmpty(), "the hat: " + report);
		Map<String, byte[]> files = unzip(fx.pack().zip());
		String textures = "assets/skins/textures/item/cowboy_hat_test/";
		assertThat(files.containsKey(textures + "dyed/felt.png"), "no grey copy of the tinted texture: " + files.keySet());
		assertThat(text(files, textures + "dyed/felt.png.mcmeta").equals(mcmeta), "the grey copy's .mcmeta isn't the texture's");
		assertThat(!files.containsKey(textures + "dyed/band.png"), "an untinted texture got a grey copy");
		assertThat(Arrays.equals(files.get(textures + "felt.png"), SkinFixtures.bytes(fx.folder("cowboy_hat_test").resolve("felt.png"))),
				"the natural texture changed");

		JsonObject natural = json(files, "assets/skins/models/item/cowboy_hat_test/cowboy_hat_test.json");
		JsonObject dyed = json(files, "assets/skins/models/item/cowboy_hat_test/dyed/cowboy_hat_test.json");
		assertThat(!dyed.has("parent"), "the dyed model isn't a full copy: " + dyed);
		assertThat(dyed.get("display").equals(natural.get("display")), "the dyed model's display transforms changed: " + dyed.get("display"));
		assertThat(dyed.getAsJsonObject("textures").equals(JsonParser.parseString("""
				{ "0": "skins:item/cowboy_hat_test/felt", "1": "skins:item/cowboy_hat_test/band", "particle": "skins:item/cowboy_hat_test/felt",
				"dyed_0": "skins:item/cowboy_hat_test/dyed/felt" }""")), "the dyed model's textures: " + dyed.get("textures"));
		JsonObject faces = dyed.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces");
		assertThat(faces.equals(JsonParser.parseString("""
				{ "north": { "uv": [0, 0, 1, 1], "texture": "#dyed_0", "tintindex": 0 },
				"south": { "uv": [0, 0, 1, 1], "texture": "#0" },
				"east": { "uv": [0, 0, 1, 1], "texture": "#0", "tintindex": -1 },
				"up": { "uv": [0, 0, 1, 1], "texture": "#1" } }""")), "the dyed model's faces: " + faces);
		assertThat(!natural.getAsJsonObject("textures").has("dyed_0")
				&& natural.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces").getAsJsonObject("north").get("texture").getAsString().equals("#0"),
				"the natural model changed: " + natural);

		assertThat(json(files, "assets/skins/items/cowboy_hat_test.json").equals(JsonParser.parseString("""
				{ "model": {
					"type": "minecraft:condition",
					"property": "minecraft:has_component",
					"component": "minecraft:dyed_color",
					"on_false": { "type": "minecraft:model", "model": "skins:item/cowboy_hat_test/cowboy_hat_test" },
					"on_true": { "type": "minecraft:model", "model": "skins:item/cowboy_hat_test/dyed/cowboy_hat_test",
						"tints": [ { "type": "minecraft:dye", "default": -1 } ] } } }""")), "the item definition: " + text(files, "assets/skins/items/cowboy_hat_test.json"));
		helper.succeed();
	}

	/** A Dyeable Hat with a {@code color} is always its dyed model, in that colour until the helmet is dyed. */
	@GameTest
	public void aDyeableHatWithAColorStartsInIt(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().tintedHat("green_hat_test", "{ model: \"green_hat_test.json\", dyeable: true, color: \"#4F7942\" }");
		ReloadReport report = fx.reload();
		assertThat(report.loaded().equals(List.of("green_hat_test")) && report.warnings().isEmpty(), "the hat: " + report);
		Map<String, byte[]> files = unzip(fx.pack().zip());
		assertThat(json(files, "assets/skins/items/green_hat_test.json").equals(JsonParser.parseString("""
				{ "model": { "type": "minecraft:model", "model": "skins:item/green_hat_test/dyed/green_hat_test",
					"tints": [ { "type": "minecraft:dye", "default": %d } ] } }""".formatted(0xFF4F7942))),
				"the item definition: " + text(files, "assets/skins/items/green_hat_test.json"));
		assertThat(fx.skins.skin("green_hat_test").map(s -> s.dyeable()).orElse(false), "the hat isn't dyeable");
		helper.succeed();
	}

	/**
	 * Each grey copy maps a pixel's brightness around one light grey: 205 + (lum(pixel) − lum(average)) × 1.25,
	 * clamped, with its alpha kept, where the average is of every visible pixel of all the hat's tinted
	 * textures together. Here the visible pixels' luminances are 124.2, 230 and 110, so the average's is 154.73.
	 */
	@GameTest
	public void theGreyCopiesShareOneAverageAcrossTheHatsTintedTextures(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().dyeableHat("two_tone_test")
				.file("two_tone_test", "two_tone_test.json", """
						{ "textures": { "a": "felt_a", "b": "felt_b" },
						"elements": [ { "from": [0, 8, 0], "to": [16, 12, 16], "faces": {
						"north": { "uv": [0, 0, 1, 1], "texture": "#a", "tintindex": 0 },
						"south": { "uv": [0, 0, 1, 1], "texture": "#b", "tintindex": 0 } } } ],
						"display": %s }""".formatted(SkinFixtures.ALL_DISPLAY))
				.file("two_tone_test", "felt_a.png", SkinFixtures.png(2, 0xFFC86432, 0xFFE6E6E6))
				.file("two_tone_test", "felt_b.png", SkinFixtures.png(2, 0xFF6E6E6E, 0x000A0A0A));
		ReloadReport report = fx.reload();
		assertThat(report.loaded().equals(List.of("two_tone_test")), "the hat: " + report);
		Map<String, byte[]> files = unzip(fx.pack().zip());
		int[] a = SkinFixtures.pixels(files.get("assets/skins/textures/item/two_tone_test/dyed/felt_a.png"));
		int[] b = SkinFixtures.pixels(files.get("assets/skins/textures/item/two_tone_test/dyed/felt_b.png"));
		assertThat(Arrays.equals(a, new int[] {0xFFA7A7A7, 0xFFFFFFFF}), "felt_a's grey copy: " + hex(a));
		assertThat(Arrays.equals(b, new int[] {0xFF959595, 0x00181818}), "felt_b's grey copy: " + hex(b));
		helper.succeed();
	}

	/** A Dyeable Hat's pack is the same every time, restarts included; turning dyeable off or on changes it. */
	@GameTest
	public void aDyeableHatsPackIsStableAndChangesWithDyeable(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().dyeableHat("cowboy_hat_test");
		ReloadReport first = fx.reload();
		ReloadReport second = fx.reload();
		assertThat(second.sha1().equals(first.sha1()) && !second.changed(), "rebuilt the same Dyeable Hat into another SHA-1");
		assertThat(Skins.at(fx.root).reload().sha1().equals(first.sha1()), "a restart built another SHA-1");
		fx.tintedHat("cowboy_hat_test", "{ model: \"cowboy_hat_test.json\" }");
		ReloadReport off = fx.reload();
		assertThat(off.changed() && !off.sha1().equals(first.sha1()), "turning dyeable off didn't change the pack");
		fx.dyeableHat("cowboy_hat_test");
		ReloadReport on = fx.reload();
		assertThat(on.changed() && on.sha1().equals(first.sha1()), "turning dyeable back on didn't give the first pack");
		helper.succeed();
	}

	private static List<String> hex(int[] pixels) {
		return Arrays.stream(pixels).mapToObj(Integer::toHexString).toList();
	}

	/** The last three builds stay downloadable under their SHA-1; an older one is gone. */
	@GameTest
	public void theLastThreeBuildsStayDownloadable(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create();
		SkinsPack[] builds = new SkinsPack[4];
		for (int i = 0; i < builds.length; i++) {
			fx.lookOnly("skin_" + i, "sword").reload();
			builds[i] = fx.pack();
		}
		assertThat(fx.skins.servePack(builds[0].path()).isEmpty(), "the fourth-newest build is still served");
		for (int i = 1; i < builds.length; i++) {
			var served = fx.skins.servePack(builds[i].path());
			assertThat(served.isPresent() && Arrays.equals(served.get(), builds[i].zip()), "build " + i + " is not served as built");
		}
		assertThat(fx.skins.servePack("metacraft-skins/" + "0".repeat(40) + ".zip").isEmpty(), "served a pack that was never built");
		helper.succeed();
	}

	private static JsonObject json(Map<String, byte[]> files, String path) {
		return JsonParser.parseString(text(files, path)).getAsJsonObject();
	}
}
