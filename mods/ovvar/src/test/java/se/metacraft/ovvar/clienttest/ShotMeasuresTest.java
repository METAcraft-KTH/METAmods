package se.metacraft.ovvar.clienttest;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the shaderpack client tests' screenshot verdicts (ShotMeasures) on real-GPU 1920x1080 BSL frames, cropped
 * (src/test/resources/clientshots): legs at frame offset (760,780), tags at (760,120). leg_unpatched_run1/2 are two
 * runs of unpatched BSL (violet/near-black checker), leg_patched the pack patched by ovvar-client (graded Data cloth).
 * tag_run1_* and tag_run2_* are two nametag runs; tag_base is run 2's frame without a tag (run 1 kept none; the
 * scene is static and the two runs' frames agree off the tag). Run 2's target frame has a toast sliding in.
 */
class ShotMeasuresTest {
	private static final int LEG_X = 760, LEG_Y = 780, TAG_X = 760, TAG_Y = 120;

	private static BufferedImage png(String name) {
		try (InputStream in = ShotMeasuresTest.class.getResourceAsStream("/clientshots/" + name + ".png")) {
			assertNotNull(in, name);
			return ImageIO.read(in);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private static ShotMeasures.Leg leg(String name) {
		ShotMeasures.Leg leg = ShotMeasures.leg(png(name), ShotMeasures.rightLeg(1920, 1080).shift(-LEG_X, -LEG_Y), ShotMeasures.legBackgroundDx(1080));
		System.out.println(name + ": " + leg);
		return leg;
	}

	@Test
	void unpatchedBslShowsTheChecker() {
		for (String name : new String[]{"leg_unpatched_run1", "leg_unpatched_run2"}) {
			ShotMeasures.Leg leg = leg(name);
			assertTrue(leg.showsChecker(), name + ": " + leg);
			assertFalse(leg.showsArt(), name + ": " + leg);
		}
	}

	@Test
	void patchedBslShowsTheArt() {
		ShotMeasures.Leg leg = leg("leg_patched");
		assertTrue(leg.showsArt(), leg.toString());
		assertFalse(leg.showsChecker(), leg.toString());
		assertEquals(0, leg.checkerLight(), leg.toString());
	}

	/** The vanilla checker (Spot.CHECKER_A #FF00FF / CHECKER_B #000000, one texel = 16 px) on grass; and the plain Data cloth. */
	@Test
	void vanillaColours() {
		BufferedImage img = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
		ShotMeasures.Rect crop = new ShotMeasures.Rect(150, 20, 206, 145);
		for (int y = 0; y < 200; y++) {
			for (int x = 0; x < 300; x++) img.setRGB(x, y, x >= crop.x0() && x < crop.x1() ? ((x / 16 + y / 16) % 2 == 0 ? 0xFF00FF : 0x000000) : 0x73A05F);
		}
		ShotMeasures.Leg checker = ShotMeasures.leg(img, crop, -120);
		assertTrue(checker.showsChecker(), checker.toString());
		assertFalse(checker.showsArt(), checker.toString());
		for (int y = 0; y < 200; y++) {
			for (int x = crop.x0(); x < crop.x1(); x++) img.setRGB(x, y, (x / 16 + y / 16) % 2 == 0 ? 0xBD3754 : 0x8E2940);
		}
		ShotMeasures.Leg art = ShotMeasures.leg(img, crop, -120);
		assertTrue(art.showsArt(), art.toString());
		assertFalse(art.showsChecker(), art.toString());
	}

	private static ShotMeasures.Nametag tags(String chapter, String target, String stock) {
		BufferedImage base = png("tag_base");
		ShotMeasures.Rect window = new ShotMeasures.Rect(576 - TAG_X, 135 - TAG_Y, 1344 - TAG_X, 675 - TAG_Y);   // NametagColoursClientTests.middle, clipped below
		window = new ShotMeasures.Rect(Math.max(0, window.x0()), window.y0(), Math.min(base.getWidth(), window.x1()), Math.min(base.getHeight(), window.y1()));
		ShotMeasures.Nametag n = ShotMeasures.nametag(base, png(chapter), png(target), png(stock), window);
		System.out.println(chapter + " vs " + target + " / " + stock + ": " + n);
		return n;
	}

	@Test
	void chapterTagDrawsLikeCerise() {
		for (String run : new String[]{"tag_run1_", "tag_run2_"}) {
			ShotMeasures.Nametag n = tags(run + "dark_purple", run + "hex_E83D84", run + "hex_AB00AB");
			assertNull(n.failure(), run + ": " + n);
		}
	}

	/** The stock frame fed in as the chapter tag: a chapter tag that kept #AB00AB must fail. */
	@Test
	void stockColouredChapterTagFails() {
		for (String run : new String[]{"tag_run1_", "tag_run2_"}) {
			ShotMeasures.Nametag n = tags(run + "hex_AB00AB", run + "hex_E83D84", run + "hex_AB00AB");
			assertNotNull(n.failure(), run + ": " + n);
		}
	}
}
