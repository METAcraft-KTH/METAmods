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
            return List.of("patch/seat/" + placement.patch() + "_r", "patch/seat/" + placement.patch() + "_l");
        }
        return List.of("patch/" + placement.spot().id() + "/" + placement.patch());
    }

    public static String previewTexture(Piece piece) {
        return "patch/preview_" + piece.id;
    }

    public static String json(Chapter chapter, Piece piece, boolean nercabbad, List<Placement> placements) {
        JsonArray layers = new JsonArray();
        layers.add(layer(baseTexture(chapter, piece, nercabbad), false));
        for (Placement p : placements) {
            if (p.piece() != piece) throw new IllegalArgumentException(p + " is not on the " + piece);
            Patches.get(p.patch());
            for (String t : textures(p)) layers.add(layer(Ovvar.MOD_ID + ":" + t, false));
        }
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
    public static String packPath(Chapter chapter, Piece piece, boolean nercabbad, String combo) {
        return "assets/" + Ovvar.MOD_ID + "/equipment/" + metacraft.ovvar.content.Looks.assetPath(chapter, piece, nercabbad, combo) + ".json";
    }

    /** All (chapter, nercabbad) variants a piece's combination needs. */
    public static List<boolean[]> variants(Chapter chapter, Piece piece) {
        List<boolean[]> out = new ArrayList<>();
        out.add(new boolean[]{false});
        if (piece == Piece.BOTTOM && chapter.rollable) out.add(new boolean[]{true});
        return out;
    }
}
