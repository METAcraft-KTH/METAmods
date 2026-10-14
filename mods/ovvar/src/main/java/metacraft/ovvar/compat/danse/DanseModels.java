package metacraft.ovvar.compat.danse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.tomalbrc.danse.api.BodyLayerModels;
import de.tomalbrc.danse.util.MinecraftSkinParser.BodyPart;
import metacraft.ovvar.Ovvar;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Piece;
import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.Spot;
import metacraft.ovvar.pack.EquipmentJson;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The item models a gesturing stand-in wears an ovve with (see {@link DanseLayers}): one item
 * definition per piece and part, {@code ovvar:danse/<piece>/<part>}, a composite of
 * <ol>
 *   <li>the base cloth, chosen by {@code custom_model_data} string 0 (the base texture's name,
 *       {@code data/top}, {@code data/bottom_nercabbad}, …);</li>
 *   <li>per cell that lands on this part, bottom layer first, the patch chosen by string
 *       {@code 1 + Spot.cells(piece).indexOf(cell)} (its id, or empty for none).</li>
 * </ol>
 * Every model is a {@link BodyLayerModels#shell} over a crop of ovvar's own equipment texture — the
 * same 256×128 layer a real client's armour renderer draws, cut at build time to the one part's
 * rectangle ({@link BodyLayerModels#region}) and put under {@code item/}, where the vanilla items
 * atlas already looks. Full layer sheets would stitch hundreds of mostly transparent 256×128
 * sprites into every player's atlas; the crops are a sixth of that, and a crop with nothing on it
 * is left out along with its model. Left limbs take the base cloth's asymmetric art from the mirror
 * strip ({@link Spot#MIRROR_SHIFT} texels up), where datagen keeps it for the shader; patch textures
 * for left cells already sit pre-mirrored on the right limb's rectangle, which the shell reads mirrored.
 */
public final class DanseModels {
	private DanseModels() {}

	private static final Gson GSON = new GsonBuilder().create();

	private static final List<BodyPart> TOP_PARTS = List.of(BodyPart.BODY, BodyPart.RIGHT_ARM, BodyPart.LEFT_ARM);
	private static final List<BodyPart> BOTTOM_PARTS = List.of(BodyPart.BODY, BodyPart.RIGHT_LEG, BodyPart.LEFT_LEG);

	/** The top is a humanoid layer (Danse's outer pass), the bottom a leggings layer (inner). */
	public static boolean draws(Piece piece, BodyPart part) {
		return parts(piece).contains(part);
	}

	public static Identifier itemModel(Piece piece, BodyPart part) {
		return Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, "danse/" + piece.id + "/" + part.getName());
	}

	/** {@code data/top}: the base texture's name without namespace or layer folder. */
	public static String baseKey(Chapter chapter, Piece piece, boolean nercabbad) {
		return EquipmentJson.baseTexture(chapter, piece, nercabbad).substring(Ovvar.MOD_ID.length() + 1);
	}

	public static List<String> strings(Chapter chapter, Piece piece, boolean nercabbad, List<Placement> placements) {
		List<Spot> cells = Spot.cells(piece);
		List<String> out = new ArrayList<>(1 + cells.size());
		out.add(baseKey(chapter, piece, nercabbad));
		for (int i = 0; i < cells.size(); i++) out.add("");
		for (Placement placement : placements) {
			int at = cells.indexOf(placement.spot());
			if (at >= 0) out.set(1 + at, placement.patch().id());
		}
		return out;
	}

	/** Everything this layer adds to the pack. */
	public static void write(BiConsumer<String, byte[]> out) {
		for (Piece piece : Piece.values()) {
			for (BodyPart part : parts(piece)) writePart(piece, part, out);
		}
	}

	private static void writePart(Piece piece, BodyPart part, BiConsumer<String, byte[]> out) {
		String prefix = "danse/" + piece.id + "/" + part.getName() + "/";
		JsonArray models = new JsonArray();

		JsonArray baseCases = new JsonArray();
		for (Chapter chapter : Chapter.values()) {
			for (boolean[] variant : EquipmentJson.variants(chapter, piece)) {
				String key = baseKey(chapter, piece, variant[0]);
				String model = prefix + "base/" + key;
				if (shell(piece, part, key, model, isLeft(part) ? -Spot.MIRROR_SHIFT : 0, 0f, out)) {
					baseCases.add(when(key, model));
				}
			}
		}
		if (!baseCases.isEmpty()) models.add(select(0, baseCases));

		List<Spot> cells = Spot.cells(piece);
		List<Spot> here = cells.stream().filter(spot -> lands(spot, part))
				.sorted(Comparator.comparingInt(Spot::layer)).toList();
		for (Spot spot : here) {
			JsonArray cases = new JsonArray();
			for (Patches.Patch patch : Patches.all()) {
				if (!patch.fits(spot)) continue;
				String texture = textureFor(new Placement(spot, patch), part);
				String model = prefix + spot.id() + "/" + patch.id();
				if (shell(piece, part, texture, model, 0, 0.01f * (1 + spot.layer()), out)) {
					cases.add(when(patch.id(), model));
				}
			}
			if (!cases.isEmpty()) models.add(select(1 + cells.indexOf(spot), cases));
		}

		JsonObject composite = new JsonObject();
		composite.addProperty("type", "minecraft:composite");
		composite.add("models", models);
		JsonObject root = new JsonObject();
		root.add("model", composite);
		out.accept("assets/" + Ovvar.MOD_ID + "/items/" + itemModel(piece, part).getPath() + ".json", bytes(GSON.toJson(root)));
	}

	/**
	 * One shell: the layer texture cropped to the part's rectangle, written next to its model. False,
	 * and nothing written, when datagen made no such texture (a retired patch, say) or the crop is
	 * empty (nothing of this layer on this part).
	 */
	private static boolean shell(Piece piece, BodyPart part, String texture, String model, int vShift, float inflate,
			BiConsumer<String, byte[]> out) {
		BufferedImage layer = layer(piece, texture);
		if (layer == null) return false;
		int[] region = BodyLayerModels.region(part, vShift);
		int scale = layer.getWidth() / 64;
		BufferedImage crop = layer.getSubimage(region[0] * scale, region[1] * scale, region[2] * scale, region[3] * scale);
		if (empty(crop)) return false;
		// named after the layer it was cut from, under the part it was cut for
		String path = "item/" + model.substring(0, model.indexOf('/', "danse/".length() + piece.id.length() + 1) + 1) + texture;
		out.accept("assets/" + Ovvar.MOD_ID + "/textures/" + path + ".png", png(crop));
		Identifier id = Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, path);
		out.accept(modelPath(model), bytes(BodyLayerModels.shell(part, id, vShift, inflate, region)));
		return true;
	}

	/** A layer texture datagen made, off our own classpath, or null. */
	private static @Nullable BufferedImage layer(Piece piece, String texture) {
		String path = "/assets/" + Ovvar.MOD_ID + "/textures/entity/equipment/" + piece.layer + "/" + texture + ".png";
		try (InputStream in = DanseModels.class.getResourceAsStream(path)) {
			return in == null ? null : ImageIO.read(in);
		} catch (IOException e) {
			throw new UncheckedIOException("cannot read " + path, e);
		}
	}

	private static boolean empty(BufferedImage image) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if ((image.getRGB(x, y) >>> 24) != 0) return false;
			}
		}
		return true;
	}

	private static byte[] png(BufferedImage image) {
		BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		copy.getGraphics().drawImage(image, 0, 0, null);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try {
			ImageIO.write(copy, "png", bytes);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return bytes.toByteArray();
	}

	/** Does this cell land on this part? The seat is one patch cut across both legs. */
	private static boolean lands(Spot spot, BodyPart part) {
		return switch (spot.side) {
			case BODY -> part == BodyPart.BODY;
			case RIGHT -> part == BodyPart.RIGHT_ARM || part == BodyPart.RIGHT_LEG;
			case LEFT -> part == BodyPart.LEFT_ARM || part == BodyPart.LEFT_LEG;
			case SEAT -> part == BodyPart.RIGHT_LEG || part == BodyPart.LEFT_LEG;
		};
	}

	/** The placement's texture on this part; the seat has one per leg, right first. */
	private static String textureFor(Placement placement, BodyPart part) {
		List<String> textures = EquipmentJson.textures(placement);
		return textures.size() == 1 ? textures.getFirst() : textures.get(part == BodyPart.RIGHT_LEG ? 0 : 1);
	}

	private static List<BodyPart> parts(Piece piece) {
		return piece == Piece.TOP ? TOP_PARTS : BOTTOM_PARTS;
	}

	private static boolean isLeft(BodyPart part) {
		return part == BodyPart.LEFT_ARM || part == BodyPart.LEFT_LEG;
	}

	private static String modelPath(String model) {
		return "assets/" + Ovvar.MOD_ID + "/models/" + model + ".json";
	}

	private static JsonObject select(int index, JsonArray cases) {
		JsonObject o = new JsonObject();
		o.addProperty("type", "minecraft:select");
		o.addProperty("property", "minecraft:custom_model_data");
		o.addProperty("index", index);
		o.add("cases", cases);
		JsonObject fallback = new JsonObject();
		fallback.addProperty("type", "minecraft:empty");
		o.add("fallback", fallback);
		return o;
	}

	private static JsonObject when(String value, String model) {
		JsonObject m = new JsonObject();
		m.addProperty("type", "minecraft:model");
		m.addProperty("model", Ovvar.MOD_ID + ":" + model);
		JsonObject c = new JsonObject();
		c.addProperty("when", value);
		c.add("model", m);
		return c;
	}

	private static byte[] bytes(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}
}
