package metacraft.ovvar.pack;

import metacraft.ovvar.datagen.Tex;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A {@value #HEIGHT}-pixel-tall type face, drawn here rather than taken from anywhere, for the
 * notices and the stats readout the wardrobe screen bakes into its own glyphs. The vanilla font is
 * 8 px tall and about 6 px wide a character, which is how the v2 title ran past the edge of the
 * screen; at 3×5 a whole line of explanation fits inside one panel and the stats fit in the spare
 * header width beside the chapter's name.
 *
 * <p>One case only — small capitals read better than lowercase at this size, and a reader cannot
 * tell the difference in a 3 px glyph anyway — so {@link #render} folds case. Every character
 * advances {@value #ADVANCE} px (3 of art, 1 of gap) and a space {@value #SPACE}.
 */
public final class TinyType {
	private TinyType() {}

	public static final int HEIGHT = 5, ADVANCE = 4, SPACE = 2;
	/** Rows between two lines of a block. */
	public static final int LEADING = 2;

	private static final Map<Character, String[]> GLYPHS = new LinkedHashMap<>();

	private static void glyph(char c, String r0, String r1, String r2, String r3, String r4) {
		GLYPHS.put(c, new String[]{r0, r1, r2, r3, r4});
	}

	static {
		glyph('A', "###", "#.#", "###", "#.#", "#.#");
		glyph('B', "###", "#.#", "##.", "#.#", "###");
		glyph('C', "###", "#..", "#..", "#..", "###");
		glyph('D', "##.", "#.#", "#.#", "#.#", "##.");
		glyph('E', "###", "#..", "##.", "#..", "###");
		glyph('F', "###", "#..", "##.", "#..", "#..");
		glyph('G', "###", "#..", "#.#", "#.#", "###");
		glyph('H', "#.#", "#.#", "###", "#.#", "#.#");
		glyph('I', "###", ".#.", ".#.", ".#.", "###");
		glyph('J', "..#", "..#", "..#", "#.#", "###");
		glyph('K', "#.#", "#.#", "##.", "#.#", "#.#");
		glyph('L', "#..", "#..", "#..", "#..", "###");
		glyph('M', "#.#", "###", "###", "#.#", "#.#");
		glyph('N', "##.", "#.#", "#.#", "#.#", "#.#");
		glyph('O', "###", "#.#", "#.#", "#.#", "###");
		glyph('P', "###", "#.#", "###", "#..", "#..");
		glyph('Q', "###", "#.#", "#.#", "###", "..#");
		glyph('R', "###", "#.#", "###", "##.", "#.#");
		glyph('S', "###", "#..", "###", "..#", "###");
		glyph('T', "###", ".#.", ".#.", ".#.", ".#.");
		glyph('U', "#.#", "#.#", "#.#", "#.#", "###");
		glyph('V', "#.#", "#.#", "#.#", "#.#", ".#.");
		glyph('W', "#.#", "#.#", "###", "###", "#.#");
		glyph('X', "#.#", "#.#", ".#.", "#.#", "#.#");
		glyph('Y', "#.#", "#.#", "###", ".#.", ".#.");
		glyph('Z', "###", "..#", ".#.", "#..", "###");
		glyph('0', "###", "#.#", "#.#", "#.#", "###");
		glyph('1', ".#.", "##.", ".#.", ".#.", "###");
		glyph('2', "###", "..#", "###", "#..", "###");
		glyph('3', "###", "..#", ".##", "..#", "###");
		glyph('4', "#.#", "#.#", "###", "..#", "..#");
		glyph('5', "###", "#..", "###", "..#", "###");
		glyph('6', "###", "#..", "###", "#.#", "###");
		glyph('7', "###", "..#", "..#", "..#", "..#");
		glyph('8', "###", "#.#", "###", "#.#", "###");
		glyph('9', "###", "#.#", "###", "..#", "..#");
		glyph('.', "...", "...", "...", "...", ".#.");
		glyph(',', "...", "...", "...", ".#.", "#..");
		glyph('-', "...", "...", "###", "...", "...");
		glyph('·', "...", "...", ".#.", "...", "...");
		glyph(':', "...", ".#.", "...", ".#.", "...");
		glyph('!', ".#.", ".#.", ".#.", "...", ".#.");
		glyph('?', "###", "..#", ".##", "...", ".#.");
		glyph('\'', ".#.", ".#.", "...", "...", "...");
		glyph('/', "..#", "..#", ".#.", "#..", "#..");
	}

	/** Can this text be drawn? (Every character of it has a glyph.) */
	public static boolean drawable(String text) {
		for (char c : text.toUpperCase(Locale.ROOT).toCharArray()) {
			if (c != ' ' && !GLYPHS.containsKey(c)) return false;
		}
		return true;
	}

	/** How wide {@code text} comes out: every character's advance, less the gap after the last one. */
	public static int width(String text) {
		int w = 0;
		for (char c : text.toCharArray()) w += c == ' ' ? SPACE : ADVANCE;
		return Math.max(0, w - 1);
	}

	/** One line, in {@code argb}, exactly {@link #width} × {@value #HEIGHT}. */
	public static Tex render(String text, int argb) {
		String upper = text.toUpperCase(Locale.ROOT);
		int[] px = new int[Math.max(1, width(text)) * HEIGHT];
		int stride = Math.max(1, width(text));
		int x = 0;
		for (char c : upper.toCharArray()) {
			if (c == ' ') {
				x += SPACE;
				continue;
			}
			String[] rows = GLYPHS.get(c);
			if (rows == null) throw new IllegalArgumentException("no " + HEIGHT + "px glyph for '" + c + "' in \"" + text + "\"");
			for (int y = 0; y < HEIGHT; y++) {
				for (int i = 0; i < 3; i++) {
					if (rows[y].charAt(i) == '#' && x + i < stride) px[y * stride + x + i] = argb;
				}
			}
			x += ADVANCE;
		}
		return Tex.of(stride, HEIGHT, px);
	}

	/**
	 * A block of lines, each centred, in {@code argb} over a 1 px shadow in {@code shadow} offset
	 * down and right — the notices sit on cloth whose colour is the chapter's, so the ink needs
	 * something behind it whichever chapter is open.
	 */
	public static Tex block(List<String> lines, int width, int argb, int shadow) {
		int height = lines.size() * HEIGHT + (lines.size() - 1) * LEADING + 1;
		Tex out = Tex.blank(width, height);
		for (int i = 0; i < lines.size(); i++) {
			Tex line = render(lines.get(i), argb);
			Tex shade = render(lines.get(i), shadow);
			int x = (width - line.width) / 2, y = i * (HEIGHT + LEADING);
			out = out.composite(Tex.blank(width, height).blit(shade, 0, 0, shade.width, HEIGHT, Math.min(width - shade.width, x + 1), y + 1));
			out = out.composite(Tex.blank(width, height).blit(line, 0, 0, line.width, HEIGHT, x, y));
		}
		return out;
	}

	/** How tall {@link #block} comes out for {@code lines} lines. */
	public static int blockHeight(int lines) {
		return lines * HEIGHT + (lines - 1) * LEADING + 1;
	}
}
