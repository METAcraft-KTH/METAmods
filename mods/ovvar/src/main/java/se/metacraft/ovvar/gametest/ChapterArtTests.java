package se.metacraft.ovvar.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import se.metacraft.ovvar.Ovvar;
import se.metacraft.ovvar.datagen.Tex;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * The chapter art tools/chapter_overlays.py draws: a top-up and a rolled-down skin overlay per
 * chapter ovve, the B-frack (Media's frack without Media's emblem), and an inventory icon each.
 * These read the checked-in PNGs, so a re-run of the tool that drifts from the approved art, or an
 * icon that loses the ovve's shape, fails here.
 */
public final class ChapterArtTests {
	private record New(String id, int colour) {}

	/** The 13 new ovvar, in the plan's table order, with their cloth colours. */
	private static final List<New> NEW = List.of(
			new New("arkitektur", 0xA43AA8),
			new New("bygg", 0xA0522D),
			new New("cl", 0x1A1A1A),
			new New("doktorand", 0x1A1A1A),
			new New("elektro", 0xF5F5F5),
			new New("energi", 0x006B71),
			new New("fysik", 0xFF642B),
			new New("indek", 0x4A2C1D),
			new New("kemi", 0xFFCB05),
			new New("mit", 0x8EC9EB),
			new New("open", 0xC62C22),
			new New("samhall", 0x294630),
			new New("tbas", 0xA2EE8D));

	/** The second colour on the stripes/placket and cuffs of the three trimmed ovvar. */
	private static final Map<String, Integer> TRIMS = Map.of(
			"cl", 0xC8102E,
			"doktorand", 0xC9A227,
			"tbas", 0x2C2C2C);

	private static Tex art(GameTestHelper helper, String name) {
		InputStream in = Ovvar.class.getResourceAsStream("/art/ovvar/" + name + ".png");
		if (in == null) helper.fail("missing art: " + name + ".png");
		return Tex.read(in);
	}

	private static double lum(int p) {
		return 0.2126 * Tex.r(p) + 0.7152 * Tex.g(p) + 0.0722 * Tex.b(p);
	}

	private static float saturation(int p) {
		return java.awt.Color.RGBtoHSB(Tex.r(p), Tex.g(p), Tex.b(p), null)[1];
	}

	private static void size(GameTestHelper helper, String name, Tex t, int w, int h) {
		if (t.width != w || t.height != h) helper.fail(name + " is " + t.width + "x" + t.height + ", not " + w + "x" + h);
	}

	@GameTest
	public void newOverlaysAreSkinOverlays(GameTestHelper helper) {
		for (New n : NEW) {
			size(helper, n.id(), art(helper, n.id()), 64, 64);
			size(helper, n.id() + "-nercabbad", art(helper, n.id() + "-nercabbad"), 64, 64);
		}
		size(helper, "bfrack", art(helper, "bfrack"), 64, 64);
		helper.succeed();
	}

	@GameTest
	public void overlayClothIsTheChapterColour(GameTestHelper helper) {
		for (New n : NEW) {
			for (String name : List.of(n.id(), n.id() + "-nercabbad")) {
				int dominant = art(helper, name).withoutGreenKey().dominant() | 0xFF000000;
				int want = n.colour() | 0xFF000000;
				if (dominant != want) helper.fail(name + "'s cloth is " + Integer.toHexString(dominant) + ", not " + Integer.toHexString(want));
			}
		}
		helper.succeed();
	}

	/** Body back face, where Media's frack carries Media's emblem. */
	private static boolean hasEmblem(Tex frack) {
		Tex t = frack.withoutGreenKey();
		for (int y = 20; y < 32; y++) {
			for (int x = 32; x < 40; x++) {
				int p = t.get(x, y);
				if (Tex.a(p) > 0 && (saturation(p) > 0.35f || lum(p) > 100)) return true;
			}
		}
		return false;
	}

	@GameTest
	public void bFrackHasNoMediaLogo(GameTestHelper helper) {
		if (!hasEmblem(art(helper, "mediafrack"))) helper.fail("mediafrack.png has no emblem on its back, so this test can't tell");
		if (hasEmblem(art(helper, "bfrack"))) helper.fail("bfrack.png still carries Media's emblem on its back");
		helper.succeed();
	}

	@GameTest
	public void chapterIconsShareTheOvveSilhouette(GameTestHelper helper) {
		Tex ovve = art(helper, "icon");
		for (New n : NEW) {
			String name = "icon_" + n.id();
			Tex icon = art(helper, name);
			size(helper, name, icon, 16, 16);
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					int o = ovve.get(x, y), p = icon.get(x, y);
					if ((Tex.a(o) > 0) != (Tex.a(p) > 0)) helper.fail(name + "'s alpha at (" + x + "," + y + ") differs from icon.png's");
					if (o == 0xFF000000 && p != 0xFF000000) helper.fail(name + " lost the outline at (" + x + "," + y + ")");
				}
			}
		}
		for (Map.Entry<String, Integer> t : TRIMS.entrySet()) {
			int want = t.getValue() | 0xFF000000;
			int got = art(helper, "icon_" + t.getKey()).get(2, 10);
			if (got != want) helper.fail("icon_" + t.getKey() + "'s cuff is " + Integer.toHexString(got) + ", not " + Integer.toHexString(want));
		}
		helper.succeed();
	}

	/** (3, 1) is one of icon.png's dark cloth shades; even black cloth must stay off the black outline. */
	@GameTest
	public void darkIconsStayReadable(GameTestHelper helper) {
		for (New n : NEW) {
			int p = art(helper, "icon_" + n.id()).get(3, 1);
			if (lum(p) < 48) helper.fail("icon_" + n.id() + "'s dark cloth is " + Integer.toHexString(p) + ", too close to the outline");
		}
		helper.succeed();
	}
}
