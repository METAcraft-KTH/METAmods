package se.metacraft.ovvar.combo;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The names and the equipment definition of a patch combination, shared by ovvar (the server writes them
 * into the combination pack, {@code pack/ComboPack}) and by the ovvar-client mod (the client builds the
 * same definition from the asset's name alone, so the server need not push it the pack). Plain Java and
 * Gson, no Minecraft or ovvar classes: this directory is compiled into both jars (the client's copy moved
 * to se.metacraft.ovvarclient.combo, so the two never shadow each other on one classpath), so the two can
 * never build a definition differently.
 *
 * <p>The client cannot ask ovvar's {@code Spot} enum which cell lies over which, so the cell table is
 * written out here ({@link #CELLS}); a game test checks it against {@code Spot} cell for cell, and the
 * byte-for-byte parity test checks {@link #jsonFor} against what ComboPack writes. Anything that changes
 * what a name means or the definition built from it is a new {@link #PROTOCOL}: a server and a client
 * on different protocols do not handshake, and that client is pushed the pack as before.
 */
public final class ComboAssets {
	private ComboAssets() {}

	public static final String NAMESPACE = "ovvar";
	/**
	 * The asset grammar and recipe version. Bump it, and the fingerprint in ComboAssetsTest, whenever a
	 * name could mean something else or build different JSON (a cell added, a texture renamed).
	 */
	public static final int PROTOCOL = 2;
	/**
	 * The client's handshake: it registers a configuration-phase receiver on this channel (nothing is ever
	 * sent on it), and Fabric tells the server which channels a client can receive before the join packs go
	 * out. The protocol is in the name, so a server on another protocol simply does not see it.
	 */
	public static final String CHANNEL_PATH = "draws_combos/v" + PROTOCOL;
	public static final String NERCABBAD = "_nercabbad";
	public static final String SEAT = "seat";
	/** The first folder of a chestplate wrap's asset: {@code chest/<chapter>/<material>[/<combo>]}. */
	public static final String CHEST = "chest";

	/** The two halves: id to the equipment JSON's layer key (ovvar's {@code Piece}). */
	public static final Map<String, String> PIECE_LAYERS;

	/** A cell: the half it is on and where its patch sits in the stack ({@code Spot#layer}). */
	public record Cell(String piece, int layer) {}

	/** Every cell by id, in {@code Spot}'s order. */
	public static final Map<String, Cell> CELLS;

	static {
		Map<String, String> pieces = new LinkedHashMap<>();
		pieces.put("top", "humanoid");
		pieces.put("bottom", "humanoid_leggings");
		PIECE_LAYERS = Collections.unmodifiableMap(pieces);
		Map<String, Cell> cells = new LinkedHashMap<>();
		for (String id : List.of("front_top_left", "front_top_right", "front_low_left", "front_low_right")) cells.put(id, new Cell("top", 0));
		// the two back-top cells lie on the big back cell, so they are drawn over it
		cells.put("back_top_left", new Cell("top", 1));
		cells.put("back_top_right", new Cell("top", 1));
		cells.put("back_big", new Cell("top", 0));
		for (String face : List.of("out", "front", "back")) {
			for (String side : List.of("r", "l")) {
				cells.put("sleeve_" + face + "_top_" + side, new Cell("top", 0));
				cells.put("sleeve_" + face + "_mid_" + side, new Cell("top", 0));
			}
		}
		for (String face : List.of("out", "front", "back")) {
			for (String side : List.of("r", "l")) {
				// the legs' back-top cells lie under the seat's rectangle (never sewn with it, but stacked as if)
				cells.put("leg_" + face + "_top_" + side, new Cell("bottom", face.equals("back") ? 1 : 0));
				cells.put("leg_" + face + "_mid_" + side, new Cell("bottom", 0));
			}
		}
		cells.put(SEAT, new Cell("bottom", 0));
		CELLS = Collections.unmodifiableMap(cells);
	}

	// ---- names

	/** An asset's path: {@code <chapter>/<piece>[_nercabbad][/<combo>]}. */
	public static String assetPath(String chapter, String piece, boolean nercabbad, String combo) {
		return chapter + "/" + piece + (nercabbad ? NERCABBAD : "") + (combo.isEmpty() ? "" : "/" + combo);
	}

	/**
	 * A chestplate wrap's path: {@code chest/<chapter>/<material>}, the ovve top under a vanilla chestplate
	 * (in the Polymer pack, drawing the instant patches only), or with the top's combination,
	 * {@code chest/<chapter>/<material>/<combo>}: the same wrap with every patch, which ovvar-client builds from
	 * the name and the combination pack holds for some materials.
	 */
	public static String chestPath(String chapter, String material, String combo) {
		return CHEST + "/" + chapter + "/" + material + (combo.isEmpty() ? "" : "/" + combo);
	}

	/** The chapter's cloth for a half, namespaced. */
	public static String baseTexture(String chapter, String piece, boolean nercabbad) {
		return NAMESPACE + ":" + chapter + "/" + piece + (nercabbad ? NERCABBAD : "");
	}

	/** Texture names (no namespace) a patch on a cell is drawn with: one, or two for the seat. */
	public static List<String> textures(String cell, String patch) {
		if (cell.equals(SEAT)) return List.of("patch/seat/" + patch + "_r", "patch/seat/" + patch + "_l");
		return List.of("patch/" + cell + "/" + patch);
	}

	/** The half's dyeable preview layer (no namespace), which only the shader draws. */
	public static String previewTexture(String piece) {
		return "patch/preview_" + piece;
	}

	/**
	 * A combination key split back into its {@code cell.patch} keys. Patch ids may hold a '-' themselves
	 * (tmeit-marshal) but never a '.', and cell ids neither, so a piece without a '.' is the rest of the
	 * patch id before it.
	 */
	public static List<String> placementKeys(String key) {
		List<String> out = new ArrayList<>();
		for (String part : key.split("-")) {
			if (!out.isEmpty() && part.indexOf('.') < 0) out.set(out.size() - 1, out.getLast() + "-" + part);
			else out.add(part);
		}
		return out;
	}

	// ---- the definition

	/**
	 * The equipment definition of a half: its opaque layers bottom first (namespaced textures, the cloth
	 * then the patches), then the dyeable preview layer, under the half's layer key. Pretty-printed by
	 * Gson; ComboPack writes exactly these bytes.
	 */
	public static String json(String layerKey, List<String> opaqueTextures, String previewTexture) {
		JsonArray layers = new JsonArray();
		for (String texture : opaqueTextures) layers.add(layer(texture, false));
		layers.add(layer(previewTexture, true));
		JsonObject byType = new JsonObject();
		byType.add(layerKey, layers);
		JsonObject root = new JsonObject();
		root.add("layers", byType);
		return new GsonBuilder().setPrettyPrinting().create().toJson(root);
	}

	/**
	 * A chestplate wrap's definition: the top's opaque layers bottom first (namespaced: the cloth, then its
	 * patches), the top's dyeable preview, then the vanilla chestplate's own humanoid layer over them all.
	 * A chestplate's arms and neck are largely transparent, so the sleeves and collar show through.
	 */
	public static String chestJson(String material, List<String> opaqueTextures) {
		JsonArray layers = new JsonArray();
		for (String texture : opaqueTextures) layers.add(layer(texture, false));
		layers.add(layer(NAMESPACE + ":" + previewTexture("top"), true));
		layers.add(layer("minecraft:" + material, false));
		JsonObject byType = new JsonObject();
		byType.add(PIECE_LAYERS.get("top"), layers);
		JsonObject root = new JsonObject();
		root.add("layers", byType);
		return new GsonBuilder().setPrettyPrinting().create().toJson(root);
	}

	public static JsonObject layer(String texture, boolean dyeable) {
		JsonObject o = new JsonObject();
		o.addProperty("texture", texture);
		if (dyeable) o.add("dyeable", new JsonObject());
		return o;
	}

	/**
	 * The definition of an {@code ovvar:} combination asset from its path alone, as the server's pack
	 * holds it: {@code <chapter>/<piece>[_nercabbad]/<combo>}, the combo the canonical (sorted) key the
	 * server names assets with; or a chestplate wrap with the top's combination,
	 * {@code chest/<chapter>/<material>/<combo>}. Empty for any other path (the base garments, the wrap
	 * without patches and the cuffs are in the Polymer pack) or one this build cannot read: an unknown
	 * cell, a cell on the other half, a roll-down on the top. The chapter and material are taken as given;
	 * the server only names real ones.
	 */
	public static Optional<String> jsonFor(String path) {
		String[] parts = path.split("/", -1);
		for (String part : parts) if (part.isEmpty()) return Optional.empty();
		if (parts.length == 4 && parts[0].equals(CHEST)) {
			return opaqueTextures(parts[1], "top", false, parts[3]).map(textures -> chestJson(parts[2], textures));
		}
		if (parts.length != 3) return Optional.empty();
		boolean nercabbad = parts[1].endsWith(NERCABBAD);
		String piece = nercabbad ? parts[1].substring(0, parts[1].length() - NERCABBAD.length()) : parts[1];
		String layerKey = PIECE_LAYERS.get(piece);
		if (layerKey == null || (nercabbad && !piece.equals("bottom"))) return Optional.empty();
		return opaqueTextures(parts[0], piece, nercabbad, parts[2]).map(textures -> json(layerKey, textures, NAMESPACE + ":" + previewTexture(piece)));
	}

	/** A half's opaque layers from a combination's key, namespaced and bottom first; empty if a placement does not read. */
	private static Optional<List<String>> opaqueTextures(String chapter, String piece, boolean nercabbad, String combo) {
		List<String> cells = new ArrayList<>();
		List<String> patches = new ArrayList<>();
		for (String key : placementKeys(combo)) {
			int dot = key.indexOf('.');
			if (dot <= 0 || dot == key.length() - 1) return Optional.empty();
			String cell = key.substring(0, dot);
			Cell known = CELLS.get(cell);
			if (known == null || !known.piece().equals(piece)) return Optional.empty();
			cells.add(cell);
			patches.add(key.substring(dot + 1));
		}
		// Bottom first: by layer, and in the key's (sorted) order within one, as Spot.stacked has them.
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < cells.size(); i++) order.add(i);
		order.sort((a, b) -> Integer.compare(CELLS.get(cells.get(a)).layer(), CELLS.get(cells.get(b)).layer()));
		List<String> textures = new ArrayList<>();
		textures.add(baseTexture(chapter, piece, nercabbad));
		for (int i : order) for (String t : textures(cells.get(i), patches.get(i))) textures.add(NAMESPACE + ":" + t);
		return Optional.of(textures);
	}
}
