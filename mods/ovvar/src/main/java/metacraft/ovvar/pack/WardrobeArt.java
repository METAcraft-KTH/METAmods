package metacraft.ovvar.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import metacraft.ovvar.Ovvar;
import metacraft.ovvar.content.Chapter;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The wardrobe screen's art: one 176×126 background per chapter (exactly the GENERIC_9x6 chest's
 * own six 18px rows below its header, so nothing opaque paints over the player's inventory), tinted from the single
 * hand-drawn greyscale template ({@code art/ovvar/wardrobe_template.png}, checked in, drawn once
 * by {@code tools/wardrobe_template.py}), and the {@code ovvar:wardrobe} font that draws them as
 * the container's title — the same negative-space-plus-bitmap-glyph trick better-pets uses for
 * {@code pet_gui} ({@link metacraft.ovvar.sewing.SewingFont} does the equivalent for the sewing
 * dialog): a {@code space} provider moves the cursor back to the corner, one {@code bitmap} glyph
 * per chapter draws the whole background, then a matching space moves the cursor back so ordinary
 * text (the stats strip) can follow.
 *
 * <p>Tinting: {@code output = round(luminance/255 * chapterColour)} per channel, except pixels at
 * or above {@link #WHITE_THRESHOLD} (the overlock stitching in the template) which are kept pure
 * white instead of being multiplied down to a tint of the chapter colour — the stitching is meant
 * to read the same on every chapter's background.
 */
public final class WardrobeArt {
	private WardrobeArt() {}

	public static final Identifier FONT = Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, "wardrobe");
	private static final String TEMPLATE_PATH = "art/" + Ovvar.MOD_ID + "/wardrobe_template.png";
	private static final String TEXTURE_DIR = "assets/" + Ovvar.MOD_ID + "/textures/wardrobe/";
	private static final String FONT_PATH = "assets/" + Ovvar.MOD_ID + "/font/wardrobe.json";

	public static final int WIDTH = 176, HEIGHT = 126;
	/** A template pixel at or above this luminance is stitching, not cloth, and stays white. */
	private static final int WHITE_THRESHOLD = 250;

	/** The tab-row corner reset, and the chapter glyphs, share the "abc" pattern: −8, the glyph, then back. */
	private static final int CORNER = -8;
	private static final int RESET = -(CORNER + WIDTH + 1);   // -(-8 + 176 + 1) = -169, the better-pets formula
	/** better-pets' {@code pet_gui} ascent for a full-background glyph anchored to the container's top-left corner. */
	private static final int ASCENT = 13;

	private static final char CORNER_CHAR = 'a', RESET_CHAR = 'c';
	private static final Map<Chapter, Character> CHAPTER_CHAR = new LinkedHashMap<>();

	static {
		char c = 'A';
		for (Chapter chapter : Chapter.values()) CHAPTER_CHAR.put(chapter, c++);
	}

	/** The codepoint the chapter's background is drawn at, in the wardrobe font (tests, {@link #backgroundGlyph}). */
	public static char chapterChar(Chapter chapter) {
		return CHAPTER_CHAR.get(chapter);
	}

	public static void init() {
		// Touching the class registers its glyphs (the bare ovvar from four sides, and every patch
		// on every cell it can be seen on) with WardrobeFont, well before any pack is built. Not
		// during datagen: those glyphs are composited out of the very equipment textures datagen is
		// about to write, so the class cannot be initialised before the run that writes them.
		if (!Ovvar.DATAGEN) WardrobePreview.init();
		PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(WardrobeArt::build);
	}

	private static void build(ResourcePackBuilder builder) {
		BufferedImage template = readTemplate();
		Map<String, int[]> files = new LinkedHashMap<>();
		for (Chapter chapter : Chapter.values()) {
			builder.addData(TEXTURE_DIR + chapter.id + ".png", png(tint(template, colour(chapter))));
			files.put(TEXTURE_DIR + chapter.id + ".png", new int[]{WIDTH, HEIGHT});
		}
		// The font's own glyphs first — its furniture and the paper dolls — since the font JSON
		// below lists a provider for each of them.
		WardrobeFont.build(builder, files);
		written = Map.copyOf(files);
		builder.addStringData(FONT_PATH, fontJson().toString());
		Ovvar.LOGGER.info("[ovvar] wardrobe art: {} background(s), font {}", Chapter.values().length, FONT);
	}

	/** Every texture this build put in the pack, by pack path, with its size — what the font may point at. */
	private static volatile Map<String, int[]> written = Map.of();

	public static Map<String, int[]> packFiles() {
		return written;
	}

	/** The pack path a font provider's {@code file} refers to: {@code ovvar:wardrobe/x.png}. */
	public static String texturePath(String file) {
		return "assets/" + file.replace(":", "/textures/");
	}

	public static BufferedImage readTemplate() {
		try (InputStream in = WardrobeArt.class.getResourceAsStream("/" + TEMPLATE_PATH)) {
			if (in == null) throw new IOException("missing " + TEMPLATE_PATH);
			BufferedImage img = ImageIO.read(in);
			if (img.getWidth() != WIDTH || img.getHeight() != HEIGHT) {
				throw new IllegalStateException(TEMPLATE_PATH + " is " + img.getWidth() + "x" + img.getHeight() + ", wanted " + WIDTH + "x" + HEIGHT);
			}
			return img;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * The chapter's cloth colour: {@code #BD3754} data, {@code #8A57BD} IT, {@code #769BB0} IT
	 * kisel, {@code #1B1B1B} media dark; the PolymITer variants reuse their base chapter's colour.
	 */
	public static int colour(Chapter chapter) {
		return switch (chapter) {
			case DATA, DATA_POLYMITER -> 0xBD3754;
			case IT, IT_POLYMITER -> 0x8A57BD;
			case IT_KISEL -> 0x769BB0;
			case MEDIA -> 0x1B1B1B;
		};
	}

	/** Luminance × colour, per channel; a template pixel that is stitching (near-white) is kept white. */
	public static BufferedImage tint(BufferedImage template, int colour) {
		int w = template.getWidth(), h = template.getHeight();
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		int r = (colour >> 16) & 0xFF, g = (colour >> 8) & 0xFF, b = colour & 0xFF;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int argb = template.getRGB(x, y);
				int a = (argb >>> 24) & 0xFF;
				int grey = argb & 0xFF;   // template is greyscale: R = G = B
				int px;
				if (grey >= WHITE_THRESHOLD) {
					px = 0xFFFFFF;
				} else {
					double l = grey / 255.0;
					px = (clamp(l * r) << 16) | (clamp(l * g) << 8) | clamp(l * b);
				}
				out.setRGB(x, y, (a << 24) | px);
			}
		}
		return out;
	}

	private static int clamp(double v) {
		return Math.max(0, Math.min(255, Math.round((float) v)));
	}

	private static byte[] png(BufferedImage image) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			ImageIO.write(image, "png", out);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out.toByteArray();
	}

	/**
	 * {@code assets/ovvar/font/wardrobe.json}: one {@code space} provider, one {@code bitmap} per
	 * chapter background, then one per glyph {@link WardrobeFont} holds — its own furniture and
	 * {@link WardrobePreview}'s paper dolls.
	 */
	public static JsonObject fontJson() {
		JsonObject root = new JsonObject();
		JsonArray providers = new JsonArray();

		JsonObject space = new JsonObject();
		space.addProperty("type", "space");
		JsonObject advances = new JsonObject();
		advances.addProperty(String.valueOf(CORNER_CHAR), CORNER);
		advances.addProperty(String.valueOf(RESET_CHAR), RESET);
		// And ±1 … ±128, which is how everything else — the paper doll, each patch on it, the
		// notices, the tab highlight, the stats — is put where it goes (WardrobeFont.move).
		WardrobeFont.spaceAdvances().forEach((c, advance) -> advances.addProperty(String.valueOf(c), advance));
		space.add("advances", advances);
		providers.add(space);

		for (Chapter chapter : Chapter.values()) {
			JsonObject bitmap = new JsonObject();
			bitmap.addProperty("type", "bitmap");
			bitmap.addProperty("file", Ovvar.MOD_ID + ":wardrobe/" + chapter.id + ".png");
			bitmap.addProperty("ascent", ASCENT);
			bitmap.addProperty("height", HEIGHT);
			JsonArray chars = new JsonArray();
			chars.add(String.valueOf(CHAPTER_CHAR.get(chapter)));
			bitmap.add("chars", chars);
			providers.add(bitmap);
		}
		for (JsonObject glyph : WardrobeFont.providers()) providers.add(glyph);

		root.add("providers", providers);
		return root;
	}

	public static final Style STYLE = Style.EMPTY.withFont(new FontDescription.Resource(FONT)).withColor(ChatFormatting.WHITE);

	/**
	 * {@code [space to −8][glyph for chapter][space back]}: draws the chapter's background with
	 * the cursor left exactly where it started, ready for whatever ordinary-font text follows
	 * (the stats strip, appended by the caller — see {@code WardrobeGui.title}).
	 */
	public static Component backgroundGlyph(Chapter chapter) {
		String text = "" + CORNER_CHAR + CHAPTER_CHAR.get(chapter) + RESET_CHAR;
		return Component.literal(text).withStyle(STYLE);
	}
}
