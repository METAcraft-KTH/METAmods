package metacraft.kultur.datagen;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Banner and shield pattern textures in vanilla's layout, from art that need not be tidy.
 *
 * <p>Vanilla's {@code entity/banner/<pattern>.png} is 64×64: the flag is a 20×40×1 box with its
 * UV at (0,0), so the front face is the 20×40 at (1,1) and the back face the 20×40 at (22,1), and
 * every vanilla pattern paints both (the back of a banner shows the pattern). PolymITer's art
 * painted the front only, so the back is filled here by copying the front — what vanilla's own
 * files do. {@code entity/shield/<pattern>.png} is 64×64 too: the plate is 12×22×1 at (0,0), its
 * face the 12×22 at (1,1), and vanilla masks stay inside the 10×20 at (2,2) with a texel of margin.
 * A hand-drawn shield keeps its whole face; a shield derived from a banner is the front face at
 * half size, which is exactly the 10×20 region. Everything outside the faces is cleared, which is
 * also what drops the odd stray pixel in a source file.
 *
 * <p>Textures may be any integer multiple of 64×64 (PolymITer's are 128×128); every region scales
 * with the file. Pure imaging, no Minecraft classes, so a unit test can hold it.
 */
public final class Masks {
	public static final int BANNER_W = 20, BANNER_H = 40, BANNER_FRONT_X = 1, BANNER_FRONT_Y = 1, BANNER_BACK_X = 22;
	public static final int SHIELD_FACE_X = 1, SHIELD_FACE_Y = 1, SHIELD_FACE_W = 12, SHIELD_FACE_H = 22;
	public static final int SHIELD_X = 2, SHIELD_Y = 2, SHIELD_W = 10, SHIELD_H = 20;

	private Masks() {}

	/** The texture's multiple of 64; a size that is not square or not a multiple of 64 is refused. */
	public static int scale(BufferedImage img) {
		int w = img.getWidth(), h = img.getHeight();
		if (w != h || w % 64 != 0 || w == 0) {
			throw new IllegalArgumentException("texture is " + w + "×" + h + ", wanted 64×64 or an integer multiple of it");
		}
		return w / 64;
	}

	/** The front face as drawn, copied onto the back face, nothing anywhere else. */
	public static BufferedImage banner(BufferedImage src) {
		int k = scale(src);
		BufferedImage out = blank(64 * k);
		copy(src, BANNER_FRONT_X * k, BANNER_FRONT_Y * k, out, BANNER_FRONT_X * k, BANNER_FRONT_Y * k, BANNER_W * k, BANNER_H * k);
		copy(src, BANNER_FRONT_X * k, BANNER_FRONT_Y * k, out, BANNER_BACK_X * k, BANNER_FRONT_Y * k, BANNER_W * k, BANNER_H * k);
		return out;
	}

	/** The shield's face as drawn, nothing anywhere else. */
	public static BufferedImage shield(BufferedImage src) {
		int k = scale(src);
		BufferedImage out = blank(64 * k);
		copy(src, SHIELD_FACE_X * k, SHIELD_FACE_Y * k, out, SHIELD_FACE_X * k, SHIELD_FACE_Y * k, SHIELD_FACE_W * k, SHIELD_FACE_H * k);
		return out;
	}

	/**
	 * The banner's front face at half size in the shield's pattern region: alpha is the max of each
	 * 2×2 block (a thin line stays a line), colour the mean of the block's opaque texels.
	 */
	public static BufferedImage shieldFromBanner(BufferedImage banner) {
		int k = scale(banner);
		BufferedImage out = blank(64 * k);
		for (int y = 0; y < SHIELD_H * k; y++) {
			for (int x = 0; x < SHIELD_W * k; x++) {
				int a = 0, r = 0, g = 0, b = 0, n = 0;
				for (int dy = 0; dy < 2; dy++) {
					for (int dx = 0; dx < 2; dx++) {
						int p = banner.getRGB(BANNER_FRONT_X * k + 2 * x + dx, BANNER_FRONT_Y * k + 2 * y + dy);
						int pa = p >>> 24;
						if (pa == 0) continue;
						a = Math.max(a, pa);
						r += (p >> 16) & 0xFF;
						g += (p >> 8) & 0xFF;
						b += p & 0xFF;
						n++;
					}
				}
				if (n > 0) out.setRGB(SHIELD_X * k + x, SHIELD_Y * k + y, (a << 24) | ((r / n) << 16) | ((g / n) << 8) | (b / n));
			}
		}
		return out;
	}

	public static BufferedImage read(InputStream in) {
		try {
			BufferedImage img = ImageIO.read(in);
			if (img == null) throw new IOException("not a PNG");
			BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
			argb.getGraphics().drawImage(img, 0, 0, null);
			return argb;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static byte[] png(BufferedImage img) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(img, "png", out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static BufferedImage blank(int size) {
		return new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
	}

	private static void copy(BufferedImage src, int sx, int sy, BufferedImage dst, int dx, int dy, int w, int h) {
		for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) dst.setRGB(dx + x, dy + y, src.getRGB(sx + x, sy + y));
	}
}
