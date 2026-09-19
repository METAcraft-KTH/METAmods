package metacraft.ovvar.datagen;

import metacraft.ovvar.content.Patches;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * ARGB texture with the few operations the generator needs. Immutable; every op returns a copy.
 * Public because the wardrobe screen's paper doll ({@link metacraft.ovvar.pack.WardrobePreview})
 * composites the very textures datagen writes, with the very same operations.
 */
public final class Tex {
	public final int width, height;
	private final int[] argb;

	private Tex(int width, int height, int[] argb) {
		this.width = width;
		this.height = height;
		this.argb = argb;
	}

	public static Tex blank(int width, int height) {
		return new Tex(width, height, new int[width * height]);
	}

	/**
	 * One of the mod's own art files, by the name under {@code art/ovvar} it is kept as ({@code
	 * "patches/itk_8x8"}, no extension) — which is what {@link metacraft.ovvar.content.Patches.Art}
	 * answers, so datagen, the paper doll and the game tests all read a patch's art through one
	 * call and cannot disagree about which PNG a place shows.
	 */
	public static Tex art(String name) {
		String path = "/art/" + metacraft.ovvar.Ovvar.MOD_ID + "/" + name + ".png";
		InputStream in = Tex.class.getResourceAsStream(path);
		if (in == null) throw new IllegalStateException("missing art: " + path);
		return read(in);
	}

	/**
	 * A patch's art, of whichever of its sizes {@link metacraft.ovvar.content.Patches#artFor} named:
	 * the PNG on the classpath, or — for a size nobody drew — the art it is scaled down from, run
	 * through {@link #downscaled}. No generated size is written to the source tree, so this is the
	 * one place the pixels come from and datagen, the paper doll and the game tests cannot disagree
	 * about them any more than they can about which size is shown.
	 */
	public static Tex art(Patches.Art art) {
		Patches.Art source = art.source();
		return source == null ? art(art.file()) : art(source).downscaled(art.width(), art.height());
	}

	public static Tex read(InputStream in) {
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

	public byte[] png() {
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

	public static int a(int p) { return (p >>> 24) & 0xFF; }
	public static int r(int p) { return (p >>> 16) & 0xFF; }
	public static int g(int p) { return (p >>> 8) & 0xFF; }
	public static int b(int p) { return p & 0xFF; }
	private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
	private static int pack(int a, int r, int g, int b) { return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b); }

	public int get(int x, int y) {
		return argb[y * width + x];
	}

	/** Pixels of {@code src} at (sx, sy, w, h) copied onto a copy of this at (dx, dy); source alpha replaces, no blending. */
	public Tex blit(Tex src, int sx, int sy, int w, int h, int dx, int dy) {
		if (sx + w > src.width || sy + h > src.height || dx + w > width || dy + h > height) {
			throw new IllegalArgumentException("blit outside bounds: " + w + "x" + h + " from (" + sx + "," + sy + ") to (" + dx + "," + dy + ")");
		}
		int[] out = argb.clone();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int p = src.argb[(sy + y) * src.width + sx + x];
				if (a(p) > 0) out[(dy + y) * width + dx + x] = p;
			}
		}
		return new Tex(width, height, out);
	}


	/** Copy with one texel replaced. */
	public Tex with(int x, int y, int argb) {
		int[] out = this.argb.clone();
		out[y * width + x] = argb;
		return new Tex(width, height, out);
	}

	/** Copy with the rectangle (x, y, w, h) mirrored horizontally in place. */
	public Tex flipX(int x, int y, int w, int h) {
		int[] out = argb.clone();
		for (int yy = y; yy < y + h; yy++) {
			for (int i = 0; i < w; i++) out[yy * width + x + i] = argb[yy * width + x + (w - 1 - i)];
		}
		return new Tex(width, height, out);
	}

	/** Copy with the whole image mirrored horizontally. */
	public Tex flipX() {
		return flipX(0, 0, width, height);
	}

	/** Copy mirrored vertically. */
	public Tex flipY() {
		int[] out = new int[argb.length];
		for (int y = 0; y < height; y++) System.arraycopy(argb, (height - 1 - y) * width, out, y * width, width);
		return new Tex(width, height, out);
	}

	/** Copy turned a quarter turn clockwise (something pointing right comes to point down). */
	public Tex rotated() {
		int[] out = new int[argb.length];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) out[x * height + (height - 1 - y)] = argb[y * width + x];
		}
		return new Tex(height, width, out);
	}

	/** {@code over} alpha-composited on top of this (same size). */
	public Tex composite(Tex over) {
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

	/**
	 * The website overlays mark "erase the skin here" with pure green. Armour has nothing to erase,
	 * so those pixels become transparent. Matched loosely: a colour-managed PNG can decode 00FF00 as 01FE00.
	 */
	public Tex withoutGreenKey() {
		int[] out = new int[argb.length];
		for (int i = 0; i < argb.length; i++) {
			int p = argb[i];
			boolean key = r(p) < 32 && g(p) > 223 && b(p) < 32 && a(p) > 127;
			out[i] = key ? 0 : p;
		}
		return new Tex(width, height, out);
	}

	/** Nearest-neighbour upscale by an integer factor. */
	public Tex scale(int factor) {
		int[] out = new int[argb.length * factor * factor];
		int w = width * factor;
		for (int y = 0; y < height * factor; y++) {
			for (int x = 0; x < w; x++) out[y * w + x] = argb[(y / factor) * width + x / factor];
		}
		return new Tex(w, height * factor, out);
	}

	/**
	 * Nearest-neighbour resample to an exact size, for factors {@link #scale} cannot do: the paper
	 * doll goes from the garment textures' 2 texels per skin pixel to 3 screen px per skin pixel,
	 * a factor of 1.5, which keeps every patch texel the art has (a downscale to 1× would throw
	 * half of them away) at the price of every other column being 2 px wide instead of 1.
	 */
	public Tex resampled(int w, int h) {
		int[] out = new int[w * h];
		for (int y = 0; y < h; y++) {
			int sy = y * height / h;
			for (int x = 0; x < w; x++) out[y * w + x] = argb[sy * width + x * width / w];
		}
		return new Tex(w, h, out);
	}

	/** Below this alpha a pixel votes as nothing at all, and a pixel that wins as nothing comes out fully clear. */
	private static final int VOTING_ALPHA = 128;

	/**
	 * This shrunk to {@code w}×{@code h} by an area-weighted majority vote: each output pixel stands
	 * for a rectangle of the source, and takes whichever colour covers most of it. Chosen by trying
	 * the candidates against the pairs the artists had already drawn both sizes of (the ITK patch at
	 * 16, 12 and 8 px), and this is what came closest to the hand-drawn smaller one.
	 *
	 * <p>A vote rather than an average because the result must use no colour the source did not: the
	 * trim channel permutes a key palette built from every opaque colour of every patch art
	 * (GeneratedAssets), so a blended edge pixel would be a colour the palette has no slot for, and
	 * it is pixel art besides — a 12×12 patch has a dozen colours on purpose. Ties go to the colour
	 * that is <em>rarer</em> in the whole source, which is what keeps an outline, an eye or a letter
	 * stroke alive: the background always has the votes, so an even split has to fall the other way
	 * or every thin thing in the art dissolves. A remaining tie goes to whichever colour appears
	 * first reading rows, so the answer never depends on iteration order.
	 *
	 * <p>Integer arithmetic throughout: the overlap of output pixel {@code i} (spanning
	 * {@code [i*W, (i+1)*W)}) with source pixel {@code j} (spanning {@code [j*W', (j+1)*W')}) in
	 * units of 1/(W·W'), so exact ties are exactly ties. At a factor of one half this is a plain 2×2
	 * block majority.
	 */
	/**
	 * Every pixel repeated {@code factor} times each way: how art drawn at
	 * {@link metacraft.ovvar.content.Spot#ART_DETAIL} is put onto a texture drawn at the finer
	 * {@link metacraft.ovvar.content.Spot#DETAIL}. Nearest-neighbour by
	 * construction, so it invents no colour and keeps the art exactly as pixel art — a factor of one
	 * returns the same pixels, which is what makes the two detail levels agreeing a no-op.
	 */
	public Tex scaledUp(int factor) {
		if (factor < 1) throw new IllegalArgumentException("cannot scale up by " + factor);
		if (factor == 1) return this;
		Tex out = blank(width * factor, height * factor);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int p = get(x, y);
				if (p == 0) continue;
				for (int dy = 0; dy < factor; dy++) {
					for (int dx = 0; dx < factor; dx++) out = out.with(x * factor + dx, y * factor + dy, p);
				}
			}
		}
		return out;
	}

	public Tex downscaled(int w, int h) {
		if (w <= 0 || h <= 0 || w > width || h > height) {
			throw new IllegalArgumentException("cannot scale " + width + "x" + height + " down to " + w + "x" + h);
		}
		// Keyed once: an invisible pixel votes as the same "nothing" whatever colour it is written in
		// (art is exported with all sorts under a zero alpha), an opaque one as its exact ARGB, which
		// is never 0 — so key 0 is transparency alone. Frequency and first sighting break the ties.
		int[] key = new int[argb.length];
		Map<Integer, Integer> frequency = new HashMap<>(), firstSeen = new HashMap<>();
		for (int i = 0; i < argb.length; i++) {
			key[i] = a(argb[i]) < VOTING_ALPHA ? 0 : argb[i];
			frequency.merge(key[i], 1, Integer::sum);
			firstSeen.putIfAbsent(key[i], i);
		}
		int[] out = new int[w * h];
		Map<Integer, Long> votes = new HashMap<>();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				votes.clear();
				for (int sy = 0; sy < height; sy++) {
					long oy = Math.min((long) (y + 1) * height, (long) (sy + 1) * h) - Math.max((long) y * height, (long) sy * h);
					if (oy <= 0) continue;
					for (int sx = 0; sx < width; sx++) {
						long ox = Math.min((long) (x + 1) * width, (long) (sx + 1) * w) - Math.max((long) x * width, (long) sx * w);
						if (ox > 0) votes.merge(key[sy * width + sx], ox * oy, Long::sum);
					}
				}
				int won = 0;
				long winning = -1;
				for (Map.Entry<Integer, Long> vote : votes.entrySet()) {
					int colour = vote.getKey();
					long count = vote.getValue();
					boolean better = count > winning || (count == winning && (frequency.get(colour) < frequency.get(won)
							|| (frequency.get(colour).equals(frequency.get(won)) && firstSeen.get(colour) < firstSeen.get(won))));
					if (winning < 0 || better) {
						won = colour;
						winning = count;
					}
				}
				out[y * w + x] = won;   // key 0 is transparency, which is the clear pixel it came from
			}
		}
		return new Tex(w, h, out);
	}

	/** The pixels, ARGB, row-major — a copy, for the few passes that are easier written on an array. */
	public int[] pixels() {
		return argb.clone();
	}

	public static Tex of(int width, int height, int[] argb) {
		if (argb.length != width * height) throw new IllegalArgumentException("not " + width + "x" + height + " pixels");
		return new Tex(width, height, argb.clone());
	}

	/**
	 * Replace the hue/saturation of every visible pixel with {@code target}'s, keeping this pixel's
	 * relative lightness. Turns the purple IT ovve into the silicon-blue one without repainting it.
	 */
	public Tex tinted(int target) {
		float[] t = java.awt.Color.RGBtoHSB(r(target), g(target), b(target), null);
		int[] out = new int[argb.length];
		for (int i = 0; i < argb.length; i++) {
			int p = argb[i];
			if (a(p) == 0) continue;
			float[] hsb = java.awt.Color.RGBtoHSB(r(p), g(p), b(p), null);
			int rgb = java.awt.Color.HSBtoRGB(t[0], t[1] * hsb[1] / Math.max(0.01f, sat(target)), hsb[2]);
			out[i] = (a(p) << 24) | (rgb & 0xFFFFFF);
		}
		return new Tex(width, height, out);
	}

	private static float sat(int p) {
		return java.awt.Color.RGBtoHSB(r(p), g(p), b(p), null)[1];
	}

	/** Copy with every texel's HSB brightness multiplied by {@code factor} (clamped). */
	public Tex brightened(float factor) {
		int[] out = new int[argb.length];
		for (int i = 0; i < argb.length; i++) {
			int p = argb[i];
			if (a(p) == 0) continue;
			float[] hsb = java.awt.Color.RGBtoHSB(r(p), g(p), b(p), null);
			int rgb = java.awt.Color.HSBtoRGB(hsb[0], hsb[1], Math.min(1.0f, hsb[2] * factor));
			out[i] = (a(p) << 24) | (rgb & 0xFFFFFF);
		}
		return new Tex(width, height, out);
	}

	/** HSB brightness of a colour, 0–1. */
	public static float brightness(int rgb) {
		return java.awt.Color.RGBtoHSB(r(rgb), g(rgb), b(rgb), null)[2];
	}

	/** Every distinct fully opaque colour (ARGB, alpha 255), in first-seen order. */
	public List<Integer> opaqueColours() {
		List<Integer> out = new ArrayList<>();
		for (int p : argb) if (a(p) == 255 && !out.contains(p)) out.add(p);
		return out;
	}

	/** {@code a} mixed towards {@code b} by {@code t} (0 = a, 1 = b), alpha from {@code a}. */
	public static int mix(int a, int b, double t) {
		return pack(a(a), (int) Math.round(r(a) + (r(b) - r(a)) * t), (int) Math.round(g(a) + (g(b) - g(a)) * t), (int) Math.round(b(a) + (b(b) - b(a)) * t));
	}

	/** Most frequent opaque colour, for deriving a chapter's colour from its overlay. */
	public int dominant() {
		Map<Integer, Integer> counts = new HashMap<>();
		for (int p : argb) if (a(p) == 255) counts.merge(p & 0xFFFFFF, 1, Integer::sum);
		return counts.entrySet().stream().max(Map.Entry.comparingByValue())
				.orElseThrow(() -> new IllegalStateException("no opaque pixels")).getKey();
	}

	public boolean isEmpty() {
		return Arrays.stream(argb).allMatch(p -> a(p) == 0);
	}

	/** The rectangle (x, y, w, h) as its own texture. */
	public Tex crop(int x, int y, int w, int h) {
		return blank(w, h).blit(this, x, y, w, h, 0, 0);
	}

	/** This at the top of a texture {@code h} tall, transparent below (or this, if already that tall). */
	public Tex padBottom(int h) {
		return h <= height ? this : blank(width, h).blit(this, 0, 0, width, height, 0, 0);
	}

	/**
	 * Every visible pixel takes {@code target}'s hue and saturation and keeps its own brightness:
	 * one piece of cloth in every chapter's colour. (Unlike {@link #tinted}, which scales
	 * saturation, this works on grey art too.)
	 */
	public Tex colourised(int target) {
		float[] t = java.awt.Color.RGBtoHSB(r(target), g(target), b(target), null);
		int[] out = new int[argb.length];
		for (int i = 0; i < argb.length; i++) {
			int p = argb[i];
			if (a(p) == 0) continue;
			float[] hsb = java.awt.Color.RGBtoHSB(r(p), g(p), b(p), null);
			out[i] = (a(p) << 24) | (java.awt.Color.HSBtoRGB(t[0], t[1], hsb[2]) & 0xFFFFFF);
		}
		return new Tex(width, height, out);
	}

	/** Copy with a horizontal line of {@code argb} from (x, y), {@code length} px long. */
	public Tex line(int x, int y, int length, int argb) {
		int[] out = this.argb.clone();
		for (int i = 0; i < length; i++) out[y * width + x + i] = argb;
		return new Tex(width, height, out);
	}

	/**
	 * A font glyph's advance is measured to its rightmost visible column, so a glyph must reach its
	 * right edge for the label arithmetic in SewingFont to hold. Copy with the bottom-right pixel
	 * made just barely visible if the last column is empty.
	 */
	public Tex reachingRightEdge() {
		for (int y = 0; y < height; y++) if (a(get(width - 1, y)) != 0) return this;
		return with(width - 1, height - 1, 0x01000000);
	}

	/**
	 * {@code text} stamped in the vanilla font ({@code ascii.png}: 16×16 cells of 8×8, cell = code
	 * point) at (x, y) in {@code argb}, with the usual shadow. Only ASCII; the width is what the
	 * client would measure. Returns the copy and its width via {@code widthOut[0]}.
	 */
	public Tex stampText(Tex ascii, String text, int x, int y, int argb, boolean shadowed) {
		Tex out = this;
		int shadow = 0xFF000000 | ((r(argb) / 4) << 16) | ((g(argb) / 4) << 8) | (b(argb) / 4);
		for (int pass = shadowed ? 0 : 1; pass < 2; pass++) {
			int cx = x + (pass == 0 ? 1 : 0), cy = y + (pass == 0 ? 1 : 0);
			int colour = pass == 0 ? shadow : argb;
			for (char c : text.toCharArray()) {
				if (c == ' ') { cx += 4; continue; }
				if (c < 0x20 || c > 0x7E) throw new IllegalArgumentException("stampText is ASCII only: " + text);
				int gx = (c % 16) * 8, gy = (c / 16) * 8, glyphWidth = 0;
				for (int yy = 0; yy < 8; yy++) {
					for (int xx = 0; xx < 8; xx++) {
						if (a(ascii.get(gx + xx, gy + yy)) == 0) continue;
						glyphWidth = Math.max(glyphWidth, xx + 1);
						if (cx + xx < width && cy + yy < height) out = out.with(cx + xx, cy + yy, colour);
					}
				}
				cx += glyphWidth + 1;
			}
		}
		return out;
	}

	/** What {@link #stampText} advances by: the vanilla widths of {@code text}. */
	public static int textWidth(Tex ascii, String text) {
		int w = 0;
		for (char c : text.toCharArray()) {
			if (c == ' ') { w += 4; continue; }
			int gx = (c % 16) * 8, gy = (c / 16) * 8, glyphWidth = 0;
			for (int yy = 0; yy < 8; yy++) for (int xx = 0; xx < 8; xx++) if (a(ascii.get(gx + xx, gy + yy)) != 0) glyphWidth = Math.max(glyphWidth, xx + 1);
			w += glyphWidth + 1;
		}
		return w;
	}
}
