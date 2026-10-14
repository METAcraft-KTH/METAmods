package metacraft.ovvar.pack;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import metacraft.ovvar.Ovvar;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Piece;
import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.Spot;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.ArrayList;
import java.util.List;

/**
 * The equipment definition of one half with a set of placements: the chapter's base texture,
 * one static texture per placement, then the dyeable preview layer. Datagen writes the empty
 * combination for every chapter; {@link Combos} writes the rest into the pack at runtime. The
 * texture names here are what datagen generates — keep the two in step.
 */
public final class EquipmentJson {
	private EquipmentJson() {}

	public static String baseTexture(Chapter chapter, Piece piece, boolean nercabbad) {
		return Ovvar.MOD_ID + ":" + chapter.id + "/" + piece.id + (nercabbad ? "_nercabbad" : "");
	}

	/** Texture names (without namespace and layer folder) a placement is drawn with: one, or two for the seat. */
	public static List<String> textures(Placement placement) {
		if (placement.spot() == Spot.SEAT) {
			return List.of("patch/seat/" + placement.patch().id() + "_r", "patch/seat/" + placement.patch().id() + "_l");
		}
		return List.of("patch/" + placement.spot().id() + "/" + placement.patch().id());
	}

	public static String previewTexture(Piece piece) {
		return "patch/preview_" + piece.id;
	}

	/** The legs' preview texture for the boots pass (the outer model: its own layer texel). */
	public static final String FEET_PREVIEW = "patch/preview_feet";

	/** The feet slot's asset over an ovve: a material's own humanoid layer (or none) plus our preview layer. */
	public static ResourceKey<EquipmentAsset> feetAsset(String material) {
		return ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, "feet/" + material));
	}

	/** The chest slot's asset over an ovve worn under a real chestplate: the ovve top and its dyeable
	 *  preview, then the chestplate's own humanoid layer on top. A vanilla chestplate's arms and neck
	 *  are largely transparent, so the sleeves and collar of the ovve show through underneath. */
	public static ResourceKey<EquipmentAsset> chestAsset(Chapter chapter, String material) {
		return ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, "chest/" + chapter.id + "/" + material));
	}

	public static String chestJson(Chapter chapter, String material) {
		JsonArray layers = new JsonArray();
		layers.add(layer(baseTexture(chapter, Piece.TOP, false), false));       // the ovve top fabric, underneath
		layers.add(layer(Ovvar.MOD_ID + ":" + previewTexture(Piece.TOP), true)); // its instant patches, in the dye colour
		layers.add(layer("minecraft:" + material, false));                       // the chestplate on top
		JsonObject byType = new JsonObject();
		byType.add(Piece.TOP.layer, layers);
		JsonObject root = new JsonObject();
		root.add("layers", byType);
		return new GsonBuilder().setPrettyPrinting().create().toJson(root);
	}

	public static String feetJson(String material) {
		JsonArray layers = new JsonArray();
		if (!material.equals(metacraft.ovvar.content.OvveFeet.NONE)) layers.add(layer("minecraft:" + material, false));
		layers.add(layer(Ovvar.MOD_ID + ":" + FEET_PREVIEW, true));
		JsonObject byType = new JsonObject();
		byType.add("humanoid", layers);
		JsonObject root = new JsonObject();
		root.add("layers", byType);
		return new GsonBuilder().setPrettyPrinting().create().toJson(root);
	}

	/**
	 * The opaque layers of a half with a set of placements, bottom first, as {@code ovvar:}-prefixed
	 * texture names: the chapter's cloth, then one texture per placement (two for the seat). The
	 * dyeable preview layer, which only the shader draws, is not one of them. This is the whole of
	 * "where a patch goes on the cloth"; {@link #json} turns it into the equipment definition a
	 * client draws and {@link WardrobePreview} composites the same list into a picture server-side,
	 * so the two can never disagree about a placement's texture or the order they stack in.
	 *
	 * <p>The order is {@link Spot#layer}'s — cells overlap (the big back cell under the two back-top
	 * ones), and a client draws these layers in the order they are listed, so a patch meant to be on
	 * top has to be later in the list whatever order the placements arrived in.
	 */
	public static List<String> layerTextures(Chapter chapter, Piece piece, boolean nercabbad, List<Placement> placements) {
		List<String> out = new ArrayList<>();
		out.add(baseTexture(chapter, piece, nercabbad));
		for (Placement p : Spot.stacked(placements)) {
			if (p.piece() != piece) throw new IllegalArgumentException(p + " is not on the " + piece);
			for (String t : textures(p)) out.add(Ovvar.MOD_ID + ":" + t);
		}
		return out;
	}

	public static String json(Chapter chapter, Piece piece, boolean nercabbad, List<Placement> placements) {
		JsonArray layers = new JsonArray();
		for (String texture : layerTextures(chapter, piece, nercabbad, placements)) layers.add(layer(texture, false));
		layers.add(layer(Ovvar.MOD_ID + ":" + previewTexture(piece), true));
		JsonObject byType = new JsonObject();
		byType.add(piece.layer, layers);
		JsonObject root = new JsonObject();
		root.add("layers", byType);
		return new GsonBuilder().setPrettyPrinting().create().toJson(root);
	}

	private static JsonObject layer(String texture, boolean dyeable) {
		JsonObject o = new JsonObject();
		o.addProperty("texture", texture);
		if (dyeable) o.add("dyeable", new JsonObject());
		return o;
	}

	/** The pack path of a combination's definition. */
	public static String packPath(Chapter chapter, Piece piece, boolean nercabbad, Combos.Combo combo) {
		return "assets/" + Ovvar.MOD_ID + "/equipment/" + metacraft.ovvar.content.Looks.assetPath(chapter, piece, nercabbad, combo.key()) + ".json";
	}

	/** All (chapter, nercabbad) variants a piece's combination needs: none for a half the garment lacks (a frack's legs). */
	public static List<boolean[]> variants(Chapter chapter, Piece piece) {
		List<boolean[]> out = new ArrayList<>();
		if (!chapter.pieces().contains(piece)) return out;
		out.add(new boolean[]{false});
		if (piece == Piece.BOTTOM && chapter.rollable) out.add(new boolean[]{true});
		return out;
	}
}
