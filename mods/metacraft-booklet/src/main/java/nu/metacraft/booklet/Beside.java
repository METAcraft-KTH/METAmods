package nu.metacraft.booklet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.pb4.booklet.api.TextUncenterer;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.body.PlainMessage;
import xyz.nucleoid.server.translations.api.LocalizationTarget;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Text on the left, a picture on the right — a layout a dialog body does not have, since bodies
 * stack. A page asks for it with an image under {@code beside/}:
 * <pre>
 *   ### Image: metacraft:beside/ovvar/hero The text that goes to the left of the picture.
 * </pre>
 *
 * <p>The picture is a font of our own ({@code assets/metacraft/font/beside.json}, made by
 * {@code tools/booklet_images.py}): cut into 16×9 UI px tiles, the height of a line of text, four
 * image pixels to a UI pixel. Each line of the body is then a line of the page's text, wrapped to
 * the room left of the picture and padded to exactly that width, a gap, and one row of tiles — only
 * ordinary, positive advances, so nothing depends on another font's spacing being right on the
 * client. Every line is the same width, so the dialog, which centres lines, keeps them flush.
 */
public final class Beside {
	private Beside() {}

	public static final String PREFIX = "beside/";
	private static final String INDEX = "/assets/metacraft/beside/index.json";
	private static final Style FONT = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("metacraft", "beside")))
			.withColor(0xFFFFFF).withShadowColor(0);
	/**
	 * The page body's width (Booklet's default); the text widget wraps at that less 2 × 4 px padding,
	 * and breaks the moment a line's running width passes it, so every line keeps SLACK in hand.
	 */
	private static final int WIDTH = 300, INSET = 8, GAP = 8, SLACK = 4;

	private record Picture(int width, List<String> rows) {}

	private static final Map<String, Picture> PICTURES = new HashMap<>();

	public static void init() {
		try (InputStream in = Beside.class.getResourceAsStream(INDEX)) {
			if (in == null) {
				MetacraftBooklet.LOGGER.warn("[{}] no {}: pictures beside text will be missing", MetacraftBooklet.MOD_ID, INDEX);
				return;
			}
			JsonObject index = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (var entry : index.entrySet()) {
				JsonObject p = entry.getValue().getAsJsonObject();
				List<String> rows = new ArrayList<>();
				p.getAsJsonArray("rows").forEach(r -> rows.add(r.getAsString()));
				PICTURES.put(entry.getKey(), new Picture(p.get("width").getAsInt(), rows));
			}
		} catch (IOException | RuntimeException e) {
			MetacraftBooklet.LOGGER.warn("[{}] cannot read {}: {}", MetacraftBooklet.MOD_ID, INDEX, e.toString());
		}
	}

	public static boolean wants(Identifier image) {
		return image.getPath().startsWith(PREFIX);
	}

	/** Whether a {@code beside/} picture exists (for the game tests). */
	public static boolean has(Identifier image) {
		return wants(image) && PICTURES.containsKey(image.getPath().substring(PREFIX.length()));
	}

	public static PlainMessage layout(Identifier id, Component text, PacketContext context) {
		Picture picture = PICTURES.get(id.getPath().substring(PREFIX.length()));
		int textWidth = WIDTH - INSET - SLACK - GAP - (picture == null ? 0 : picture.width());
		String language = LocalizationTarget.of(context).getLanguageCode();
		List<Component> lines = TextUncenterer.getLeftAligned(text, textWidth, language);
		if (picture == null) return new PlainMessage(CommonComponents.joinLines(lines), WIDTH);

		int count = Math.max(lines.size(), picture.rows().size());
		int textTop = (count - lines.size()) / 2, imageTop = (count - picture.rows().size()) / 2;
		List<Component> out = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			int t = i - textTop, r = i - imageTop;
			MutableComponent line = Component.empty();
			line.append(t >= 0 && t < lines.size() ? lines.get(t) : TextUncenterer.filler(textWidth));
			line.append(TextUncenterer.filler(GAP));
			line.append(r >= 0 && r < picture.rows().size()
					? Component.literal(picture.rows().get(r)).setStyle(FONT)
					: TextUncenterer.filler(picture.width()));
			out.add(line);
		}
		return new PlainMessage(CommonComponents.joinLines(out), WIDTH);
	}
}
