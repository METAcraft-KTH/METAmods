package se.metacraft.skins.gametest;

import se.metacraft.skins.DefaultSkins;
import se.metacraft.skins.Kind;
import se.metacraft.skins.ReloadReport;
import se.metacraft.skins.Release;
import se.metacraft.skins.SkinName;
import se.metacraft.skins.SkinSummary;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static se.metacraft.skins.gametest.SkinFixtures.assertThat;

/**
 * Reading skin folders: what loads, and every way a definition is rejected. A rejection sets
 * only that skin aside (Dormant, with its reason) and names the file at fault; the rest load.
 */
public final class SkinDefinitionTests {

	/**
	 * The skins that ship in the jar, from every group, go right into a new server's skins folder and load, each with its
	 * Kind, fitting every Material of it, dyeable where it should be, and Unobtainable where it should be (the
	 * Winter Wonderland skins and the two test hats); once the folder is there, a deleted one stays deleted.
	 */
	@GameTest
	public void theShippedSkinsLoadOnce(GameTestHelper helper) {
		Map<String, Kind> kinds = Map.ofEntries(Map.entry("testblockhat", Kind.HELMET), Map.entry("test_cap", Kind.HELMET),
				// The MCC hats.
				Map.entry("cowboy_hat", Kind.HELMET), Map.entry("witch_hat", Kind.HELMET),
				Map.entry("banana_hat_peeled", Kind.HELMET), Map.entry("cat_ears", Kind.HELMET), Map.entry("fishing_hat", Kind.HELMET),
				Map.entry("flight_helmet", Kind.HELMET), Map.entry("hard_hat", Kind.HELMET), Map.entry("headphones_gaming", Kind.HELMET),
				Map.entry("knight_helmet", Kind.HELMET), Map.entry("leaf_crown", Kind.HELMET), Map.entry("mobster_hat", Kind.HELMET),
				Map.entry("pirate_hat", Kind.HELMET), Map.entry("plunderers_bandana", Kind.HELMET), Map.entry("propeller_hat", Kind.HELMET),
				Map.entry("safari_hat", Kind.HELMET), Map.entry("sensei", Kind.HELMET), Map.entry("shades_cool", Kind.HELMET),
				Map.entry("spartan", Kind.HELMET), Map.entry("sun_hat_bow", Kind.HELMET), Map.entry("sun_visor", Kind.HELMET),
				Map.entry("traffic_cone_roadworks", Kind.HELMET), Map.entry("trucker_hat", Kind.HELMET),
				// Winter Wonderland.
				Map.entry("candy_cane_pickaxe", Kind.PICKAXE), Map.entry("candy_sword", Kind.SWORD), Map.entry("icicle_blade", Kind.SWORD),
				Map.entry("jingle_bell_mace", Kind.MACE), Map.entry("christmas_tree_hat", Kind.HELMET), Map.entry("frozen_head", Kind.HELMET),
				Map.entry("gift_box_head", Kind.HELMET), Map.entry("gingerbread_head", Kind.HELMET), Map.entry("hot_cocoa_mug", Kind.HELMET),
				Map.entry("lucia_crown", Kind.HELMET), Map.entry("nutcracker_shako", Kind.HELMET), Map.entry("penguin_hat", Kind.HELMET),
				Map.entry("polar_bear_hat", Kind.HELMET), Map.entry("reindeer_antlers", Kind.HELMET), Map.entry("santa_hat", Kind.HELMET),
				Map.entry("snow_globe", Kind.HELMET), Map.entry("snowed_in", Kind.HELMET), Map.entry("trapper_hat", Kind.HELMET),
				Map.entry("yeti_head", Kind.HELMET));
		Set<String> dyeable = Set.of("testblockhat", "test_cap", "cat_ears", "cowboy_hat", "fishing_hat", "flight_helmet", "headphones_gaming",
				"knight_helmet", "mobster_hat", "pirate_hat", "plunderers_bandana", "safari_hat", "shades_cool", "spartan", "sun_hat_bow", "sun_visor",
				"traffic_cone_roadworks", "trucker_hat", "witch_hat");
		Set<String> unobtainable = Set.of("testblockhat", "test_cap", "candy_cane_pickaxe", "candy_sword", "icicle_blade", "jingle_bell_mace", "christmas_tree_hat", "frozen_head",
				"gift_box_head", "gingerbread_head", "hot_cocoa_mug", "lucia_crown", "nutcracker_shako", "penguin_hat", "polar_bear_hat", "reindeer_antlers",
				"santa_hat", "snow_globe", "snowed_in", "trapper_hat", "yeti_head");
		SkinFixtures fx = SkinFixtures.create();
		Path folder = fx.skins.skinsFolder();
		assertThat(DefaultSkins.installIfMissing(folder), "the shipped skins weren't put in " + folder);
		ReloadReport report = fx.reload();
		assertThat(Set.copyOf(report.loaded()).equals(kinds.keySet()) && report.rejections().isEmpty(), "the shipped skins: " + report);
		for (SkinSummary skin : fx.skins.allSkins()) {
			assertThat(skin.kind().equals(Optional.of(kinds.get(skin.id()))) && skin.materials().isEmpty() && skin.dyeable() == dyeable.contains(skin.id())
					&& skin.release().map(Release::obtainable).orElse(true) != unobtainable.contains(skin.id()), skin.id() + " loaded as " + skin);
		}

		try (var files = Files.walk(folder.resolve("testblockhat"))) {
			for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		assertThat(!DefaultSkins.installIfMissing(folder), "the shipped skins were put in over an existing folder");
		assertThat(!fx.reload().loaded().contains("testblockhat"), "a deleted shipped skin came back");
		helper.succeed();
	}

	@GameTest
	public void brokenJson5IsRejected(GameTestHelper helper) {
		SkinFixtures fx = withHealthyNeighbour().file("broken", "skin.json5", "{ name: \"Broken\", kind: \"sword\", look: { model: \"broken.json\" ");
		assertRejected(fx.reload(), "broken", "skin.json5", "json5");
		helper.succeed();
	}

	@GameTest
	public void aFolderWithoutSkinJson5IsRejected(GameTestHelper helper) {
		SkinFixtures fx = withHealthyNeighbour().png("empty", "empty");
		assertRejected(fx.reload(), "empty", "skin.json5", "no skin.json5");
		helper.succeed();
	}

	@GameTest
	public void unknownFieldsAreRejected(GameTestHelper helper) {
		SkinFixtures fx = withHealthyNeighbour().lookOnly("typo", "sword")
				.file("typo", "skin.json5", "{ name: \"Typo\", kind: \"sword\", colour: \"red\", look: { model: \"typo.json\" } }");
		assertRejected(fx.reload(), "typo", "skin.json5", "colour");
		helper.succeed();
	}

	@GameTest
	public void missingRequiredFieldsAreRejected(GameTestHelper helper) {
		SkinFixtures fx = withHealthyNeighbour().lookOnly("nameless", "sword")
				.file("nameless", "skin.json5", "{ kind: \"sword\", look: { model: \"nameless.json\" } }");
		assertRejected(fx.reload(), "nameless", "skin.json5", "name");
		helper.succeed();
	}

	@GameTest
	public void anUnknownKindIsRejected(GameTestHelper helper) {
		SkinFixtures fx = withHealthyNeighbour().lookOnly("hat", "hat");
		assertRejected(fx.reload(), "hat", "skin.json5", "unknown kind");
		helper.succeed();
	}

	/** A spear takes an icon and an in-hand model; every other Kind one model. Either the other way round is wrong. */
	@GameTest
	public void theWrongLookShapeIsRejected(GameTestHelper helper) {
		SkinFixtures fx = withHealthyNeighbour()
				.lookOnly("one_model_spear", "spear")
				.file("two_model_sword", "skin.json5", "{ name: \"Sword\", kind: \"sword\", look: { icon: \"a.json\", in_hand: \"a.json\" } }")
				.file("two_model_sword", "a.json", SkinFixtures.model("a")).png("two_model_sword", "a");
		ReloadReport report = fx.reload();
		assertRejected(report, "one_model_spear", "skin.json5", "look: { icon, in_hand }");
		assertRejected(report, "two_model_sword", "skin.json5", "look: { model }");
		helper.succeed();
	}

	@GameTest
	public void aMissingModelIsRejected(GameTestHelper helper) {
		SkinFixtures fx = withHealthyNeighbour()
				.file("modelless", "skin.json5", "{ name: \"Modelless\", kind: \"axe\", look: { model: \"modelless.json\" } }");
		assertRejected(fx.reload(), "modelless", "skin.json5", "modelless.json");
		helper.succeed();
	}

	@GameTest
	public void aTextureWithoutItsPngIsRejected(GameTestHelper helper) {
		SkinFixtures fx = withHealthyNeighbour().lookOnly("pale", "hoe");
		fx.file("pale", "pale.json", SkinFixtures.model("block/pale_paint"));
		assertRejected(fx.reload(), "pale", "pale.json", "pale_paint.png");
		helper.succeed();
	}

	/** {@code materials} must be a list of one or more names; anything else rejects the skin. */
	@GameTest
	public void aMaterialsFieldThatIsntANonEmptyListIsRejected(GameTestHelper helper) {
		SkinFixtures fx = withHealthyNeighbour().limited("bare", "sword", "\"diamond\"").limited("none", "sword", "[]")
				.limited("numbered", "sword", "[ 4 ]");
		ReloadReport report = fx.reload();
		assertRejected(report, "bare", "skin.json5", "\"materials\" must be a list");
		assertRejected(report, "none", "skin.json5", "\"materials\" is empty");
		assertRejected(report, "numbered", "skin.json5", "\"materials\" must be a list");
		helper.succeed();
	}

	/**
	 * A Material must be one of the fixed names: {@code wooden} and {@code golden} get a hint, any
	 * other unknown name lists what the Kind comes in, as an unknown Kind lists the Kinds.
	 */
	@GameTest
	public void anUnknownMaterialIsRejectedWithAHint(GameTestHelper helper) {
		SkinFixtures fx = withHealthyNeighbour().limited("wooden", "sword", "[\"wooden\"]").limited("golden", "helmet", "[\"leather\", \"golden\"]")
				.limited("ruby", "pickaxe", "[\"ruby\"]").limited("shell", "helmet", "[\"Diamond\"]");
		ReloadReport report = fx.reload();
		assertRejected(report, "wooden", "skin.json5", "unknown material \"wooden\" (did you mean \"wood\"?)");
		assertRejected(report, "golden", "skin.json5", "unknown material \"golden\" (did you mean \"gold\"?)");
		assertRejected(report, "ruby", "skin.json5", "unknown material \"ruby\" (one of: wood, stone, copper, iron, gold, diamond, netherite)");
		assertRejected(report, "shell", "skin.json5",
				"unknown material \"Diamond\" (one of: leather, chainmail, copper, iron, gold, diamond, netherite, turtle)");
		helper.succeed();
	}

	/** A Material the Kind doesn't come in, any {@code materials} on a mace or elytra, and a duplicate are rejected. */
	@GameTest
	public void anImpossibleOrRepeatedMaterialIsRejected(GameTestHelper helper) {
		SkinFixtures fx = withHealthyNeighbour().limited("turtle_sword", "sword", "[\"diamond\", \"turtle\"]").limited("wooden_boots", "boots", "[\"wood\"]")
				.limited("hammer", "mace", "[\"diamond\"]").limited("wings", "elytra", "[\"leather\"]")
				.limited("twice", "sword", "[\"diamond\", \"netherite\", \"diamond\"]");
		ReloadReport report = fx.reload();
		assertRejected(report, "turtle_sword", "skin.json5", "\"turtle\" isn't a material of the sword Kind (it comes in: wood, stone, copper, iron, gold, diamond, netherite)");
		assertRejected(report, "wooden_boots", "skin.json5", "\"wood\" isn't a material of the boots Kind (it comes in: leather, chainmail, copper, iron, gold, diamond, netherite)");
		assertRejected(report, "hammer", "skin.json5", "the mace Kind has no materials, so it takes no \"materials\"");
		assertRejected(report, "wings", "skin.json5", "the elytra Kind has no materials, so it takes no \"materials\"");
		assertRejected(report, "twice", "skin.json5", "\"diamond\" is listed twice in \"materials\"");
		helper.succeed();
	}

	/** Every Material of the Kind may be listed, in any order, with no warning. */
	@GameTest
	public void aSkinMayListEveryMaterialOfItsKind(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create()
				.limited("all_helmets", "helmet", "[\"turtle\", \"netherite\", \"diamond\", \"gold\", \"iron\", \"copper\", \"chainmail\", \"leather\"]");
		ReloadReport report = fx.reload();
		assertThat(report.loaded().equals(List.of("all_helmets")) && report.warnings().isEmpty(), "every helmet Material: " + report);
		helper.succeed();
	}

	/** {@code /itemskins list}'s source: every folder with its Kind, and the reason a Dormant one failed. */
	@GameTest
	public void allSkinsListsEveryFolderWithDormantReasons(GameTestHelper helper) {
		SkinFixtures fx = withHealthyNeighbour().lookOnly("pale", "boots");
		fx.file("pale", "pale.json", SkinFixtures.model("pale_paint"));
		fx.reload();
		List<SkinSummary> all = fx.skins.allSkins();
		assertThat(all.stream().map(SkinSummary::id).toList().equals(List.of("healthy", "pale")), "listed " + all);
		SkinSummary healthy = all.get(0), pale = all.get(1);
		assertThat(healthy.kind().equals(Optional.of(Kind.SWORD)) && healthy.dormantReason().isEmpty(), "healthy: " + healthy);
		assertThat(pale.kind().equals(Optional.of(Kind.BOOTS)), "pale's Kind: " + pale);
		assertThat(pale.dormantReason().map(r -> r.contains("pale_paint.png")).orElse(false), "pale's reason: " + pale);
		helper.succeed();
	}

	/** Fixing a Dormant skin's folder and reloading brings it back; deleting a folder removes it from the list. */
	@GameTest
	public void reloadPicksUpFixedAndDeletedFolders(GameTestHelper helper) {
		SkinFixtures fx = withHealthyNeighbour().lookOnly("hat", "hat");
		assertRejected(fx.reload(), "hat", "skin.json5", "unknown kind");
		fx.lookOnly("hat", "helmet");
		ReloadReport fixed = fx.reload();
		assertThat(fixed.rejections().isEmpty() && fixed.loaded().equals(List.of("hat", "healthy")), "after the fix: " + fixed);
		fx.delete("hat");
		ReloadReport deleted = fx.reload();
		assertThat(deleted.loaded().equals(List.of("healthy")), "after deleting: " + deleted);
		assertThat(fx.skins.allSkins().size() == 1, "a deleted folder is still listed: " + fx.skins.allSkins());
		helper.succeed();
	}

	/**
	 * Missing display transforms are warned about and never invented: a missing gui (or head, on a
	 * helmet) loudly, a missing fixed or on_shelf quietly. The skin still loads.
	 */
	@GameTest
	public void missingDisplayTransformsAreWarnedAbout(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create()
				.file("santa_hat_test", "skin.json5", "{ name: \"Santa Hat\", kind: \"helmet\", look: { model: \"santa_hat_test.json\" } }")
				.file("santa_hat_test", "santa_hat_test.json", """
						{ "textures": { "0": "santa_hat_test" }, "elements": [], "display": { "ground": { "scale": [1, 1, 1] }, "on_shelf": {} } }""")
				.png("santa_hat_test", "santa_hat_test");
		ReloadReport report = fx.reload();
		assertThat(report.loaded().equals(List.of("santa_hat_test")), "the hat did not load: " + report);
		assertThat(warned(report, "gui", true), "no prominent gui warning: " + report.warnings());
		assertThat(warned(report, "head", true), "no prominent head warning: " + report.warnings());
		assertThat(warned(report, "fixed", false), "no minor fixed warning: " + report.warnings());
		assertThat(!warned(report, "on_shelf", false) && !warned(report, "on_shelf", true), "warned about on_shelf, which is there: " + report.warnings());
		String model = SkinFixtures.text(SkinFixtures.unzip(fx.pack().zip()), "assets/skins/models/item/santa_hat_test/santa_hat_test.json");
		assertThat(!model.contains("\"gui\"") && !model.contains("\"head\""), "a display transform was invented: " + model);
		helper.succeed();
	}

	/** Only a helmet skin can be a Dyeable Hat, a spear's look included, and {@code dyeable} is true or false. */
	@GameTest
	public void dyeableOnANonHelmetOrNotTrueOrFalseIsRejected(GameTestHelper helper) {
		SkinFixtures fx = withHealthyNeighbour().dyeableHat("dyed_sword")
				.file("dyed_sword", "skin.json5", "{ name: \"Sword\", kind: \"sword\", look: { model: \"dyed_sword.json\", dyeable: true } }")
				.dyeableHat("dyed_spear")
				.file("dyed_spear", "skin.json5", "{ name: \"Spear\", kind: \"spear\", look: { icon: \"dyed_spear.json\", in_hand: \"dyed_spear.json\", dyeable: false } }")
				.tintedHat("yes_hat", "{ model: \"yes_hat.json\", dyeable: \"yes\" }")
				.tintedHat("off_hat", "{ model: \"off_hat.json\", dyeable: false }");
		ReloadReport report = fx.reload();
		assertRejected(report, "dyed_sword", "skin.json5", "\"dyeable\" is only for helmet skins (Dyeable Hats), not a sword");
		assertRejected(report, "dyed_spear", "skin.json5", "\"dyeable\" is only for helmet skins (Dyeable Hats), not a spear");
		assertRejected(report, "yes_hat", "skin.json5", "\"dyeable\" must be true or false");
		assertThat(report.loaded().contains("off_hat"), "dyeable: false is the same as leaving it out, but: " + report);

		// color: only on a Dyeable Hat, and only "#RRGGBB".
		SkinFixtures colours = withHealthyNeighbour()
				.tintedHat("plain_coloured", "{ model: \"plain_coloured.json\", color: \"#FF0000\" }")
				.tintedHat("named_colour", "{ model: \"named_colour.json\", dyeable: true, color: \"red\" }")
				.tintedHat("good_colour", "{ model: \"good_colour.json\", dyeable: true, color: \"#4f7942\" }");
		ReloadReport coloured = colours.reload();
		assertRejected(coloured, "plain_coloured", "skin.json5", "\"color\" is a Dyeable Hat's colour until it's dyed, so it needs \"dyeable\": true");
		assertRejected(coloured, "named_colour", "skin.json5", "\"color\" must be a colour written \"#RRGGBB\"");
		assertThat(coloured.loaded().contains("good_colour"), "a lower-case colour: " + coloured);
		helper.succeed();
	}

	/** A Dyeable Hat's model must mark Tint faces in its own elements: ones only in a parent don't count. */
	@GameTest
	public void aDyeableHatWithNoTintFacesOfItsOwnIsRejected(GameTestHelper helper) {
		SkinFixtures fx = withHealthyNeighbour().dyeableHat("untinted").file("untinted", "untinted.json", SkinFixtures.model("felt"))
				.dyeableHat("inherited").file("inherited", "inherited.json", """
						{ "parent": "minecraft:block/grass_block", "textures": { "top": "felt", "side": "felt", "overlay": "felt" } }""");
		ReloadReport report = fx.reload();
		assertRejected(report, "untinted", "untinted.json", "marks no Tint faces (\"tintindex\") in its own elements, so the skin can't be dyeable");
		assertRejected(report, "inherited", "inherited.json", "Tint faces in its parent don't count");
		helper.succeed();
	}

	/**
	 * A model that marks Tint faces on a skin that isn't dyeable loads, on any Kind, with one minor
	 * warning: its faces keep their painted colours. A Dyeable Hat gets no such warning.
	 */
	@GameTest
	public void tintFacesOnASkinThatIsntDyeableAreWarnedAbout(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().tintedHat("plain_hat", "{ model: \"plain_hat.json\" }")
				.tintedHat("off_hat", "{ model: \"off_hat.json\", dyeable: false }")
				.tintedHat("tinted_sword", "{ model: \"tinted_sword.json\" }")
				.file("tinted_sword", "skin.json5", "{ name: \"Sword\", kind: \"sword\", look: { model: \"tinted_sword.json\" } }")
				.dyeableHat("dyed_hat");
		ReloadReport report = fx.reload();
		assertThat(report.loaded().equals(List.of("dyed_hat", "off_hat", "plain_hat", "tinted_sword")), "loaded: " + report);
		for (String id : List.of("plain_hat", "off_hat", "tinted_sword")) {
			List<ReloadReport.Warning> warnings = report.warnings().stream().filter(w -> w.skinId().equals(id)).toList();
			assertThat(warnings.size() == 1 && !warnings.getFirst().prominent() && warnings.getFirst().file().getFileName().toString().equals(id + ".json")
					&& warnings.getFirst().message().equals(id + ".json marks Tint faces but the skin isn't dyeable; they keep their painted colours"),
					id + "'s warnings: " + warnings);
		}
		assertThat(report.warnings().stream().noneMatch(w -> w.skinId().equals("dyed_hat")), "the Dyeable Hat was warned about: " + report.warnings());
		helper.succeed();
	}

	private static boolean warned(ReloadReport report, String slot, boolean prominent) {
		return report.warnings().stream().anyMatch(w -> w.skinId().equals("santa_hat_test") && w.prominent() == prominent && w.message().contains("\"" + slot + "\""));
	}

	/**
	 * A name's formatting codes colour it and are gone from its plain name; {@code released} and
	 * {@code obtainable} are read, and a skin that leaves them out is obtainable, with no date.
	 */
	@GameTest
	public void namesReleasesAndObtainabilityAreRead(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().lookOnly("plain", "sword").lookOnly("fancy", "helmet")
				.file("fancy", "skin.json5", "{ name: \"&6&lFancy §r& &#00ff00Hat\", kind: \"helmet\", released: \"2026/10/31\", obtainable: false,"
						+ " look: { model: \"fancy.json\" } }");
		ReloadReport report = fx.reload();
		assertThat(report.rejections().isEmpty(), "rejected: " + report);
		SkinSummary fancy = fx.skins.skin("fancy").orElseThrow();
		assertThat(fancy.displayName().equals("Fancy & Hat"), "the plain name is " + fancy.displayName());
		// Bold gold; reset to the yellow base, where "& " isn't a code; then green, any colour by its hex.
		List<Style> styles = fancy.styledName().getSiblings().stream().map(Component::getStyle).toList();
		assertThat(styles.size() == 3 && styles.get(0).isBold() && colour(styles.get(0), TextColor.fromLegacyFormat(ChatFormatting.GOLD))
						&& !styles.get(1).isBold() && colour(styles.get(1), TextColor.fromLegacyFormat(ChatFormatting.YELLOW))
						&& colour(styles.get(2), TextColor.fromRgb(0x00FF00)) && !fancy.styledName().getStyle().isItalic(),
				"the name is styled " + styles);
		assertThat(fancy.release().equals(Optional.of(new Release(Optional.of(LocalDate.of(2026, 10, 31)), false))), "fancy's release: " + fancy.release());
		assertThat(fancy.release().get().line().getString().equals("Released: 2026/10/31 - Unobtainable"), "fancy's line: " + fancy.release().get().line().getString());
		SkinSummary plain = fx.skins.skin("plain").orElseThrow();
		assertThat(plain.release().equals(Optional.of(new Release(Optional.empty(), true))), "plain's release: " + plain.release());
		assertThat(plain.release().get().line().getString().equals("Obtainable"), "plain's line: " + plain.release().get().line().getString());
		// As in vanilla, a colour clears the formats before it.
		assertThat(!SkinName.styled("&l&6Gold").getSiblings().getFirst().getStyle().isBold(), "a colour kept the bold before it");
		helper.succeed();
	}

	/**
	 * A gradient colours the name letter by letter, first colour to last; mirrored, it runs to the
	 * middle and back. The name's formats (here bold) stay.
	 */
	@GameTest
	public void aGradientColoursTheNameLetterByLetter(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().lookOnly("faded", "sword").lookOnly("mirrored", "sword")
				.file("faded", "skin.json5", "{ name: \"&lAbc\", gradient: { colors: [\"#000000\", \"#FFFFFF\"] }, kind: \"sword\", look: { model: \"faded.json\" } }")
				.file("mirrored", "skin.json5", "{ name: \"Abcde\", gradient: { colors: [\"#000000\", \"#FFFFFF\"], mirror: true }, kind: \"sword\","
						+ " look: { model: \"mirrored.json\" } }");
		ReloadReport report = fx.reload();
		assertThat(report.rejections().isEmpty(), "rejected: " + report);
		List<Style> faded = fx.skins.skin("faded").orElseThrow().styledName().getSiblings().stream().map(Component::getStyle).toList();
		assertThat(faded.size() == 3 && faded.stream().allMatch(Style::isBold) && colours(faded).equals(List.of(0x000000, 0x808080, 0xFFFFFF)),
				"the faded name is styled " + faded);
		List<Style> mirrored = fx.skins.skin("mirrored").orElseThrow().styledName().getSiblings().stream().map(Component::getStyle).toList();
		assertThat(colours(mirrored).equals(List.of(0x000000, 0x808080, 0xFFFFFF, 0x808080, 0x000000)), "the mirrored name is styled " + mirrored);
		assertThat(fx.skins.skin("mirrored").orElseThrow().displayName().equals("Abcde"), "the plain name is " + fx.skins.skin("mirrored").get().displayName());
		helper.succeed();
	}

	@GameTest
	public void aBadGradientIsRejected(GameTestHelper helper) {
		SkinFixtures fx = withHealthyNeighbour().lookOnly("coded", "sword").lookOnly("one", "sword").lookOnly("hex", "sword").lookOnly("extra", "sword")
				.file("coded", "skin.json5", "{ name: \"&6Coded\", gradient: { colors: [\"#000000\", \"#FFFFFF\"] }, kind: \"sword\", look: { model: \"coded.json\" } }")
				.file("one", "skin.json5", "{ name: \"One\", gradient: { colors: [\"#000000\"] }, kind: \"sword\", look: { model: \"one.json\" } }")
				.file("hex", "skin.json5", "{ name: \"Hex\", gradient: { colors: [\"#000000\", \"orange\"] }, kind: \"sword\", look: { model: \"hex.json\" } }")
				.file("extra", "skin.json5", "{ name: \"Extra\", gradient: { colors: [\"#000000\", \"#FFFFFF\"], reverse: true }, kind: \"sword\","
						+ " look: { model: \"extra.json\" } }");
		ReloadReport report = fx.reload();
		assertRejected(report, "coded", "skin.json5", "takes no colour codes");
		assertRejected(report, "one", "skin.json5", "two colours or more");
		assertRejected(report, "hex", "skin.json5", "gradient.colors[1]: must be a colour written \"#RRGGBB\"");
		assertRejected(report, "extra", "skin.json5", "unknown field \"reverse\"");
		helper.succeed();
	}

	private static List<Integer> colours(List<Style> styles) {
		return styles.stream().map(s -> s.getColor() == null ? -1 : s.getColor().getValue()).toList();
	}

	@GameTest
	public void aBadNameReleaseOrObtainableIsRejected(GameTestHelper helper) {
		SkinFixtures fx = withHealthyNeighbour().lookOnly("date", "sword").lookOnly("yes", "sword").lookOnly("codes", "sword")
				.file("date", "skin.json5", "{ name: \"Date\", kind: \"sword\", released: \"2026-10-31\", look: { model: \"date.json\" } }")
				.file("yes", "skin.json5", "{ name: \"Yes\", kind: \"sword\", obtainable: \"yes\", look: { model: \"yes.json\" } }")
				.file("codes", "skin.json5", "{ name: \"&6&l\", kind: \"sword\", look: { model: \"codes.json\" } }");
		ReloadReport report = fx.reload();
		assertRejected(report, "date", "skin.json5", "\"released\" must be a date");
		assertRejected(report, "yes", "skin.json5", "\"obtainable\" must be true or false");
		assertRejected(report, "codes", "skin.json5", "besides its formatting codes");
		helper.succeed();
	}

	private static boolean colour(Style style, TextColor colour) {
		return colour.equals(style.getColor());
	}

	/** A skin that loads, next to the broken one: a rejection must never take the others down. */
	private static SkinFixtures withHealthyNeighbour() {
		return SkinFixtures.create().lookOnly("healthy", "sword");
	}

	private static void assertRejected(ReloadReport report, String id, String file, String reasonFragment) {
		assertThat(report.loaded().contains("healthy"), "the healthy neighbour did not load: " + report);
		assertThat(!report.loaded().contains(id), id + " loaded but should be rejected");
		var rejection = report.rejections().stream().filter(r -> r.skinId().equals(id)).findFirst()
				.orElseThrow(() -> SkinFixtures.fail(id + " was not rejected: " + report));
		assertThat(rejection.file().getFileName().toString().equals(file), id + " blamed " + rejection.file() + ", not " + file);
		assertThat(rejection.reason().contains(reasonFragment), id + "'s reason \"" + rejection.reason() + "\" does not mention " + reasonFragment);
	}
}
