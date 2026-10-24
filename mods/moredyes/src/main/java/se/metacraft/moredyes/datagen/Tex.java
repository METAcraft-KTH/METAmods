package se.metacraft.moredyes.datagen;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** ARGB texture with the few operations the generator needs. Immutable; every op returns a copy. */
final class Tex {
	final int width, height;
	private final int[] argb;

	private Tex(int width, int height, int[] argb) {
		this.width = width;
		this.height = height;
		this.argb = argb;
	}

	static Tex read(InputStream in) {
		try (in) {
			BufferedImage img = ImageIO.read(in);
			if (img == null) throw new IOException("not an image");
			int w = img.getWidth(), h = img.getHeight();
			int bands = img.getRaster().getNumBands();
			int[] px;
			if (bands <= 2 && img.getColorModel().getColorSpace().getType() == java.awt.color.ColorSpace.TYPE_GRAY) {
				// Greyscale PNGs: getRGB would run the samples through ImageIO's linear grey colour
				// space and shift them; take the raw 8-bit samples like every other PNG decoder does.
				px = new int[w * h];
				int[] s = new int[bands];
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						img.getRaster().getPixel(x, y, s);
						int v = s[0], a = bands == 2 ? s[1] : 255;
						px[y * w + x] = (a << 24) | (v << 16) | (v << 8) | v;
					}
				}
			} else {
				px = img.getRGB(0, 0, w, h, null, 0, w);
			}
			return new Tex(w, h, px);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	byte[] png() {
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		img.setRGB(0, 0, width, height, argb, 0, width);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			ImageIO.write(img, "png", out);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out.toByteArray();
	}

	private static int a(int p) { return (p >>> 24) & 0xFF; }
	private static int r(int p) { return (p >>> 16) & 0xFF; }
	private static int g(int p) { return (p >>> 8) & 0xFF; }
	private static int b(int p) { return p & 0xFF; }
	private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
	/** Channels are clamped: fully transparent pixels can land outside the ramp and must not leak into alpha. */
	private static int pack(int a, int r, int g, int b) { return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b); }
	private static double lum(int p) { return 0.2126 * r(p) + 0.7152 * g(p) + 0.0722 * b(p); }

	/**
	 * Map the source's luminance range onto a two-stop ramp, preserving alpha. Luminance is
	 * normalised to the source's own min/max over visible pixels, so a texture keeps its full
	 * contrast (wool knit, concrete grain) whatever its absolute brightness. A ramp whose stops are
	 * equal is a plain colour: see {@link #recolourWhere}.
	 */
	Tex recolour(int dark, int light) {
		return recolourWhere(i -> a(argb[i]) > 0, i -> true, dark, light);
	}

	/**
	 * {@link #recolour} for just the texels a vanilla dyed variant changes from this one: a bed's
	 * blanket but not its pillow or wooden frame, a bundle's cloth but not its string, a candle's wax
	 * but not its wick.
	 */
	Tex recolourWhereDyed(Tex dyed, int dark, int light) {
		if (dyed.width != width || dyed.height != height) throw new IllegalArgumentException("size mismatch");
		// vanilla's dyed beds also shade the pillow a touch differently (a channel sum of 20-40 apart);
		// what the dye colours is 300+ apart, so anything under 100 counts as unchanged
		java.util.function.IntPredicate blanket = i -> a(argb[i]) > 0 && Math.abs(r(argb[i]) - r(dyed.argb[i]))
				+ Math.abs(g(argb[i]) - g(dyed.argb[i])) + Math.abs(b(argb[i]) - b(dyed.argb[i])) > 100;
		return recolourWhere(blanket, blanket, dark, light);
	}

	/** Below 1 so a tinted texture's highlights (up to ~8% over its mean on wool) don't clip. */
	private static final double TINT_HEADROOM = 0.9;

	/**
	 * Recolour the texels (by index) {@code write} accepts, measured over those {@code measure} accepts. With
	 * {@code dark != light} the measured luminance range is stretched onto the ramp. With
	 * {@code dark == light} (an animation keyframe without a ramp) each texel is that colour scaled by
	 * its luminance over the measured mean: the source keeps its own, usually slight, contrast, the way
	 * vanilla's dyed wool and concrete look, instead of being stretched to a full dark-to-light ramp.
	 */
	private Tex recolourWhere(java.util.function.IntPredicate measure, java.util.function.IntPredicate write, int dark, int light) {
		double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE, sum = 0;
		int n = 0;
		for (int i = 0; i < argb.length; i++) {
			if (measure.test(i)) {
				double l = lum(argb[i]);
				lo = Math.min(lo, l);
				hi = Math.max(hi, l);
				sum += l;
				n++;
			}
		}
		if (lo > hi) { lo = 0; hi = 255; }
		double span = hi - lo == 0 ? 1.0 : hi - lo;
		double mean = n == 0 || sum == 0 ? 1.0 : sum / n;
		int[] out = argb.clone();
		for (int i = 0; i < argb.length; i++) {
			int p = argb[i];
			if (!write.test(i)) continue;
			if (dark == light) {
				double f = TINT_HEADROOM * lum(p) / mean;
				out[i] = pack(a(p), (int) Math.round(r(dark) * f), (int) Math.round(g(dark) * f), (int) Math.round(b(dark) * f));
				continue;
			}
			double t = (lum(p) - lo) / span;
			out[i] = pack(a(p),
					(int) Math.round(r(dark) + (r(light) - r(dark)) * t),
					(int) Math.round(g(dark) + (g(light) - g(dark)) * t),
					(int) Math.round(b(dark) + (b(light) - b(dark)) * t));
		}
		return new Tex(width, height, out);
	}

	/**
	 * Horizontal bands of equal height across the rows the variants colour differently (where they
	 * all agree, as on a harness's leather, there is nothing to band; equipment sheets also leave much
	 * of their height empty); band {@code i} is taken from {@code variants.get(i)}.
	 */
	static Tex bands(java.util.List<Tex> variants) {
		Tex first = variants.get(0);
		int top = first.height, bottom = -1;
		for (int y = 0; y < first.height; y++) {
			for (int x = 0; x < first.width; x++) {
				int i = y * first.width + x;
				if (a(first.argb[i]) > 0 && variants.stream().anyMatch(v -> v.argb[i] != first.argb[i])) {
					top = Math.min(top, y);
					bottom = y;
					break;
				}
			}
		}
		int rows = Math.max(1, bottom - top + 1);
		int[] out = new int[first.argb.length];
		for (int y = 0; y < first.height; y++) {
			int band = Math.max(0, Math.min(variants.size() - 1, (y - top) * variants.size() / rows));
			System.arraycopy(variants.get(band).argb, y * first.width, out, y * first.width, first.width);
		}
		return new Tex(first.width, first.height, out);
	}

	/**
	 * {@link #recolour} for the petals only: the warm, saturated texels (a torchflower's flame), mapped
	 * onto the ramp by their own lightness; stem and leaves keep their colours.
	 */
	Tex recolourPetals(int dark, int light) {
		java.util.function.IntPredicate petal = p -> {
			if (a(p) == 0) return false;
			float[] hsb = java.awt.Color.RGBtoHSB(r(p), g(p), b(p), null);
			return hsb[1] > 0.35f && (hsb[0] < 0.17f || hsb[0] > 0.95f);
		};
		return recolourWhere(i -> petal.test(argb[i]), i -> petal.test(argb[i]), dark, light);
	}

	/**
	 * Vanilla's hurt overlay baked in: the overlay texel is red at alpha 178/255 and the entity
	 * shader does mix(overlay, texel, overlay.a), i.e. texel * 0.7 + (76, 0, 0).
	 */
	Tex hurt() {
		int[] out = new int[argb.length];
		for (int i = 0; i < argb.length; i++) {
			int p = argb[i];
			out[i] = pack(a(p), Math.min(255, (int) Math.round(r(p) * 0.7 + 76.5)),
					(int) Math.round(g(p) * 0.7), (int) Math.round(b(p) * 0.7));
		}
		return new Tex(width, height, out);
	}

	/** The frames of an animated sprite, one under the other (the layout of a {@code .png.mcmeta} animation strip). */
	static Tex stack(java.util.List<Tex> frames) {
		Tex first = frames.getFirst();
		int[] out = new int[first.argb.length * frames.size()];
		int at = 0;
		for (Tex frame : frames) {
			if (frame.width != first.width || frame.height != first.height) throw new IllegalArgumentException("size mismatch");
			System.arraycopy(frame.argb, 0, out, at, frame.argb.length);
			at += frame.argb.length;
		}
		return new Tex(first.width, first.height * frames.size(), out);
	}

	/** {@code over} alpha-composited on top of this (same size). */
	Tex composite(Tex over) {
		if (over.width != width || over.height != height) throw new IllegalArgumentException("size mismatch");
		int[] out = new int[argb.length];
		for (int i = 0; i < argb.length; i++) {
			int base = argb[i], top = over.argb[i];
			double ta = a(top) / 255.0, ba = a(base) / 255.0;
			double oa = ta + ba * (1 - ta);
			if (oa == 0) { out[i] = 0; continue; }
			int rr = (int) Math.round((r(top) * ta + r(base) * ba * (1 - ta)) / oa);
			int gg = (int) Math.round((g(top) * ta + g(base) * ba * (1 - ta)) / oa);
			int bb = (int) Math.round((b(top) * ta + b(base) * ba * (1 - ta)) / oa);
			out[i] = pack((int) Math.round(oa * 255), rr, gg, bb);
		}
		return new Tex(width, height, out);
	}
}
