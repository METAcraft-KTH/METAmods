package metacraft.kultur;

import metacraft.kultur.datagen.Masks;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MasksTest {
	private static BufferedImage art(String path) throws Exception {
		try (InputStream in = MasksTest.class.getResourceAsStream("/art/kultur/" + path)) {
			if (in == null) throw new AssertionError("no art at " + path);
			return Masks.read(in);
		}
	}

	private static int alphaOutside(BufferedImage img, int x0, int y0, int w, int h) {
		int n = 0;
		for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) {
			boolean inside = x >= x0 && x < x0 + w && y >= y0 && y < y0 + h;
			if (!inside && (img.getRGB(x, y) >>> 24) != 0) n++;
		}
		return n;
	}

	private static int opaque(BufferedImage img, int x0, int y0, int w, int h) {
		int n = 0;
		for (int y = y0; y < y0 + h; y++) for (int x = x0; x < x0 + w; x++) if ((img.getRGB(x, y) >>> 24) != 0) n++;
		return n;
	}

	@Test
	public void scaleIsTheMultipleOf64() {
		assertEquals(1, Masks.scale(new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)));
		assertEquals(2, Masks.scale(new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB)));
		assertThrows(IllegalArgumentException.class, () -> Masks.scale(new BufferedImage(64, 128, BufferedImage.TYPE_INT_ARGB)));
		assertThrows(IllegalArgumentException.class, () -> Masks.scale(new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)));
	}

	/** The front face survives, the back face is its copy, and nothing else remains (tmeit's stray pixel goes). */
	@Test
	public void bannerKeepsTheFrontAndCopiesItToTheBack() throws Exception {
		BufferedImage src = art("it/banner/tmeit.png");
		BufferedImage out = Masks.banner(src);
		int k = 2;
		assertEquals(128, out.getWidth());
		int front = opaque(out, Masks.BANNER_FRONT_X * k, Masks.BANNER_FRONT_Y * k, Masks.BANNER_W * k, Masks.BANNER_H * k);
		assertTrue(front > 200, "front face is nearly empty: " + front);
		assertEquals(front, opaque(src, Masks.BANNER_FRONT_X * k, Masks.BANNER_FRONT_Y * k, Masks.BANNER_W * k, Masks.BANNER_H * k), "front face changed");
		for (int y = 0; y < Masks.BANNER_H * k; y++) for (int x = 0; x < Masks.BANNER_W * k; x++) {
			int f = out.getRGB(Masks.BANNER_FRONT_X * k + x, Masks.BANNER_FRONT_Y * k + y);
			int b = out.getRGB(Masks.BANNER_BACK_X * k + x, Masks.BANNER_FRONT_Y * k + y);
			assertEquals(f, b, "back differs from front at " + x + "," + y);
		}
		// Only the two faces carry alpha.
		int outside = 0;
		for (int y = 0; y < out.getHeight(); y++) for (int x = 0; x < out.getWidth(); x++) {
			boolean inFront = x >= Masks.BANNER_FRONT_X * k && x < (Masks.BANNER_FRONT_X + Masks.BANNER_W) * k && y >= Masks.BANNER_FRONT_Y * k && y < (Masks.BANNER_FRONT_Y + Masks.BANNER_H) * k;
			boolean inBack = x >= Masks.BANNER_BACK_X * k && x < (Masks.BANNER_BACK_X + Masks.BANNER_W) * k && y >= Masks.BANNER_FRONT_Y * k && y < (Masks.BANNER_FRONT_Y + Masks.BANNER_H) * k;
			if (!inFront && !inBack && (out.getRGB(x, y) >>> 24) != 0) outside++;
		}
		assertEquals(0, outside, "alpha outside the faces");
	}

	/** A hand-drawn shield keeps only its face (itk's stray pixel goes). */
	@Test
	public void shieldKeepsOnlyTheFace() throws Exception {
		BufferedImage src = art("it/shield/itk.png");
		BufferedImage out = Masks.shield(src);
		int k = 2;
		assertEquals(0, alphaOutside(out, Masks.SHIELD_FACE_X * k, Masks.SHIELD_FACE_Y * k, Masks.SHIELD_FACE_W * k, Masks.SHIELD_FACE_H * k));
		assertTrue(opaque(out, Masks.SHIELD_FACE_X * k, Masks.SHIELD_FACE_Y * k, Masks.SHIELD_FACE_W * k, Masks.SHIELD_FACE_H * k) > 100);
		for (int y = 0; y < Masks.SHIELD_FACE_H * k; y++) for (int x = 0; x < Masks.SHIELD_FACE_W * k; x++) {
			int sx = Masks.SHIELD_FACE_X * k + x, sy = Masks.SHIELD_FACE_Y * k + y;
			assertEquals(src.getRGB(sx, sy), out.getRGB(sx, sy), "face differs from source at " + x + "," + y);
		}
	}

	/** Derived: the banner's front face at half size lands in the shield's pattern region, and nowhere else. */
	@Test
	public void shieldFromBannerIsTheFrontFaceHalved() throws Exception {
		BufferedImage banner = art("it/banner/itk.png");
		BufferedImage out = Masks.shieldFromBanner(banner);
		int k = 2;
		assertEquals(128, out.getWidth());
		assertEquals(0, alphaOutside(out, Masks.SHIELD_X * k, Masks.SHIELD_Y * k, Masks.SHIELD_W * k, Masks.SHIELD_H * k));
		int got = opaque(out, Masks.SHIELD_X * k, Masks.SHIELD_Y * k, Masks.SHIELD_W * k, Masks.SHIELD_H * k);
		int src = opaque(banner, Masks.BANNER_FRONT_X * k, Masks.BANNER_FRONT_Y * k, Masks.BANNER_W * k, Masks.BANNER_H * k);
		// Max-alpha over 2×2 blocks: between a quarter of the source texels and all of them.
		assertTrue(got >= src / 4 && got <= src, got + " opaque texels from " + src);
		// A single opaque source texel is enough to light its block.
		BufferedImage one = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		one.setRGB(Masks.BANNER_FRONT_X + 5, Masks.BANNER_FRONT_Y + 7, 0xFF102030);
		BufferedImage d = Masks.shieldFromBanner(one);
		assertEquals(0xFF102030, d.getRGB(Masks.SHIELD_X + 2, Masks.SHIELD_Y + 3));
		assertEquals(1, opaque(d, 0, 0, 64, 64));
	}

	@Test
	public void emptyInGivesEmptyOut() {
		BufferedImage blank = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		assertEquals(0, opaque(Masks.banner(blank), 0, 0, 64, 64));
		assertEquals(0, opaque(Masks.shield(blank), 0, 0, 64, 64));
		assertEquals(0, opaque(Masks.shieldFromBanner(blank), 0, 0, 64, 64));
	}

	@Test
	public void pngRoundTrips() throws Exception {
		BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		img.setRGB(3, 4, 0x80FF0000);
		BufferedImage back = Masks.read(new java.io.ByteArrayInputStream(Masks.png(img)));
		assertEquals(0x80FF0000, back.getRGB(3, 4));
	}
}
