package se.metacraft.ovvar.clienttest;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The screenshot judgements of the shaderpack client tests, as pure functions over images (plain JDK, no game
 * classes), so ShotMeasuresTest pins them on saved real-GPU frames without starting Minecraft. Each judgement is
 * relative or by colour family, never exact RGB: a shaderpack (BSL) grades every colour, e.g. the checker's #FF00FF
 * becomes about violet rgb(100,40,170), its black about rgb(34,31,55), the Data cloth about rgb(80,43,70), and the
 * white wall behind the nametags blue-grey.
 */
final class ShotMeasures {
	private ShotMeasures() {
	}

	/** A pixel rectangle, x0/y0 inclusive, x1/y1 exclusive. */
	record Rect(int x0, int y0, int x1, int y1) {
		Rect shift(int dx, int dy) {
			return new Rect(x0 + dx, y0 + dy, x1 + dx, y1 + dy);
		}

		int area() {
			return (x1 - x0) * (y1 - y0);
		}
	}

	// ---- the checker on the leg (OvvarShaderpackClientTests)

	/** Share of the crop that must be garment (differs from the grass beside the legs), or the crop missed. */
	static final double GARMENT_SHARE = 0.50;
	/** An unpatched pack: at least this share of checker-light (violet-family) and as much checker-dark. */
	static final double CHECKER_CELL_SHARE = 0.25;
	/** ... and the two together cover the crop: a two-colour pattern, not a violet tint. */
	static final double CHECKER_SHARE = 0.75;
	/** A patched pack: at most this share of violet-family pixels (none seen on the real frames), and cloth. */
	static final double ART_MAX_VIOLET_SHARE = 0.005;
	static final double CLOTH_SHARE = 0.30;
	/**
	 * The Data ovve cloth's dominant texel, rgb(189,55,84): hue about 345 degrees, apart from skin (20-35) and from
	 * the checker's magenta (300) or BSL's violet (265). BSL grades it to about (80,43,70), hue 344.
	 */
	private static final float DATA_CLOTH_HUE = Color.RGBtoHSB(189, 55, 84, null)[0];
	private static final float HUE_RANGE = 25f / 360f;
	private static final float MIN_SATURATION = 0.3f;
	private static final int BACKGROUND_TOLERANCE = 40;

	/**
	 * The mannequin's right leg (screen left of centre) in a frame of the shaderpack test's scene: the mannequin three
	 * blocks ahead, about 16 screen px per model pixel at 1080 high; the trousers span y = h/2 + 270..405 and x =
	 * w/2 - 67..+68. The crop keeps clear of the belt, the boots and the gap between the legs.
	 */
	static Rect rightLeg(int w, int h) {
		double s = h / 1080.0;
		int cx = w / 2, cy = h / 2;
		return new Rect(cx - (int) (62 * s), cy + (int) (275 * s), cx - (int) (6 * s), cy + (int) (400 * s));
	}

	/** Where {@link #leg} reads the background for a crop from {@link #rightLeg}: grass left of the legs, below the hand. */
	static int legBackgroundDx(int h) {
		return -(int) (120 * h / 1080.0);
	}

	/**
	 * Pixel counts over a leg crop. Checker-light: the checker's #FF00FF or what a shaderpack grades it to, the
	 * violet family (strongly blue, red above green). Checker-dark: the checker's black, any dark pixel that is not
	 * violet (the patched cloth is dark under BSL too, so only the violet tells checker from art).
	 */
	record Leg(int total, int garment, int cloth, int checkerLight, int checkerDark) {
		boolean onGarment() {
			return garment >= total * GARMENT_SHARE;
		}

		/** An unpatched pack: the leg is a two-colour violet/dark pattern. */
		boolean showsChecker() {
			return onGarment() && checkerLight >= total * CHECKER_CELL_SHARE && checkerDark >= total * CHECKER_CELL_SHARE
					&& checkerLight + checkerDark >= total * CHECKER_SHARE;
		}

		/** A patched pack: the leg shows the Data cloth and (next to) no violet. */
		boolean showsArt() {
			return onGarment() && cloth >= total * CLOTH_SHARE && checkerLight <= total * ART_MAX_VIOLET_SHARE;
		}

		@Override
		public String toString() {
			return "garment " + garment + "/" + total + ", cloth " + cloth + ", checker violet " + checkerLight + " + dark " + checkerDark;
		}
	}

	/** A checker #FF00FF texel as drawn plain or graded: violet family. Data cloth, skin and grass never pass. */
	static boolean checkerLight(int r, int g, int b) {
		return b >= 110 && b - g >= 70 && r - g >= 20;
	}

	/** A checker #000000 texel as drawn plain or graded (BSL: about rgb(34,31,55)). */
	static boolean checkerDark(int r, int g, int b) {
		return Math.max(r, Math.max(g, b)) <= 80;
	}

	static boolean dataCloth(int r, int g, int b, float[] hsb) {
		Color.RGBtoHSB(r, g, b, hsb);
		float dh = Math.abs(hsb[0] - DATA_CLOTH_HUE);
		return hsb[1] >= MIN_SATURATION && Math.min(dh, 1f - dh) <= HUE_RANGE;
	}

	/** Counts over {@code crop}; garment = differs from the pixel {@code backgroundDx} to the side, same row. */
	static Leg leg(BufferedImage img, Rect crop, int backgroundDx) {
		int total = 0, garment = 0, cloth = 0, light = 0, dark = 0;
		float[] hsb = new float[3];
		for (int y = crop.y0(); y < crop.y1(); y++) {
			for (int x = crop.x0(); x < crop.x1(); x++) {
				int p = img.getRGB(x, y), q = img.getRGB(x + backgroundDx, y);
				int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
				total++;
				if (distance(rgb(p), rgb(q)) > BACKGROUND_TOLERANCE) garment++;
				if (dataCloth(r, g, b, hsb)) cloth++;
				if (checkerLight(r, g, b)) light++;
				else if (checkerDark(r, g, b)) dark++;
			}
		}
		return new Leg(total, garment, cloth, light, dark);
	}

	// ---- nametag glyph colours (NametagColoursClientTests)

	/** A pixel belongs to the tag when it changed by more than this (max channel) from the frame without it. */
	private static final int CHANGED = 12;
	/** Ignore changed regions smaller than this (a toast's corner, a stray particle). */
	private static final int MIN_PLATE_PIXELS = 400;
	/** Glyph pixels are at least this fraction of the way from the plate to the tag's far end (98th percentile). */
	private static final double GLYPH_FRACTION = 0.6;
	/** The chapter tag must be at least this many times nearer its target than its stock neighbour (chroma). */
	static final int MARGIN = 3;
	/** The two references must be at least this far apart in chroma or the shot cannot tell them apart. */
	static final double MIN_SPREAD = 20;

	/**
	 * The nametag's plate in {@code shot}: the pixels that changed from {@code base} (the same frame without the tag)
	 * form connected regions; the plate is the largest one that spans the window's centre column (the armour stand
	 * is straight ahead) and darkens what was behind it (it is a translucent dark box). Toasts that slide in between
	 * the two frames are other regions, off centre. Null when no such region.
	 */
	static Rect plate(BufferedImage base, BufferedImage shot, Rect window) {
		int w = window.x1() - window.x0(), h = window.y1() - window.y0(), cx = w / 2;
		boolean[] changed = new boolean[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				changed[y * w + x] = distance(rgb(base.getRGB(window.x0() + x, window.y0() + y)), rgb(shot.getRGB(window.x0() + x, window.y0() + y))) > CHANGED;
			}
		}
		boolean[] seen = new boolean[w * h];
		Rect best = null;
		int bestN = 0;
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		for (int start = 0; start < w * h; start++) {
			if (!changed[start] || seen[start]) continue;
			seen[start] = true;
			queue.add(start);
			int n = 0, x0 = w, y0 = h, x1 = -1, y1 = -1;
			long darker = 0;
			while (!queue.isEmpty()) {
				int i = queue.poll(), x = i % w, y = i / w;
				n++;
				x0 = Math.min(x0, x);
				y0 = Math.min(y0, y);
				x1 = Math.max(x1, x);
				y1 = Math.max(y1, y);
				int[] a = rgb(base.getRGB(window.x0() + x, window.y0() + y)), b = rgb(shot.getRGB(window.x0() + x, window.y0() + y));
				darker += (a[0] + a[1] + a[2]) - (b[0] + b[1] + b[2]);
				if (x > 0) visit(i - 1, changed, seen, queue);
				if (x < w - 1) visit(i + 1, changed, seen, queue);
				if (y > 0) visit(i - w, changed, seen, queue);
				if (y < h - 1) visit(i + w, changed, seen, queue);
			}
			if (n >= MIN_PLATE_PIXELS && x0 <= cx && cx <= x1 && darker > 0 && n > bestN) {
				bestN = n;
				best = new Rect(window.x0() + x0, window.y0() + y0, window.x0() + x1 + 1, window.y0() + y1 + 1);
			}
		}
		return best;
	}

	private static void visit(int j, boolean[] changed, boolean[] seen, ArrayDeque<Integer> queue) {
		if (changed[j] && !seen[j]) {
			seen[j] = true;
			queue.add(j);
		}
	}

	/**
	 * The glyph colour inside a plate: the plate's own colour is the per-channel median of the box (glyphs are a
	 * minority); glyph pixels are those at least {@link #GLYPH_FRACTION} of the way to the farthest (98th percentile)
	 * pixel from it, which drops the anti-aliased edges; their per-channel median is the colour. Null when nothing
	 * stands out.
	 */
	static int[] glyph(BufferedImage shot, Rect plate) {
		List<int[]> px = new ArrayList<>();
		for (int y = plate.y0(); y < plate.y1(); y++) {
			for (int x = plate.x0(); x < plate.x1(); x++) px.add(rgb(shot.getRGB(x, y)));
		}
		if (px.isEmpty()) return null;
		int[] mode = median(px);
		int[] d = px.stream().mapToInt(q -> distance(q, mode)).sorted().toArray();
		int far = d[d.length * 98 / 100];
		if (far <= CHANGED) return null;
		List<int[]> glyph = new ArrayList<>();
		for (int[] q : px) if (distance(q, mode) >= far * GLYPH_FRACTION) glyph.add(q);
		return median(glyph);
	}

	/**
	 * How far apart two colours are in hue and saturation, not brightness: the largest difference of their
	 * chromaticities r/(r+g+b), g/.., b/.., times 1000. BSL dims and tints the glyphs; this only sees which way they lean.
	 */
	static double chroma(int[] a, int[] b) {
		double sa = Math.max(1, a[0] + a[1] + a[2]), sb = Math.max(1, b[0] + b[1] + b[2]), m = 0;
		for (int c = 0; c < 3; c++) m = Math.max(m, Math.abs(a[c] / sa - b[c] / sb));
		return m * 1000;
	}

	/** The verdict on one case: the chapter tag, the target it should draw like and the stock colour it must not. */
	record Nametag(int[] chapter, int[] target, int[] stock) {
		double toTarget() {
			return chroma(chapter, target);
		}

		double toStock() {
			return chroma(chapter, stock);
		}

		double spread() {
			return chroma(target, stock);
		}

		/** Null when it passes, else why not. */
		String failure() {
			if (spread() < MIN_SPREAD) return "the references drew only " + fmt(spread()) + " apart (chroma): the shot cannot judge";
			if (toTarget() * MARGIN > toStock()) return "the chapter tag draws like its stock colour: " + fmt(toTarget()) + " from the target, " + fmt(toStock()) + " from stock";
			return null;
		}

		@Override
		public String toString() {
			return "glyph " + Arrays.toString(chapter) + ", target " + Arrays.toString(target) + " (" + fmt(toTarget()) + " off), stock "
					+ Arrays.toString(stock) + " (" + fmt(toStock()) + " off), spread " + fmt(spread());
		}
	}

	/** The glyph colour of the tag in {@code shot}; throws when no plate or no glyph is found. */
	static int[] tagColour(BufferedImage base, BufferedImage shot, Rect window, String what) {
		Rect plate = plate(base, shot, window);
		if (plate == null) throw new AssertionError("no nametag plate drew for " + what);
		int[] colour = glyph(shot, plate);
		if (colour == null) throw new AssertionError("no glyph pixels stand out from the plate for " + what + " (plate " + plate + ")");
		return colour;
	}

	static Nametag nametag(BufferedImage base, BufferedImage chapter, BufferedImage target, BufferedImage stock, Rect window) {
		return new Nametag(tagColour(base, chapter, window, "the chapter tag"), tagColour(base, target, window, "the target tag"),
				tagColour(base, stock, window, "the stock tag"));
	}

	// ---- helpers

	static int[] rgb(int p) {
		return new int[]{(p >> 16) & 255, (p >> 8) & 255, p & 255};
	}

	/** The largest channel difference. */
	static int distance(int[] a, int[] b) {
		return Math.max(Math.abs(a[0] - b[0]), Math.max(Math.abs(a[1] - b[1]), Math.abs(a[2] - b[2])));
	}

	private static int[] median(List<int[]> px) {
		int[] m = new int[3];
		int[] v = new int[px.size()];
		for (int c = 0; c < 3; c++) {
			for (int i = 0; i < v.length; i++) v[i] = px.get(i)[c];
			Arrays.sort(v);
			m[c] = v[v.length / 2];
		}
		return m;
	}

	private static String fmt(double d) {
		return String.format(java.util.Locale.ROOT, "%.1f", d);
	}
}
