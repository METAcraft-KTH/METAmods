package metacraft.ovvar.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import metacraft.ovvar.Ovvar;
import metacraft.ovvar.datagen.Tex;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The {@code ovvar:wardrobe} font's own furniture: the spaces that put a glyph anywhere on the
 * screen, and the small static glyphs the wardrobe draws over its background — the highlight under
 * the tab being shown, the empty-state notices, and the tiny stats readout in the header.
 *
 * <p>Everything here is drawn in the container's title, which is one line of text the client
 * renders at (8, 6) inside the container, so every position is arithmetic:
 * <ul>
 *   <li><b>across:</b> a {@code space} provider with advances ±1, ±2, … ±128 ({@link #move}) walks
 *	   the cursor to any x and back again, so a glyph can be placed per player — under whichever
 *	   tab is showing, or right-aligned against the header's right margin — which a fixed pair of
 *	   advances could not;</li>
 *   <li><b>down:</b> a bitmap glyph's top lands at {@code textY + 7 − ascent}, so a glyph whose art
 *	   should start at container y {@code top} declares {@link #ascent(int) ascent(top)} —
 *	   negative for anything below the header, which is most of the screen.</li>
 * </ul>
 *
 * <p>The slot grid these positions are quoted in: an item's icon fills the 16×16 at
 * ({@value #ITEM_X} + 18·col, {@value #ITEM_Y} + 18·row) and the 18×18 cell around it — the ring
 * the vanilla slot frame would be drawn on, which the wardrobe's own background hides — starts one
 * pixel up and to the left. Anything drawn on that ring is a frame; anything drawn inside it
 * overlaps the icon, which is exactly what the v2 screen got wrong.
 */
public final class WardrobeFont {
	private WardrobeFont() {}

	/** Where the client draws a container's title, inside the container. */
	public static final int TITLE_X = 8, TITLE_Y = 6;
	/** The chest's slot grid: the icon's own 16×16, the 18 px pitch, and the cell ring one px outside it. */
	public static final int ITEM_X = 8, ITEM_Y = 18, PITCH = 18, ICON = 16;
	public static final int CELL = PITCH;

	/** The x of a slot's 18×18 cell: its frame ring, one px left of the icon. */
	public static int cellX(int col) {
		return ITEM_X - 1 + PITCH * col;
	}

	/** The y of a slot's 18×18 cell. */
	public static int cellY(int row) {
		return ITEM_Y - 1 + PITCH * row;
	}

	/** The ascent a bitmap glyph needs for its art to start at container y {@code top}. */
	public static int ascent(int top) {
		return TITLE_Y + 7 - top;
	}

	// ---- spaces: ±1, ±2, … ±128, so any move up to 255 px is a handful of codepoints

	private static final char SPACE_FIRST = '\uE800';
	private static final int SPACE_BITS = 8;

	/** Codepoint → advance, for the font's {@code space} provider. */
	public static Map<Character, Integer> spaceAdvances() {
		Map<Character, Integer> out = new LinkedHashMap<>();
		for (int bit = 0; bit < SPACE_BITS; bit++) {
			out.put((char) (SPACE_FIRST + bit), 1 << bit);
			out.put((char) (SPACE_FIRST + SPACE_BITS + bit), -(1 << bit));
		}
		return out;
	}

	/** Spaces that move the cursor {@code dx} px, either way. */
	public static String move(int dx) {
		int size = Math.abs(dx);
		if (size >= 1 << SPACE_BITS) throw new IllegalArgumentException("a move of " + dx + " px is too far for the wardrobe font");
		StringBuilder out = new StringBuilder();
		for (int bit = 0; bit < SPACE_BITS; bit++) {
			if ((size >> bit & 1) != 0) out.append((char) (SPACE_FIRST + (dx < 0 ? SPACE_BITS : 0) + bit));
		}
		return out.toString();
	}

	// ---- the static glyphs

	/**
	 * One bitmap glyph of the font: its art, its size, where it goes on the screen — the container
	 * x it is normally drawn at and the container y its top sits at, which fixes its ascent — and
	 * the codepoint it is drawn with. {@code art} is called once per pack build. A glyph whose
	 * place is the player's own (the tab highlight, the stats) is drawn with {@link #at} instead,
	 * and its {@code x} is only where it would go by default.
	 */
	public record Glyph(String name, int x, int top, int width, int height, char codepoint, Supplier<Tex> art) {
		/** What the client adds to the cursor after drawing it: the texture's width plus one. */
		public int advance() {
			return width + 1;
		}

		public String texturePath() {
			return "assets/" + Ovvar.MOD_ID + "/textures/wardrobe/" + name + ".png";
		}

		public String textureRef() {
			return Ovvar.MOD_ID + ":wardrobe/" + name + ".png";
		}
	}

	private static final List<Glyph> GLYPHS = new ArrayList<>();
	/** The static glyphs' codepoints; the previews have {@code \\uE000}… and the spaces {@code \\uE800}…. */
	private static char next = '\uE100';

	/**
	 * Registers a glyph whose art starts at container y {@code top}.
	 *
	 * <p>The declared height is raised to the ascent where it has to be, and the art padded below
	 * with transparent rows to match: <b>a bitmap provider with an ascent greater than its height
	 * is refused, and the client then drops the whole font</b> — every glyph of it, so the screen
	 * falls back to a plain chest with a title of missing-glyph boxes. A glyph high up the screen
	 * has a large positive ascent ({@code 13 − top}) and the stats readout, 5 px tall at the top of
	 * the header, asked for 6; padding below moves nothing, since the art still begins at the
	 * canvas's own top. ({@link metacraft.ovvar.sewing.SewingFont} pads for the same reason.)
	 */
	static Glyph glyph(String name, int x, int top, int width, int height, Supplier<Tex> art) {
		if (next >= SPACE_FIRST) throw new IllegalStateException("too many wardrobe glyphs");
		int declared = Math.max(height, ascent(top));
		Glyph glyph = new Glyph(name, x, top, width, declared, next++, () -> art.get().padBottom(declared));
		GLYPHS.add(glyph);
		return glyph;
	}

	public static List<Glyph> glyphs() {
		return List.copyOf(GLYPHS);
	}

	/**
	 * The lighter box under the tab being shown. The background is one glyph per chapter and the
	 * tabs are in owned-chapter order, which is per player, so the highlight cannot be baked into
	 * the background: it is its own cell-sized glyph, placed with spaces at the active tab's slot.
	 */
	public static final Glyph ACTIVE_TAB = glyph("active_tab", cellX(0), cellY(0), CELL, CELL, WardrobeFont::activeTabArt);

	private static Tex activeTabArt() {
		int[] px = new int[CELL * CELL];
		for (int y = 0; y < CELL; y++) {
			for (int x = 0; x < CELL; x++) {
				boolean ring = x == 0 || y == 0 || x == CELL - 1 || y == CELL - 1;
				// The ring: solid white where the background's own stitching is dashed, so the
				// showing tab reads as a raised button. Inside: a wash of white over the cloth.
				px[y * CELL + x] = ring ? 0xFFFFFFFF : 0x38FFFFFF;
			}
		}
		return Tex.of(CELL, CELL, px);
	}

	// ---- the empty states, drawn across the panel they are about

	/** The patch collection: rows 1-4, cols 0-4. The preview: rows 1-4, cols 5-8. */
	public static final int PATCH_PANEL_X = cellX(0), PREVIEW_PANEL_X = cellX(5), PANEL_Y = cellY(1);
	public static final int PATCH_PANEL_W = 5 * PITCH, PREVIEW_PANEL_W = 4 * PITCH, PANEL_H = 4 * PITCH;

	/** Ink for a notice on the cream canvas pocket, and for one on the darker cloth behind the preview. */
	private static final int DARK_INK = 0xFF2B1F19, LIGHT_INK = 0xFFF6EBD2;
	private static final int DARK_SHADOW = 0x99F6EBD2, LIGHT_SHADOW = 0x99000000;

	private static final List<String> NO_PATCHES_LINES = List.of("NO PATCHES YET", "EARN THEM AT", "CHAPTER EVENTS");
	private static final List<String> NOTHING_SEWN_LINES = List.of("NOTHING SEWN YET", "TAKE A PATCH TO", "A SEWING STAND");

	/**
	 * An empty stash, said across the whole pocket rather than by one paper item in the middle of
	 * it: baked pixel text in the glyph layer, so the slots stay empty and there is nothing to
	 * hover, click or mistake for a patch.
	 */
	public static final Glyph NO_PATCHES = notice("no_patches", PATCH_PANEL_W, PATCH_PANEL_X, NO_PATCHES_LINES, DARK_INK, DARK_SHADOW);
	/**
	 * And, over the same pocket, what {@code /ovvar look} puts there instead: somebody else's
	 * wardrobe is their ovve and their design, never their stash, so those slots stay empty and
	 * say why.
	 */
	public static final Glyph LOOK_ONLY = notice("look_only", PATCH_PANEL_W, PATCH_PANEL_X,
			List.of("A LOOK AT ANOTHER", "PLAYER'S OVVE", "THEIR STASH IS THEIRS"), DARK_INK, DARK_SHADOW);

	/** And a half with nothing sewn on it, across the bare garment on the doll behind. */
	public static final Glyph NOTHING_SEWN = notice("nothing_sewn", PREVIEW_PANEL_W, PREVIEW_PANEL_X, NOTHING_SEWN_LINES, LIGHT_INK, LIGHT_SHADOW);

	private static Glyph notice(String name, int width, int x, List<String> lines, int ink, int shadow) {
		int height = TinyType.blockHeight(lines.size());
		return glyph(name, x, PANEL_Y + (PANEL_H - height) / 2, width, height, () -> TinyType.block(lines, width, ink, shadow));
	}

	// ---- the stats readout: tiny numerals in the header, beside the chapter's name

	/** The right margin of the container's header, and the row the stats sit on. */
	public static final int HEADER_RIGHT = WardrobeArt.WIDTH - ITEM_X, STATS_TOP = 7;
	/**
	 * A conservative width for a character of the vanilla font, which draws the title's own text:
	 * the widest is 6 px with its gap, so the stats never run into the chapter's name even though
	 * the server cannot measure that text the way the client does.
	 */
	public static final int TITLE_CHAR = 6;
	/** The gap the stats keep from the title's text, and between two of their own glyphs. */
	private static final int MARGIN = 4, GAP = 1;

	private static final int STATS_INK = 0xFFFFFFFF, STATS_SHADOW = 0xAA000000;

	private static final Glyph[] DIGITS = new Glyph[10];
	// The labels carry a trailing space of their own, so "earned" and its number do not run together.
	public static final Glyph EARNED = word("earned", "earned "), SEWN = word("sewn", "sewn "), STASH = word("stash", "stash ");
	public static final Glyph DOT = word("dot", "·");

	static {
		for (int digit = 0; digit < 10; digit++) {
			String text = String.valueOf(digit);
			DIGITS[digit] = word("d" + digit, text);
		}
	}

	private static Glyph word(String name, String text) {
		int width = TinyType.width(text);
		return glyph("stats/" + name, HEADER_RIGHT, STATS_TOP, width, TinyType.HEIGHT,
				() -> TinyType.block(List.of(text), width, STATS_INK, STATS_SHADOW).crop(0, 0, width, TinyType.HEIGHT));
	}

	public static Glyph digit(int value) {
		return DIGITS[value];
	}

	/** The glyphs of a number, most significant first. */
	public static List<Glyph> number(int value) {
		List<Glyph> out = new ArrayList<>();
		for (char c : String.valueOf(Math.max(0, value)).toCharArray()) out.add(digit(c - '0'));
		return out;
	}

	/** How wide a row of glyphs comes out, laid side by side with {@value #GAP} px between them. */
	public static int width(List<Glyph> row) {
		int w = 0;
		for (Glyph glyph : row) w += glyph.width() + GAP;
		return Math.max(0, w - GAP);
	}

	/** A row of glyphs laid left to right from {@code x}; the cursor ends where it started. */
	public static String row(int x, List<Glyph> glyphs) {
		StringBuilder out = new StringBuilder();
		int at = x;
		for (Glyph glyph : glyphs) {
			out.append(at(glyph, at));
			at += glyph.width() + GAP;
		}
		return out.toString();
	}

	/**
	 * {@code earned 32 · sewn 11 · stash 0} as pixel text, right-aligned against the header's right
	 * margin, with as many of the three counts as fit to the right of a title {@code titleChars}
	 * characters long. The v2 screen put all this in the title's own text and it ran off the edge
	 * of the screen ("text going over the limit"); at 3×5 it fits beside the chapter's name, and
	 * what does not fit is still in the help item's lore, in full.
	 */
	public static Component stats(int earned, int sewn, int stash, int titleChars) {
		List<Glyph> shown = statsRow(earned, sewn, stash, titleChars);
		if (shown.isEmpty()) return Component.empty();
		return Component.literal(row(HEADER_RIGHT - width(shown), shown)).withStyle(WardrobeArt.STYLE);
	}

	/**
	 * The glyphs {@link #stats} would draw, in order: as many of the three counts as fit, separated
	 * by a dot. Empty when the chapter's name leaves no room at all — the help item's lore has them
	 * in full whatever happens.
	 */
	public static List<Glyph> statsRow(int earned, int sewn, int stash, int titleChars) {
		List<List<Glyph>> groups = List.of(labelled(EARNED, earned), labelled(SEWN, sewn), labelled(STASH, stash));
		int room = HEADER_RIGHT - (TITLE_X + titleChars * TITLE_CHAR) - MARGIN;
		List<Glyph> shown = new ArrayList<>();
		for (List<Glyph> group : groups) {
			List<Glyph> next = new ArrayList<>(shown);
			if (!next.isEmpty()) next.add(DOT);
			next.addAll(group);
			if (width(next) > room) break;
			shown = next;
		}
		return shown;
	}

	/** Where {@link #statsRow} puts each of its glyphs: right-aligned against the header's margin. */
	public static int statsX(List<Glyph> shown, int index) {
		int x = HEADER_RIGHT - width(shown);
		for (int i = 0; i < index; i++) x += shown.get(i).width() + GAP;
		return x;
	}

	private static List<Glyph> labelled(Glyph label, int value) {
		List<Glyph> out = new ArrayList<>();
		out.add(label);
		out.addAll(number(value));
		return out;
	}

	// ---- the pocket's page counter

	/**
	 * "page 2/3", when the stash has more kinds of patch than the pocket's twenty slots. It goes in
	 * the action row's spare middle (cols 5-6), which is the nearest free pixels to the pocket's
	 * bottom-right corner: the pocket itself is slots edge to edge, with nothing but 1 px gutters
	 * between them, and 5 px of type has to go somewhere it does not sit on an icon.
	 */
	public static final int PAGE_X = cellX(5) + 2, PAGE_TOP = cellY(5) + 6;

	private static final Glyph[] PAGE_DIGITS = new Glyph[10];
	public static final Glyph PAGE_LABEL = pageWord("page", "page "), PAGE_OF = pageWord("of", "/");

	static {
		for (int digit = 0; digit < 10; digit++) PAGE_DIGITS[digit] = pageWord("p" + digit, String.valueOf(digit));
	}

	private static Glyph pageWord(String name, String text) {
		int width = TinyType.width(text);
		return glyph("page/" + name, PAGE_X, PAGE_TOP, width, TinyType.HEIGHT,
				() -> TinyType.block(List.of(text), width, STATS_INK, STATS_SHADOW).crop(0, 0, width, TinyType.HEIGHT));
	}

	/** {@code page N/M}, or nothing at all when it all fits on one page. */
	public static Component pages(int page, int of) {
		List<Glyph> row = pagesRow(page, of);
		if (row.isEmpty()) return Component.empty();
		return Component.literal(row(PAGE_X, row)).withStyle(WardrobeArt.STYLE);
	}

	/** The glyphs {@link #pages} draws, in order; empty when it all fits on one page. */
	public static List<Glyph> pagesRow(int page, int of) {
		List<Glyph> row = new ArrayList<>();
		if (of <= 1) return row;
		row.add(PAGE_LABEL);
		for (char c : String.valueOf(page + 1).toCharArray()) row.add(PAGE_DIGITS[c - '0']);
		row.add(PAGE_OF);
		for (char c : String.valueOf(of).toCharArray()) row.add(PAGE_DIGITS[c - '0']);
		return row;
	}

	/** Where {@link #row} puts the glyph at {@code index}, laid out from {@code from}. */
	public static int rowX(List<Glyph> row, int index, int from) {
		int x = from;
		for (int i = 0; i < index; i++) x += row.get(i).width() + GAP;
		return x;
	}

	// ---- drawing

	/** {@code [spaces to x][the glyph][spaces back]}: the cursor ends exactly where it started. */
	public static String at(Glyph glyph, int x) {
		int dx = x - TITLE_X;
		return move(dx) + glyph.codepoint() + move(-(dx + glyph.advance()));
	}

	/** The glyph at its own place on the screen. */
	public static String at(Glyph glyph) {
		return at(glyph, glyph.x());
	}

	/** A run of glyphs, each at its own place, as a styled component ready to append to a title. */
	public static Component drawn(Glyph... glyphs) {
		StringBuilder out = new StringBuilder();
		for (Glyph glyph : glyphs) out.append(at(glyph));
		return Component.literal(out.toString()).withStyle(WardrobeArt.STYLE);
	}

	/** One glyph somewhere other than its own place (the tab highlight, which follows the player). */
	public static Component drawnAt(Glyph glyph, int x) {
		return Component.literal(at(glyph, x)).withStyle(WardrobeArt.STYLE);
	}

	// ---- the pack

	/**
	 * Writes every glyph's PNG, recording each in {@code files} (path → its size) so that what the
	 * font JSON points at can be checked against what the build actually put in the pack. Called
	 * from {@link WardrobeArt}'s pack-build hook.
	 */
	static void build(ResourcePackBuilder builder, Map<String, int[]> files) {
		for (Glyph glyph : GLYPHS) {
			Tex art = glyph.art().get();
			if (art.width != glyph.width() || art.height != glyph.height()) {
				throw new IllegalStateException(glyph.name() + " is " + art.width + "x" + art.height + ", the font says " + glyph.width() + "x" + glyph.height());
			}
			builder.addData(glyph.texturePath(), art.reachingRightEdge().png());
			files.put(glyph.texturePath(), new int[]{art.width, art.height});
		}
		Ovvar.LOGGER.info("[ovvar] wardrobe font: {} static glyph(s)", GLYPHS.size());
	}

	static List<JsonObject> providers() {
		List<JsonObject> out = new ArrayList<>();
		for (Glyph glyph : GLYPHS) {
			JsonObject bitmap = new JsonObject();
			bitmap.addProperty("type", "bitmap");
			bitmap.addProperty("file", glyph.textureRef());
			bitmap.addProperty("ascent", ascent(glyph.top()));
			bitmap.addProperty("height", glyph.height());
			JsonArray chars = new JsonArray();
			chars.add(String.valueOf(glyph.codepoint()));
			bitmap.add("chars", chars);
			out.add(bitmap);
		}
		return out;
	}
}
