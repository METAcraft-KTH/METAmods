package se.metacraft.skins.gametest;

import se.metacraft.skins.BoxSummary;
import se.metacraft.skins.DefaultSkins;
import se.metacraft.skins.ReloadReport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static se.metacraft.skins.gametest.SkinFixtures.assertThat;

/**
 * Skin Box files, {@code config/metacraft-skins/boxes/<id>.json5}, read by {@code Skins.reload()}:
 * what loads, what rejects a box (it goes Dormant) and what only warns, how chances are worded, and
 * the shipped Classic Hats Box and Winter Skin Box.
 */
public final class BoxFileTests {
	/** The shipped boxes are put in only when there's no boxes folder; after that it's the admin's, and a deleted box stays deleted. */
	@GameTest
	public void theShippedBoxesArePutInOnce(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create();
		Path folder = fx.skins.boxesFolder();
		assertThat(DefaultSkins.installIfMissing(fx.skins.skinsFolder()) && DefaultSkins.installBoxesIfMissing(folder), "the shipped skins and boxes weren't put in");
		assertThat(files(folder).equals(List.of("classic.json5", "winter.json5")), "the boxes folder holds " + files(folder));
		ReloadReport report = fx.reload();
		assertThat(Set.copyOf(report.boxes().loaded()).equals(Set.of("classic", "winter")) && report.boxes().rejections().isEmpty()
				&& report.boxes().warnings().isEmpty(), "the shipped boxes against the shipped skins: " + report.boxes());
		// Every MCC hat in the Classic Hats Box, by rarity: common 10, uncommon 5, rare 2.
		List<String> common = List.of("fishing_hat", "headphones_gaming", "leaf_crown", "mobster_hat", "plunderers_bandana", "safari_hat",
				"sensei", "sun_hat_bow", "sun_visor", "trucker_hat");
		List<String> uncommon = List.of("banana_hat_peeled", "cowboy_hat", "flight_helmet", "hard_hat", "pirate_hat", "witch_hat");
		List<String> rare = List.of("cat_ears", "knight_helmet", "propeller_hat", "shades_cool", "spartan", "traffic_cone_roadworks");
		long total = common.size() * 10L + uncommon.size() * 5L + rare.size() * 2L;
		List<String> classic = new ArrayList<>();
		for (String hat : common) classic.add(hat + " " + BoxSummary.chance(10, total));
		for (String hat : uncommon) classic.add(hat + " " + BoxSummary.chance(5, total));
		for (String hat : rare) classic.add(hat + " " + BoxSummary.chance(2, total));
		BoxSummary classicBox = fx.skins.box("classic").orElseThrow();
		assertThat(classicBox.name().equals(Optional.of("Classic Hats Box")) && chances(classicBox).equals(classic), "the Classic Hats Box: " + chances(classicBox));
		// Every Winter Wonderland skin in the Winter box, each as likely as the next.
		List<String> winter = List.of("candy_cane_pickaxe", "candy_sword", "christmas_tree_hat", "frozen_head", "gift_box_head",
				"gingerbread_head", "hot_cocoa_mug", "icicle_blade", "jingle_bell_mace", "lucia_crown", "nutcracker_shako",
				"penguin_hat", "polar_bear_hat", "reindeer_antlers", "santa_hat", "snow_globe", "snowed_in", "trapper_hat",
				"yeti_head");
		BoxSummary winterBox = fx.skins.box("winter").orElseThrow();
		String chance = BoxSummary.chance(1, winter.size());
		assertThat(winterBox.name().equals(Optional.of("Winter Skin Box")) && chances(winterBox).equals(winter.stream().map(skin -> skin + " " + chance).toList()),
				"the Winter box: " + chances(winterBox));

		try {
			Files.delete(folder.resolve("classic.json5"));
			Files.delete(folder.resolve("winter.json5"));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		assertThat(!DefaultSkins.installBoxesIfMissing(folder), "the shipped boxes were put in over an existing folder");
		assertThat(fx.reload().boxes().loaded().isEmpty() && files(folder).isEmpty(), "a deleted shipped box came back");
		helper.succeed();
	}

	private static List<String> files(Path folder) {
		try (var files = Files.list(folder)) {
			return files.map(p -> p.getFileName().toString()).sorted().toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@GameTest
	public void aBoxLoadsWithItsSkinsAndTheirChancesHighestFirst(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("pumpkin_hat", "helmet", "Pumpkin Hat", null).skin("cleaver", "sword", "&cCleaver", null)
				.box("spooky.json5", "{ name: \"&lSpooky Box\", description: \"Only in October.\", ominous: true,"
						+ " skins: [ { skin: \"cleaver\", weight: 1 }, { skin: \"pumpkin_hat\", weight: 3 } ] }");
		ReloadReport report = fx.reload();
		assertThat(report.boxes().loaded().equals(List.of("spooky")) && report.boxes().rejections().isEmpty() && report.boxes().warnings().isEmpty(),
				"the boxes part: " + report.boxes());
		BoxSummary box = fx.skins.box("spooky").orElseThrow(() -> SkinFixtures.fail("no box spooky: " + fx.skins.boxes()));
		assertThat(box.name().equals(Optional.of("Spooky Box")) && box.title().map(t -> t.getString()).equals(Optional.of("Spooky Box")),
				"named " + box.name() + ", " + box.title());
		assertThat(box.description().equals(Optional.of("Only in October.")) && !box.dormant() && box.leftOut().isEmpty(), "the box: " + box);
		assertThat(chances(box).equals(List.of("pumpkin_hat 75%", "cleaver 25%")), "its chances: " + chances(box));
		assertThat(fx.skins.boxes().equals(List.of(box)), "every box: " + fx.skins.boxes());
		helper.succeed();
	}

	/** Each way a box file is rejected: the box is Dormant with the reason (in the skin rules' words), and its neighbour still loads. */
	@GameTest
	public void aBrokenBoxIsDormantWithItsReason(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("a", "sword").skin("b", "sword")
				.box("healthy.json5", box("{ skin: \"a\", weight: 1 }"))
				.box("broken.json5", "{ name: \"Broken\", skins: [ ")
				.box("typo.json5", "{ name: \"Typo\", colour: \"red\", skins: [ { skin: \"a\", weight: 1 } ] }")
				.box("entry_typo.json5", box("{ skin: \"a\", weight: 1, rare: true }"))
				.box("nameless.json5", "{ skins: [ { skin: \"a\", weight: 1 } ] }")
				.box("skinless.json5", "{ name: \"Skinless\" }")
				.box("empty.json5", "{ name: \"Empty\", skins: [] }")
				.box("no_skin.json5", box("{ weight: 1 }"))
				.box("no_weight.json5", box("{ skin: \"a\" }"))
				.box("zero.json5", box("{ skin: \"a\", weight: 0 }"))
				.box("half.json5", box("{ skin: \"a\", weight: 1.5 }"))
				.box("huge.json5", box("{ skin: \"a\", weight: 1000001 }"))
				.box("words.json5", box("{ skin: \"a\", weight: \"ten\" }"))
				.box("twice.json5", box("{ skin: \"a\", weight: 1 }, { skin: \"b\", weight: 1 }, { skin: \"a\", weight: 2 }"))
				.box("ominous.json5", "{ name: \"Ominous\", ominous: \"yes\", skins: [ { skin: \"a\", weight: 1 } ] }")
				.box("codes.json5", "{ name: \"&l&6\", skins: [ { skin: \"a\", weight: 1 } ] }")
				.box("hex.json5", "{ name: \"Hex\", gradient: { colors: [\"#000000\", \"orange\"] }, skins: [ { skin: \"a\", weight: 1 } ] }")
				.box("coded.json5", "{ name: \"&6Coded\", gradient: { colors: [\"#000000\", \"#FFFFFF\"] }, skins: [ { skin: \"a\", weight: 1 } ] }")
				.box("Bad Box.json5", box("{ skin: \"a\", weight: 1 }"));
		ReloadReport report = fx.reload();
		assertThat(report.boxes().loaded().equals(List.of("healthy")), "loaded " + report.boxes().loaded());
		String weights = "skins[0]: field \"weight\" must be a whole number from 1 to 1,000,000";
		assertRejected(fx, report, "broken", "broken json5");
		assertRejected(fx, report, "typo", "unknown field \"colour\"");
		assertRejected(fx, report, "entry_typo", "skins[0]: unknown field \"rare\"");
		assertRejected(fx, report, "nameless", "missing field \"name\"");
		assertRejected(fx, report, "skinless", "missing field \"skins\"");
		assertRejected(fx, report, "empty", "\"skins\" is empty");
		assertRejected(fx, report, "no_skin", "skins[0]: missing field \"skin\"");
		assertRejected(fx, report, "no_weight", "skins[0]: missing field \"weight\"");
		for (String id : List.of("zero", "half", "huge", "words")) assertRejected(fx, report, id, weights);
		assertRejected(fx, report, "twice", "\"a\" is listed twice in \"skins\"");
		assertRejected(fx, report, "ominous", "field \"ominous\" must be true or false");
		assertRejected(fx, report, "codes", "field \"name\" must have some text besides its formatting codes");
		assertRejected(fx, report, "hex", "gradient.colors[1]: must be a colour written \"#RRGGBB\"");
		assertRejected(fx, report, "coded", "a name with a \"gradient\" takes no colour codes");
		assertRejected(fx, report, "Bad Box", "the file name is the box id and may only hold a-z, 0-9, _, - and .");
		helper.succeed();
	}

	/** A skin that's unknown or Dormant is left out, with a warning, and the other skins' chances grow; a box with none usable still loads. */
	@GameTest
	public void anUnknownOrDormantSkinIsLeftOut(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("a", "sword").skin("b", "sword")
				.box("mixed.json5", box("{ skin: \"a\", weight: 1 }, { skin: \"b\", weight: 1 }, { skin: \"nowhere\", weight: 2 }"))
				.box("gone.json5", box("{ skin: \"nowhere\", weight: 1 }"));
		fx.file("b", "skin.json5", "{ name: \"B\", kind: \"nope\" }");
		ReloadReport report = fx.reload();
		assertThat(report.boxes().loaded().equals(List.of("gone", "mixed")) && report.boxes().rejections().isEmpty(), "the boxes part: " + report.boxes());
		assertThat(messages(report).equals(List.of("gone: nowhere isn't a skin on this server, left out", "mixed: b is Dormant, left out",
				"mixed: nowhere isn't a skin on this server, left out")), "the warnings: " + messages(report));
		BoxSummary mixed = fx.skins.box("mixed").orElseThrow();
		assertThat(chances(mixed).equals(List.of("a 100%")) && mixed.leftOut().equals(List.of("b", "nowhere")) && mixed.listed() == 3, "mixed: " + mixed);
		BoxSummary gone = fx.skins.box("gone").orElseThrow();
		assertThat(!gone.dormant() && gone.entries().isEmpty() && gone.leftOut().equals(List.of("nowhere")), "gone: " + gone);

		// The skin fixed, the next reload puts it back in.
		fx.skin("b", "sword");
		fx.reload();
		assertThat(chances(fx.skins.box("mixed").orElseThrow()).equals(List.of("a 50%", "b 50%")), "mixed after the fix: " + fx.skins.box("mixed"));
		helper.succeed();
	}

	/** Only {@code .json5} files are boxes: anything else, and any folder, is ignored with a warning; a name starting with a dot is skipped quietly. */
	@GameTest
	public void otherFilesInTheBoxesFolderAreIgnored(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("a", "sword")
				.box("notes.txt", "remember to add the winter box")
				.box(".draft.json5", "{ not even json5")
				.box("real.json5", box("{ skin: \"a\", weight: 1 }"));
		try {
			Files.createDirectories(fx.skins.boxesFolder().resolve("old"));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		ReloadReport report = fx.reload();
		assertThat(report.boxes().loaded().equals(List.of("real")) && report.boxes().rejections().isEmpty(), "the boxes part: " + report.boxes());
		assertThat(messages(report).equals(List.of("boxes/notes.txt isn't a .json5 file, ignored", "boxes/old/ is a folder, ignored")),
				"the warnings: " + messages(report));
		assertThat(fx.skins.boxes().stream().map(BoxSummary::id).toList().equals(List.of("real")), "the boxes: " + fx.skins.boxes());
		helper.succeed();
	}

	/** Chances are rounded half-up to one decimal, without a trailing .0, and anything rounding to nothing reads "<0.1%"; ties stay in file order. */
	@GameTest
	public void chancesAreWordedForPlayers(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("a", "sword").skin("b", "sword").skin("c", "sword").skin("d", "sword").skin("e", "sword")
				.box("fifths.json5", box("{ skin: \"c\", weight: 1 }, { skin: \"a\", weight: 1 }, { skin: \"e\", weight: 1 }, { skin: \"b\", weight: 1 }, { skin: \"d\", weight: 1 }"))
				.box("halves.json5", box("{ skin: \"a\", weight: 37 }, { skin: \"b\", weight: 3 }"))
				.box("thin.json5", box("{ skin: \"a\", weight: 997 }, { skin: \"b\", weight: 3 }"))
				.box("tiny.json5", box("{ skin: \"a\", weight: 9999 }, { skin: \"b\", weight: 1 }"))
				.box("edge.json5", box("{ skin: \"a\", weight: 1999 }, { skin: \"b\", weight: 1 }"))
				.box("ties.json5", box("{ skin: \"c\", weight: 1 }, { skin: \"a\", weight: 2 }, { skin: \"b\", weight: 1 }"));
		fx.reload();
		assertChances(fx, "fifths", "c 20%", "a 20%", "e 20%", "b 20%", "d 20%");
		assertChances(fx, "halves", "a 92.5%", "b 7.5%");
		assertChances(fx, "thin", "a 99.7%", "b 0.3%");
		assertChances(fx, "tiny", "a 100%", "b <0.1%");
		assertChances(fx, "edge", "a 100%", "b 0.1%");
		assertChances(fx, "ties", "a 50%", "c 25%", "b 25%");
		helper.succeed();
	}

	@GameTest
	public void aDormantBoxComesBackAfterAFixAndAReload(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("a", "sword").box("fixme.json5", "{ name: \"Fix Me\", skins: [ { skin: \"a\" } ] }");
		fx.reload();
		assertThat(fx.skins.box("fixme").map(BoxSummary::dormant).orElse(false), "the broken box isn't Dormant: " + fx.skins.box("fixme"));
		fx.box("fixme.json5", "{ name: \"Fix Me\", skins: [ { skin: \"a\", weight: 1 } ] }");
		ReloadReport report = fx.reload();
		assertThat(report.boxes().loaded().equals(List.of("fixme")) && chances(fx.skins.box("fixme").orElseThrow()).equals(List.of("a 100%")),
				"after the fix: " + report.boxes() + ", " + fx.skins.box("fixme"));
		helper.succeed();
	}

	/** A box file named "Box" listing these entries (the inside of its {@code skins} list). */
	private static String box(String entries) {
		return "{ name: \"Box\", skins: [ " + entries + " ] }";
	}

	private static void assertRejected(SkinFixtures fx, ReloadReport report, String id, String reason) {
		var rejection = report.boxes().rejections().stream().filter(r -> r.boxId().equals(id)).findFirst()
				.orElseThrow(() -> SkinFixtures.fail(id + " wasn't rejected: " + report.boxes()));
		assertThat(rejection.reason().contains(reason) && rejection.file().getFileName().toString().equals(id + ".json5"),
				id + " was rejected for \"" + rejection.reason() + "\" (" + rejection.file().getFileName() + "), not \"" + reason + "\"");
		BoxSummary box = fx.skins.box(id).orElseThrow(() -> SkinFixtures.fail("no Dormant box " + id));
		assertThat(box.dormantReason().equals(Optional.of(rejection.reason())) && box.entries().isEmpty(), id + " reads as " + box);
	}

	private static void assertChances(SkinFixtures fx, String id, String... expected) {
		List<String> chances = chances(fx.skins.box(id).orElseThrow(() -> SkinFixtures.fail("no box " + id)));
		assertThat(chances.equals(List.of(expected)), id + "'s chances: " + chances);
	}

	private static List<String> messages(ReloadReport report) {
		return report.boxes().warnings().stream().map(ReloadReport.BoxWarning::message).toList();
	}

	static List<String> chances(BoxSummary box) {
		return box.entries().stream().map(e -> e.skin() + " " + e.chance()).toList();
	}
}
