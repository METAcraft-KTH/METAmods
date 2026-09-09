package metacraft.moredyes.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import metacraft.moredyes.MoreDyes;
import metacraft.moredyes.color.ModColor;
import metacraft.moredyes.color.ModColors;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

import static metacraft.moredyes.datagen.J.arr;
import static metacraft.moredyes.datagen.J.itemDef;
import static metacraft.moredyes.datagen.J.nums;
import static metacraft.moredyes.datagen.J.obj;
import static metacraft.moredyes.datagen.J.sub;

/**
 * Every colour-derived asset and data file, generated from {@code colors.json} by
 * {@code ./gradlew runDatagen} into {@code src/main/generated} (standard Fabric data generation):
 * recoloured vanilla textures, block/item models, item definitions, lang, loot tables, recipes,
 * and the vanilla tags our blocks join. The registered content ({@link metacraft.moredyes.content.Family})
 * and the FAMILIES table below must describe the same set of blocks.
 *
 * Tables use {@code {c}} as the colour-id placeholder and are substituted per colour.
 */
public final class GeneratedAssets implements DataProvider {
    private static final String MOD = MoreDyes.MOD_ID;
    /** Item displays render block models at "fixed" scale; the templates have none, so pin 0.5 (× the display's 2). */
    private static final JsonObject FIXED_HALF = obj("fixed", obj("scale", nums(0.5, 0.5, 0.5)));

    private final Path assets, data;
    private final List<CompletableFuture<?>> writes = new ArrayList<>();
    private CachedOutput out;

    public GeneratedAssets(FabricPackOutput output) {
        this.assets = output.getOutputFolder(PackOutput.Target.RESOURCE_PACK).resolve(MOD);
        this.data = output.getOutputFolder(PackOutput.Target.DATA_PACK);
    }

    @Override
    public String getName() {
        return "More Dyes colour-derived assets";
    }

    private static String t(String name) {
        return MOD + ":block/" + name;
    }

    // ---------------------------------------------------------------- tables

    /** Textures to recolour: destination (with {c}) -> vanilla source under textures/. */
    private static final Map<String, String> TEXTURES = new LinkedHashMap<>();
    static {
        TEXTURES.put("block/{c}_wool", "block/white_wool");
        TEXTURES.put("block/{c}_concrete", "block/white_concrete");
        TEXTURES.put("block/{c}_concrete_powder", "block/white_concrete_powder");
        TEXTURES.put("block/{c}_terracotta", "block/white_terracotta");
        TEXTURES.put("block/{c}_glazed_terracotta", "block/white_glazed_terracotta");
        TEXTURES.put("block/{c}_candle", "block/white_candle");
        TEXTURES.put("block/{c}_candle_lit", "block/white_candle_lit");
        TEXTURES.put("item/{c}_dye", "item/white_dye");
        TEXTURES.put("item/{c}_candle", "item/white_candle");
        // sheep wool overlay (64x32 entity texture); under block/ so it lands in the block atlas
        TEXTURES.put("block/sheep/{c}_wool", "entity/sheep/sheep_wool");
        // beds are plain block models in 26.x: seven faces per colour
        for (String f : List.of("head_east", "head_up", "head_west", "foot_east", "foot_south", "foot_up", "foot_west")) {
            TEXTURES.put("block/{c}_bed_" + f, "block/white_bed_" + f);
        }
        // shulker box: entity texture for the item's special renderer, block-atlas copy for the placed box's displays
        TEXTURES.put("entity/shulker/{c}", "entity/shulker/shulker_white");
        TEXTURES.put("block/shulker/{c}", "entity/shulker/shulker_white");
        // stained glass keeps vanilla's partial alpha: since 26.x the client picks the render layer per sprite
        TEXTURES.put("block/{c}_stained_glass", "block/white_stained_glass");
        TEXTURES.put("block/{c}_stained_glass_pane_top", "block/white_stained_glass_pane_top");
        TEXTURES.put("item/{c}_bundle", "item/white_bundle");
        TEXTURES.put("item/{c}_bundle_open_front", "item/white_bundle_open_front");
        TEXTURES.put("item/{c}_bundle_open_back", "item/white_bundle_open_back");
    }

    /** Block models: name (with {c}) -> model. */
    private static final Map<String, JsonObject> MODELS = new LinkedHashMap<>();
    /** Item models (flat sprites and the sheep parts): name (with {c}) -> model. */
    private static final Map<String, JsonObject> ITEM_MODELS = new LinkedHashMap<>();
    /** Colour-independent item models, emitted once. */
    private static final Map<String, JsonObject> SHARED_ITEM_MODELS = new LinkedHashMap<>();
    /** Item definitions that only exist as ITEM_MODEL targets for display entities: id (with {c}) -> model id. */
    private static final Map<String, String> DISPLAY_ITEM_DEFS = new LinkedHashMap<>();
    private static final Map<String, String> SHARED_ITEM_DEFS = new LinkedHashMap<>();

    private static JsonObject cubeAll(String tex) {
        return obj("parent", "minecraft:block/cube_all", "textures", obj("all", t(tex)));
    }

    static {
        MODELS.put("{c}_wool", cubeAll("{c}_wool"));
        MODELS.put("{c}_concrete", cubeAll("{c}_concrete"));
        MODELS.put("{c}_concrete_powder", cubeAll("{c}_concrete_powder"));
        MODELS.put("{c}_terracotta", cubeAll("{c}_terracotta"));
        MODELS.put("{c}_glazed_terracotta", obj("parent", "minecraft:block/template_glazed_terracotta",
                "textures", obj("pattern", t("{c}_glazed_terracotta"))));
        MODELS.put("{c}_carpet", obj("parent", "minecraft:block/carpet", "textures", obj("wool", t("{c}_wool"))));
        String[][] candles = {{"one_candle", "template_candle"}, {"two_candles", "template_two_candles"},
                {"three_candles", "template_three_candles"}, {"four_candles", "template_four_candles"}};
        for (String[] c : candles) {
            for (String lit : new String[]{"", "_lit"}) {
                MODELS.put("{c}_candle_" + c[0] + lit, obj("parent", "minecraft:block/" + c[1], "display", FIXED_HALF,
                        "textures", obj("all", t("{c}_candle" + lit), "particle", t("{c}_candle" + lit))));
            }
        }
        for (String mat : new String[]{"wool", "concrete"}) {
            JsonObject tex = obj("bottom", t("{c}_" + mat), "top", t("{c}_" + mat), "side", t("{c}_" + mat));
            MODELS.put("{c}_" + mat + "_stairs", obj("parent", "minecraft:block/stairs", "display", FIXED_HALF, "textures", tex));
            MODELS.put("{c}_" + mat + "_stairs_inner", obj("parent", "minecraft:block/inner_stairs", "display", FIXED_HALF, "textures", tex));
            MODELS.put("{c}_" + mat + "_stairs_outer", obj("parent", "minecraft:block/outer_stairs", "display", FIXED_HALF, "textures", tex));
            MODELS.put("{c}_" + mat + "_slab", obj("parent", "minecraft:block/slab", "display", FIXED_HALF, "textures", tex));
            MODELS.put("{c}_" + mat + "_slab_top", obj("parent", "minecraft:block/slab_top", "display", FIXED_HALF, "textures", tex));
        }
        MODELS.put("{c}_stained_glass", obj("parent", "minecraft:block/cube_all",
                "textures", obj("all", obj("force_translucent", true, "sprite", t("{c}_stained_glass")))));
        MODELS.putAll(paneModels());
        MODELS.put("{c}_bed_head", obj("parent", "minecraft:block/template_bed_head", "textures",
                obj("east", t("{c}_bed_head_east"), "up", t("{c}_bed_head_up"), "west", t("{c}_bed_head_west"))));
        MODELS.put("{c}_bed_foot", obj("parent", "minecraft:block/template_bed_foot", "textures",
                obj("east", t("{c}_bed_foot_east"), "south", t("{c}_bed_foot_south"), "up", t("{c}_bed_foot_up"), "west", t("{c}_bed_foot_west"))));
        // Shulker box for the display entity: vanilla ShulkerModel's base (16×8×16 at texOffs 0,28) and
        // lid (16×12×16 at texOffs 0,0) as two block models; the lid is a separate element so it can slide.
        JsonObject shulkerTex = obj("t", t("shulker/{c}"), "particle", t("shulker/{c}"));
        MODELS.put("{c}_shulker_box_base", obj("textures", shulkerTex, "display", FIXED_HALF,
                "elements", twoSidedBox(new int[]{0, 0, 0}, new int[]{16, 8, 16}, 0, 28, 16, 8, 16, 64, 64, "#t")));
        MODELS.put("{c}_shulker_box_lid", obj("textures", shulkerTex, "display", FIXED_HALF,
                "elements", twoSidedBox(new int[]{0, 4, 0}, new int[]{16, 16, 16}, 0, 0, 16, 12, 16, 64, 64, "#t")));

        ITEM_MODELS.put("{c}_dye", generated(MOD + ":item/{c}_dye"));
        ITEM_MODELS.put("{c}_candle", generated(MOD + ":item/{c}_candle"));
        ITEM_MODELS.put("{c}_stained_glass_pane", generated(t("{c}_stained_glass")));
        ITEM_MODELS.put("{c}_bundle", generated(MOD + ":item/{c}_bundle"));
        ITEM_MODELS.put("{c}_bundle_open_front", obj("parent", "minecraft:item/template_bundle_open_front",
                "textures", obj("layer0", MOD + ":item/{c}_bundle_open_front")));
        ITEM_MODELS.put("{c}_bundle_open_back", obj("parent", "minecraft:item/template_bundle_open_back",
                "textures", obj("layer0", MOD + ":item/{c}_bundle_open_back")));

        // Sheep rig: SheepFurModel wool (head 0.6, body 1.75, legs 0.5 inflation) and SheepModel body
        // boxes; the rig rotates the body 90° about X itself. "_hurt" variants carry vanilla's hurt
        // overlay baked into the texture (display entities have no hurt flash of their own).
        // part -> {wool box, wool inflate, wool uv, body box, body uv}
        Object[][] sheep = {
                {"head", new double[]{-3, -4, -4, 6, 6, 6}, 0.6, new int[]{0, 0}, new double[]{-3, -4, -6, 6, 6, 8}, new int[]{0, 0}},
                {"body", new double[]{-4, -10, -7, 8, 16, 6}, 1.75, new int[]{28, 8}, new double[]{-4, -10, -7, 8, 16, 6}, new int[]{28, 8}},
                {"leg", new double[]{-2, 0, -2, 4, 6, 4}, 0.5, new int[]{0, 16}, new double[]{-2, 0, -2, 4, 12, 4}, new int[]{0, 16}},
        };
        for (Object[] s : sheep) {
            String part = (String) s[0];
            for (String h : new String[]{"", "_hurt"}) {
                ITEM_MODELS.put("sheep/{c}_" + part + h, sheepPart((double[]) s[1], (double) s[2], (int[]) s[3], t("sheep/{c}_wool" + h)));
                ITEM_MODELS.put("sheep/{c}_sheared_" + part + h, sheepPart((double[]) s[4], 0, (int[]) s[5], t("sheep/{c}_body_sheared" + h)));
                SHARED_ITEM_MODELS.put("sheep/body_" + part + h, sheepPart((double[]) s[4], 0, (int[]) s[5], t("sheep/body" + h)));
                DISPLAY_ITEM_DEFS.put("sheep/{c}_" + part + h, MOD + ":item/sheep/{c}_" + part + h);
                DISPLAY_ITEM_DEFS.put("sheep/{c}_sheared_" + part + h, MOD + ":item/sheep/{c}_sheared_" + part + h);
                SHARED_ITEM_DEFS.put("sheep/body_" + part + h, MOD + ":item/sheep/body_" + part + h);
            }
        }
        for (String mat : new String[]{"wool", "concrete"}) {
            for (String suffix : new String[]{"_stairs_inner", "_stairs_outer", "_slab_top"}) {
                DISPLAY_ITEM_DEFS.put("{c}_" + mat + suffix, t("{c}_" + mat + suffix));
            }
        }
        for (String[] c : candles) {
            for (String lit : new String[]{"", "_lit"}) {
                DISPLAY_ITEM_DEFS.put("{c}_candle_" + c[0] + lit, t("{c}_candle_" + c[0] + lit));
            }
        }
        for (String name : paneModels().keySet()) DISPLAY_ITEM_DEFS.put(name, t(name));
        DISPLAY_ITEM_DEFS.put("{c}_shulker_box_base", t("{c}_shulker_box_base"));
        DISPLAY_ITEM_DEFS.put("{c}_shulker_box_lid", t("{c}_shulker_box_lid"));
    }

    private static JsonObject generated(String layer0) {
        return obj("parent", "minecraft:item/generated", "textures", obj("layer0", layer0));
    }

    /** Block families: id suffix -> {display-name suffix, item-definition model (null = written specially), loot kind}. */
    private static final String[][] FAMILIES = {
            {"wool", "Wool", t("{c}_wool"), "self"},
            {"carpet", "Carpet", t("{c}_carpet"), "self"},
            {"concrete", "Concrete", t("{c}_concrete"), "self"},
            {"concrete_powder", "Concrete Powder", t("{c}_concrete_powder"), "self"},
            {"terracotta", "Terracotta", t("{c}_terracotta"), "self"},
            {"glazed_terracotta", "Glazed Terracotta", t("{c}_glazed_terracotta"), "self"},
            {"candle", "Candle", MOD + ":item/{c}_candle", "candle"},
            {"wool_stairs", "Wool Stairs", t("{c}_wool_stairs"), "self"},
            {"wool_slab", "Wool Slab", t("{c}_wool_slab"), "slab"},
            {"concrete_stairs", "Concrete Stairs", t("{c}_concrete_stairs"), "self"},
            {"concrete_slab", "Concrete Slab", t("{c}_concrete_slab"), "slab"},
            {"bed", "Bed", null, "bed"},
            {"shulker_box", "Shulker Box", null, "shulker"},
            {"stained_glass", "Stained Glass", t("{c}_stained_glass"), "silk_touch"},
            {"stained_glass_pane", "Stained Glass Pane", MOD + ":item/{c}_stained_glass_pane", "silk_touch"},
            {"candle_cake", "Candle Cake", null, "candle_cake"},   // no item
    };

    /** Vanilla tags our blocks join: tag -> families. */
    private static final Map<String, List<String>> BLOCK_TAGS = new LinkedHashMap<>();
    private static final Map<String, List<String>> ITEM_TAGS = new LinkedHashMap<>();
    static {
        BLOCK_TAGS.put("wool", List.of("wool"));
        BLOCK_TAGS.put("wool_carpets", List.of("carpet"));
        BLOCK_TAGS.put("dampens_vibrations", List.of("wool", "carpet"));
        BLOCK_TAGS.put("occludes_vibration_signals", List.of("wool", "carpet"));
        BLOCK_TAGS.put("shears_major_breaking_speed", List.of("wool", "wool_stairs", "wool_slab"));
        BLOCK_TAGS.put("mineable/pickaxe", List.of("concrete", "terracotta", "glazed_terracotta", "concrete_stairs", "concrete_slab"));
        BLOCK_TAGS.put("mineable/shovel", List.of("concrete_powder"));
        BLOCK_TAGS.put("terracotta", List.of("terracotta"));
        BLOCK_TAGS.put("candles", List.of("candle"));            // flint & steel can light them
        BLOCK_TAGS.put("stairs", List.of("wool_stairs", "concrete_stairs"));
        BLOCK_TAGS.put("slabs", List.of("wool_slab", "concrete_slab"));
        BLOCK_TAGS.put("beds", List.of("bed"));
        BLOCK_TAGS.put("shulker_boxes", List.of("shulker_box"));
        BLOCK_TAGS.put("impermeable", List.of("stained_glass"));
        BLOCK_TAGS.put("candle_cakes", List.of("candle_cake"));  // flint & steel lighting
        ITEM_TAGS.put("wool", List.of("wool"));
        ITEM_TAGS.put("wool_carpets", List.of("carpet"));
        ITEM_TAGS.put("terracotta", List.of("terracotta"));
        // candle onto cake: vanilla keys candle cakes by candle block, and ours registers that pairing
        ITEM_TAGS.put("candles", List.of("candle"));
        ITEM_TAGS.put("stairs", List.of("wool_stairs", "concrete_stairs"));
        ITEM_TAGS.put("slabs", List.of("wool_slab", "concrete_slab"));
        ITEM_TAGS.put("beds", List.of("bed"));
        ITEM_TAGS.put("shulker_boxes", List.of("shulker_box"));
    }

    // ---------------------------------------------------------------- model builders

    /**
     * Vanilla's glass-pane multipart (post + side/side_alt/noside/noside_alt with y rotations),
     * pre-baked into one model per connection set, since a Polymer donor state carries one model.
     * Element geometry and UVs are vanilla's template_glass_pane_* with the y=90 rotations applied.
     */
    private static Map<String, JsonObject> paneModels() {
        JsonObject pane = obj("force_translucent", true, "sprite", t("{c}_stained_glass"));
        String edge = t("{c}_stained_glass_pane_top"), E = "#edge", P = "#pane";
        Map<Character, JsonObject> arms = new LinkedHashMap<>();
        arms.put('n', obj("from", nums(7, 0, 0), "to", nums(9, 16, 7), "faces", obj(
                "down", face(nums(7, 0, 9, 7), E, null), "up", face(nums(7, 0, 9, 7), E, null),
                "north", face(nums(7, 0, 9, 16), E, "north"),
                "west", face(nums(16, 0, 9, 16), P, null), "east", face(nums(9, 0, 16, 16), P, null))));
        arms.put('s', obj("from", nums(7, 0, 9), "to", nums(9, 16, 16), "faces", obj(
                "down", face(nums(7, 0, 9, 7), E, null), "up", face(nums(7, 0, 9, 7), E, null),
                "south", face(nums(7, 0, 9, 16), E, "south"),
                "west", face(nums(7, 0, 0, 16), P, null), "east", face(nums(0, 0, 7, 16), P, null))));
        arms.put('e', obj("from", nums(9, 0, 7), "to", nums(16, 16, 9), "faces", obj(
                "down", face(nums(9, 7, 16, 9), E, null), "up", face(nums(9, 7, 16, 9), E, null),
                "east", face(nums(7, 0, 9, 16), E, "east"),
                "north", face(nums(16, 0, 9, 16), P, null), "south", face(nums(9, 0, 16, 16), P, null))));
        arms.put('w', obj("from", nums(0, 0, 7), "to", nums(7, 16, 9), "faces", obj(
                "down", face(nums(0, 7, 7, 9), E, null), "up", face(nums(0, 7, 7, 9), E, null),
                "west", face(nums(7, 0, 9, 16), E, "west"),
                "north", face(nums(7, 0, 0, 16), P, null), "south", face(nums(0, 0, 7, 16), P, null))));
        // the post's side faces when nothing is attached on that side (vanilla's "noside" parts)
        Map<Character, JsonObject> caps = Map.of(
                'n', face(nums(9, 0, 7, 16), P, null), 'e', face(nums(7, 0, 9, 16), P, null),
                's', face(nums(7, 0, 9, 16), P, null), 'w', face(nums(9, 0, 7, 16), P, null));
        Map<Character, String> full = Map.of('n', "north", 's', "south", 'w', "west", 'e', "east");
        Map<String, JsonObject> out = new LinkedHashMap<>();
        char[] order = {'n', 's', 'w', 'e'};
        for (int mask = 0; mask < 16; mask++) {
            StringBuilder sides = new StringBuilder();
            for (int i = 0; i < 4; i++) if ((mask & (1 << i)) != 0) sides.append(order[i]);
            JsonObject postFaces = obj("down", face(nums(7, 7, 9, 9), E, null), "up", face(nums(7, 7, 9, 9), E, null));
            for (char d : order) {
                if (sides.indexOf(String.valueOf(d)) < 0) postFaces.add(full.get(d), caps.get(d));
            }
            List<Object> elements = new ArrayList<>();
            elements.add(obj("from", nums(7, 0, 7), "to", nums(9, 16, 9), "faces", postFaces));
            for (char d : sides.toString().toCharArray()) elements.add(arms.get(d));
            String name = "{c}_stained_glass_pane_" + (sides.isEmpty() ? "post" : sides.toString());
            // display.fixed pinned so the same model works as a placed blockstate and as an item display
            out.put(name, obj("ambientocclusion", false, "display", FIXED_HALF,
                    "textures", obj("pane", pane, "edge", edge, "particle", t("{c}_stained_glass")),
                    "elements", arr(elements.toArray())));
        }
        return out;
    }

    private static JsonObject face(JsonElement uv, String texture, String cullface) {
        return obj("uv", uv, "texture", texture, "cullface", cullface);
    }

    /**
     * Vanilla box-UV strips (ModelPart.Cube) as JSON element faces, for a box authored in world
     * space the way vanilla renders entity and block-entity models: model → world is a 180° turn
     * about X (LivingEntityRenderer's scale(-1, -1, 1) + rotY(180°); ShulkerBoxRenderer's
     * scale(1, -1, -1)), so model -y is world up and model -z is world south. Hence the strip the
     * model calls DOWN lands on the JSON "up" face, NORTH on "south", and so on. JSON faces read
     * unmirrored from outside, as the strips do; only the bottom strip needs its v flipped.
     */
    static Map<String, double[]> boxUv(int u, int v, int w, int h, int d, int texW, int texH) {
        double sx = 16.0 / texW, sy = 16.0 / texH;
        Map<String, double[]> faces = new LinkedHashMap<>();
        faces.put("up", new double[]{u + d, v, u + d + w, v + d});                          // model DOWN strip
        faces.put("down", new double[]{u + d + w, v + d, u + d + 2 * w, v});                // model UP strip, v flipped
        faces.put("west", new double[]{u, v + d, u + d, v + d + h});                        // model WEST
        faces.put("east", new double[]{u + d + w, v + d, u + 2 * d + w, v + d + h});        // model EAST
        faces.put("south", new double[]{u + d, v + d, u + d + w, v + d + h});               // model NORTH (z flipped)
        faces.put("north", new double[]{u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h}); // model SOUTH
        faces.replaceAll((k, f) -> new double[]{f[0] * sx, f[1] * sy, f[2] * sx, f[3] * sy});
        return faces;
    }

    private static JsonObject facesJson(Map<String, double[]> faces, String texture) {
        JsonObject o = new JsonObject();
        faces.forEach((k, f) -> o.add(k, obj("uv", nums(f), "texture", texture)));
        return o;
    }

    /**
     * A box whose walls are visible from inside as well: vanilla draws entity/block-entity models
     * with a no-cull render type (the inside of a shulker box IS the back of its walls, and the base's
     * top / lid's bottom strips are transparent), while item displays cull backfaces. So next to the
     * outer box, one zero-thickness inward-facing quad per face, textured as its backface reads:
     * side faces mirrored in u, top/bottom mirrored in v. The inner quads sit 0.02 units inside so
     * they never share a plane with a neighbouring block's face.
     */
    private static JsonElement twoSidedBox(int[] from, int[] to, int u, int v, int w, int h, int d, int texW, int texH, String texture) {
        Map<String, double[]> outer = boxUv(u, v, w, h, d, texW, texH);
        List<Object> elements = new ArrayList<>();
        elements.add(obj("from", nums(from[0], from[1], from[2]), "to", nums(to[0], to[1], to[2]), "faces", facesJson(outer, texture)));
        double x0 = from[0], y0 = from[1], z0 = from[2], x1 = to[0], y1 = to[1], z1 = to[2], e = 0.02;
        Map<String, String> opposite = Map.of("north", "south", "south", "north", "west", "east", "east", "west", "up", "down", "down", "up");
        Map<String, double[][]> planes = new LinkedHashMap<>();
        planes.put("north", new double[][]{{x0, y0, z0 + e}, {x1, y1, z0 + e}});
        planes.put("south", new double[][]{{x0, y0, z1 - e}, {x1, y1, z1 - e}});
        planes.put("west", new double[][]{{x0 + e, y0, z0}, {x0 + e, y1, z1}});
        planes.put("east", new double[][]{{x1 - e, y0, z0}, {x1 - e, y1, z1}});
        planes.put("down", new double[][]{{x0, y0 + e, z0}, {x1, y0 + e, z1}});
        planes.put("up", new double[][]{{x0, y1 - e, z0}, {x1, y1 - e, z1}});
        planes.forEach((face, p) -> {
            double[] uv = outer.get(face);
            boolean side = !face.equals("up") && !face.equals("down");
            double[] inner = side ? new double[]{uv[2], uv[1], uv[0], uv[3]} : new double[]{uv[0], uv[3], uv[2], uv[1]};
            elements.add(obj("from", nums(p[0]), "to", nums(p[1]),
                    "faces", obj(opposite.get(face), obj("uv", nums(inner), "texture", texture))));
        });
        return arr(elements.toArray());
    }

    /**
     * One part of vanilla's sheep models as a JSON element, authored unrotated in the space the
     * server-side rig expects: model x -> +x, model y (down) -> -y, model z (back) -> -z, centred on
     * the part's pivot at (8, 8, 8). Part rotations are applied by the rig as display rotations.
     * box = {x, y, z, w, h, d} in model units before inflation; uv = {u, v} texOffs; texture 64×32.
     */
    private static JsonObject sheepPart(double[] box, double inflate, int[] uv, String tex) {
        double x0 = box[0] - inflate, x1 = box[0] + box[3] + inflate;
        double y0 = box[1] - inflate, y1 = box[1] + box[4] + inflate;
        double z0 = box[2] - inflate, z1 = box[2] + box[5] + inflate;
        Map<String, double[]> faces = boxUv(uv[0], uv[1], (int) box[3], (int) box[4], (int) box[5], 64, 32);
        return obj("textures", obj("w", tex, "particle", tex), "elements", arr(
                obj("from", nums(8 + x0, 8 - y1, 8 - z1), "to", nums(8 + x1, 8 - y0, 8 - z0), "faces", facesJson(faces, "#w"))));
    }

    // ---------------------------------------------------------------- data builders

    private static JsonObject lootTable(String kind, String item) {
        JsonObject entry = obj("type", "minecraft:item", "name", item);
        switch (kind) {
            case "slab" -> entry.add("functions", arr(obj("function", "minecraft:set_count", "count", 2.0, "add", false,
                    "conditions", arr(obj("condition", "minecraft:block_state_property", "block", item,
                            "properties", obj("type", "double"))))));
            case "bed" -> entry.add("conditions", arr(obj("condition", "minecraft:block_state_property", "block", item,
                    "properties", obj("part", "head"))));
            case "shulker" -> entry.add("functions", arr(obj("function", "minecraft:copy_components", "source", "block_entity",
                    "include", arr("minecraft:custom_name", "minecraft:container", "minecraft:lock", "minecraft:container_loot"))));
            case "silk_touch" -> {
                return obj("type", "minecraft:block", "pools", arr(obj("rolls", 1, "entries", arr(entry), "conditions", arr(
                        obj("condition", "minecraft:match_tool", "predicate", obj("predicates", obj("minecraft:enchantments", arr(
                                obj("enchantments", "minecraft:silk_touch", "levels", obj("min", 1))))))))));
            }
            case "candle_cake" -> // eating or breaking a candle cake gives the candle back
                    entry = obj("type", "minecraft:item", "name", item.substring(0, item.length() - "_candle_cake".length()) + "_candle");
            case "candle" -> {
                List<Object> fns = new ArrayList<>();
                for (int n = 2; n <= 4; n++) {
                    fns.add(obj("function", "minecraft:set_count", "count", (double) n, "add", false,
                            "conditions", arr(obj("condition", "minecraft:block_state_property", "block", item,
                                    "properties", obj("candles", String.valueOf(n))))));
                }
                entry.add("functions", arr(fns.toArray()));
            }
            default -> {}
        }
        return obj("type", "minecraft:block", "pools", arr(obj("rolls", 1, "entries", arr(entry),
                "conditions", arr(obj("condition", "minecraft:survives_explosion")))));
    }

    /** Sheep death loot: vanilla's mutton pool, plus our wool when unshorn (the choice is made in MobMixin). */
    private static Map<String, JsonObject> sheepLoot(String cid) {
        JsonObject mutton = obj("rolls", 1, "entries", arr(obj("type", "minecraft:item", "name", "minecraft:mutton", "functions", arr(
                obj("function", "minecraft:set_count", "count", obj("type", "minecraft:uniform", "min", 1.0, "max", 2.0), "add", false),
                obj("function", "minecraft:furnace_smelt", "conditions", arr(obj("condition", "minecraft:entity_properties",
                        "entity", "this", "predicate", obj("flags", obj("is_on_fire", true))))),
                obj("function", "minecraft:enchanted_count_increase", "enchantment", "minecraft:looting",
                        "count", obj("type", "minecraft:uniform", "min", 0.0, "max", 1.0))))));
        JsonObject wool = obj("rolls", 1, "entries", arr(obj("type", "minecraft:item", "name", MOD + ":" + cid + "_wool")));
        Map<String, JsonObject> out = new LinkedHashMap<>();
        out.put(cid, obj("type", "minecraft:entity", "pools", arr(wool, mutton)));
        out.put(cid + "_sheared", obj("type", "minecraft:entity", "pools", arr(mutton)));
        return out;
    }

    private static Map<String, JsonObject> recipes(String cid, String dye) {
        java.util.function.Function<String, String> m = fam -> MOD + ":" + cid + "_" + fam;
        Map<String, JsonObject> r = new LinkedHashMap<>();
        r.put(cid + "_wool", obj("type", "minecraft:crafting_shapeless", "category", "building",
                "ingredients", arr(dye, "#minecraft:wool"), "result", obj("id", m.apply("wool"), "count", 1)));
        r.put(cid + "_carpet", obj("type", "minecraft:crafting_shaped", "category", "building",
                "pattern", arr("##"), "key", obj("#", m.apply("wool")), "result", obj("id", m.apply("carpet"), "count", 3)));
        r.put(cid + "_carpet_from_dye", obj("type", "minecraft:crafting_shapeless", "category", "building",
                "ingredients", arr(dye, "#minecraft:wool_carpets"), "result", obj("id", m.apply("carpet"), "count", 1)));
        r.put(cid + "_concrete_powder", obj("type", "minecraft:crafting_shapeless", "category", "building",
                "ingredients", arr(dye, "minecraft:sand", "minecraft:sand", "minecraft:sand", "minecraft:sand",
                        "minecraft:gravel", "minecraft:gravel", "minecraft:gravel", "minecraft:gravel"),
                "result", obj("id", m.apply("concrete_powder"), "count", 8)));
        r.put(cid + "_terracotta", obj("type", "minecraft:crafting_shaped", "category", "building",
                "pattern", arr("###", "#D#", "###"), "key", obj("#", "minecraft:terracotta", "D", dye),
                "result", obj("id", m.apply("terracotta"), "count", 8)));
        r.put(cid + "_glazed_terracotta", obj("type", "minecraft:smelting", "category", "blocks",
                "ingredient", m.apply("terracotta"), "result", obj("id", m.apply("glazed_terracotta")),
                "experience", 0.1, "cookingtime", 200));
        r.put(cid + "_candle", obj("type", "minecraft:crafting_shapeless", "category", "misc",
                "ingredients", arr("minecraft:candle", dye), "result", obj("id", m.apply("candle"), "count", 1)));
        r.put(cid + "_bed", obj("type", "minecraft:crafting_shaped", "category", "misc", "group", "bed",
                "pattern", arr("###", "XXX"), "key", obj("#", m.apply("wool"), "X", "#minecraft:planks"),
                "result", obj("id", m.apply("bed"))));
        r.put("dye_" + cid + "_bed", obj("type", "minecraft:crafting_shapeless", "category", "misc", "group", "bed_dye",
                "ingredients", arr(dye, "#minecraft:beds"), "result", obj("id", m.apply("bed"))));
        // transmute keeps the box's contents, like vanilla's shulker dye recipes
        r.put(cid + "_shulker_box", obj("type", "minecraft:crafting_transmute", "category", "misc", "group", "shulker_box_dye",
                "input", "#minecraft:shulker_boxes", "material", dye, "result", obj("id", m.apply("shulker_box"))));
        r.put(cid + "_bundle", obj("type", "minecraft:crafting_transmute", "category", "equipment", "group", "bundle_dye",
                "input", "#minecraft:bundles", "material", dye, "result", obj("id", MOD + ":" + cid + "_bundle")));
        r.put(cid + "_stained_glass", obj("type", "minecraft:crafting_shaped", "category", "building", "group", "stained_glass",
                "pattern", arr("###", "#X#", "###"), "key", obj("#", "minecraft:glass", "X", dye),
                "result", obj("id", m.apply("stained_glass"), "count", 8)));
        r.put(cid + "_stained_glass_pane", obj("type", "minecraft:crafting_shaped", "category", "building", "group", "stained_glass_pane",
                "pattern", arr("###", "###"), "key", obj("#", m.apply("stained_glass")),
                "result", obj("id", m.apply("stained_glass_pane"), "count", 16)));
        r.put(cid + "_stained_glass_pane_from_glass_pane", obj("type", "minecraft:crafting_shaped", "category", "building",
                "group", "stained_glass_pane", "pattern", arr("###", "#$#", "###"), "key", obj("#", "minecraft:glass_pane", "$", dye),
                "result", obj("id", m.apply("stained_glass_pane"), "count", 8)));
        for (String mat : new String[]{"wool", "concrete"}) {
            r.put(cid + "_" + mat + "_stairs", obj("type", "minecraft:crafting_shaped", "category", "building",
                    "pattern", arr("#  ", "## ", "###"), "key", obj("#", m.apply(mat)),
                    "result", obj("id", m.apply(mat + "_stairs"), "count", 4)));
            r.put(cid + "_" + mat + "_slab", obj("type", "minecraft:crafting_shaped", "category", "building",
                    "pattern", arr("###"), "key", obj("#", m.apply(mat)),
                    "result", obj("id", m.apply(mat + "_slab"), "count", 6)));
        }
        return r;
    }

    /**
     * Colour-independent recipes that vanilla keys on DyeColor (armour dyeing, firework stars),
     * re-emitted with our recipe types, which read an RGB off the dye instead.
     */
    private static Map<String, JsonObject> sharedRecipes() {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        String prefix = "data/minecraft/recipe/";
        for (String name : Vanilla.list(prefix, prefix + "firework_star.json")) {
            if (!name.endsWith(".json")) continue;
            JsonObject r = Vanilla.json(prefix + name).getAsJsonObject();
            String type = r.has("type") ? r.get("type").getAsString() : "";
            if (type.equals("minecraft:crafting_dye") || type.equals("minecraft:crafting_special_firework_star")
                    || type.equals("minecraft:crafting_special_firework_star_fade")) {
                r.remove("dye"); // our types take any dye, ours required
                r.addProperty("type", type.replace("minecraft:", MOD + ":"));
                out.put("dyed/" + name.substring(0, name.length() - 5), r);
            }
        }
        if (out.size() < 3) throw new IllegalStateException("expected vanilla dye/firework recipes, found " + out.keySet());
        return out;
    }

    // ---------------------------------------------------------------- generation

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        this.out = output;
        writes.clear();
        ModColors.load();
        List<ModColor> colors = ModColors.all();

        Map<String, Tex> sources = new LinkedHashMap<>();
        TEXTURES.forEach((dest, src) -> sources.put(dest, Vanilla.texture(src)));
        String bundleItemDef = Vanilla.json("assets/minecraft/items/white_bundle.json").toString();
        // vanilla's shulker item definition carries a transformation the special renderer needs
        String shulkerItemDef = Vanilla.json("assets/minecraft/items/white_shulker_box.json").toString();
        if (!shulkerItemDef.contains("minecraft:shulker_white")) throw new IllegalStateException("vanilla white_shulker_box item definition changed shape");
        // the bed's composite (head + foot shifted one block) needs a full transformation record
        String bedItemDef = Vanilla.json("assets/minecraft/items/white_bed.json").toString();
        if (!bedItemDef.contains("minecraft:block/white_bed_head")) throw new IllegalStateException("vanilla white_bed item definition changed shape");
        sharedRecipes().forEach((name, recipe) -> json(data.resolve(MOD + "/recipe/" + name + ".json"), recipe));

        // Colour-independent sheep body: plain texture, models and item definitions, once.
        Tex sheepBody = Vanilla.texture("entity/sheep/sheep");
        Tex undercoat = Vanilla.texture("entity/sheep/sheep_wool_undercoat");
        png(assets.resolve("textures/block/sheep/body.png"), sheepBody);
        png(assets.resolve("textures/block/sheep/body_hurt.png"), sheepBody.hurt());
        SHARED_ITEM_MODELS.forEach((name, model) -> json(assets.resolve("models/item/" + name + ".json"), model));
        SHARED_ITEM_DEFS.forEach((name, model) -> json(assets.resolve("items/" + name + ".json"), itemDef(model)));

        Map<String, String> lang = new TreeMap<>();
        Map<String, List<String>> blockTags = new LinkedHashMap<>();
        Map<String, List<String>> itemTags = new LinkedHashMap<>();
        BLOCK_TAGS.keySet().forEach(k -> blockTags.put(k, new ArrayList<>()));
        ITEM_TAGS.keySet().forEach(k -> itemTags.put(k, new ArrayList<>()));
        itemTags.put("bundles", new ArrayList<>());
        List<String> ourDyes = new ArrayList<>();

        for (ModColor color : colors) {
            String cid = color.id(), cname = color.name();
            int dark = color.rampDark(), light = color.rampLight();
            String dye = MOD + ":" + cid + "_dye";

            sources.forEach((dest, img) -> png(assets.resolve("textures/" + dest.replace("{c}", cid) + ".png"), img.recolour(dark, light)));
            // sheared body: sheep.png with the tinted undercoat on top
            Tex sheared = sheepBody.composite(undercoat.recolour(dark, light));
            png(assets.resolve("textures/block/sheep/" + cid + "_body_sheared.png"), sheared);
            png(assets.resolve("textures/block/sheep/" + cid + "_body_sheared_hurt.png"), sheared.hurt());
            png(assets.resolve("textures/block/sheep/" + cid + "_wool_hurt.png"), sources.get("block/sheep/{c}_wool").recolour(dark, light).hurt());
            MODELS.forEach((name, model) -> json(assets.resolve("models/block/" + name.replace("{c}", cid) + ".json"), sub(model, cid)));
            ITEM_MODELS.forEach((name, model) -> json(assets.resolve("models/item/" + name.replace("{c}", cid) + ".json"), sub(model, cid)));

            json(assets.resolve("items/" + cid + "_dye.json"), itemDef(MOD + ":item/" + cid + "_dye"));
            lang.put("item." + MOD + "." + cid + "_dye", cname + " Dye");
            ourDyes.add(dye);
            // bundle: vanilla's item definition (select on display context / has_selected_item) re-pointed
            json(assets.resolve("items/" + cid + "_bundle.json"),
                    JsonParser.parseString(bundleItemDef.replace("minecraft:item/white_bundle", MOD + ":item/" + cid + "_bundle")));
            lang.put("item." + MOD + "." + cid + "_bundle", cname + " Bundle");
            itemTags.get("bundles").add(MOD + ":" + cid + "_bundle");
            DISPLAY_ITEM_DEFS.forEach((name, model) -> json(assets.resolve("items/" + name.replace("{c}", cid) + ".json"), itemDef(model.replace("{c}", cid))));
            sheepLoot(cid).forEach((name, table) -> json(data.resolve(MOD + "/loot_table/entities/sheep/" + name + ".json"), table));

            for (String[] fam : FAMILIES) {
                String id = fam[0], famName = fam[1], itemModel = fam[2], loot = fam[3];
                String bid = cid + "_" + id, full = MOD + ":" + bid;
                JsonElement def = switch (id) {
                    case "bed" -> JsonParser.parseString(bedItemDef.replace("minecraft:block/white_bed", MOD + ":block/" + cid + "_bed"));
                    case "shulker_box" -> JsonParser.parseString(shulkerItemDef.replace("minecraft:shulker_white", MOD + ":" + cid));
                    case "candle_cake" -> null;
                    default -> itemDef(itemModel.replace("{c}", cid));
                };
                if (def != null) json(assets.resolve("items/" + bid + ".json"), def);
                lang.put("block." + MOD + "." + bid, id.equals("candle_cake") ? "Cake with " + cname + " Candle" : cname + " " + famName);
                json(data.resolve(MOD + "/loot_table/blocks/" + bid + ".json"), lootTable(loot, full));
                BLOCK_TAGS.forEach((tag, fams) -> { if (fams.contains(id)) blockTags.get(tag).add(full); });
                ITEM_TAGS.forEach((tag, fams) -> { if (fams.contains(id)) itemTags.get(tag).add(full); });
            }
            recipes(cid, dye).forEach((name, recipe) -> json(data.resolve(MOD + "/recipe/" + name + ".json"), recipe));
        }

        blockTags.forEach((tag, ids) -> json(data.resolve("minecraft/tags/block/" + tag + ".json"), obj("replace", false, "values", J.strings(ids))));
        itemTags.forEach((tag, ids) -> json(data.resolve("minecraft/tags/item/" + tag + ".json"), obj("replace", false, "values", J.strings(ids))));
        // our own dye tag (NOT #minecraft:dyes: vanilla recipes read a DyeColor off those and would treat ours as white)
        json(data.resolve(MOD + "/tags/item/dyes.json"), obj("replace", false, "values", J.strings(ourDyes)));

        // Vanilla banner/shield pattern masks, bundled so runtime pattern generation needs no client jar.
        for (String kind : new String[]{"banner", "shield"}) {
            String prefix = "assets/minecraft/textures/entity/" + kind + "/";
            for (String name : Vanilla.list(prefix, prefix + "base.png")) {
                if (name.endsWith(".png")) bytes(assets.resolve("masks/" + kind + "/minecraft/" + name), Vanilla.bytes(prefix + name));
            }
        }

        lang.put("itemGroup." + MOD, "More Dyes");
        JsonObject langJson = new JsonObject();
        lang.forEach(langJson::addProperty);
        json(assets.resolve("lang/en_us.json"), langJson);
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    private void json(Path path, JsonElement element) {
        writes.add(DataProvider.saveStable(out, element, path));
    }

    private void png(Path path, Tex tex) {
        bytes(path, tex.png());
    }

    private void bytes(Path path, byte[] data) {
        writes.add(CompletableFuture.runAsync(() -> {
            try {
                out.writeIfNeeded(path, data, Hashing.sha1().hashBytes(data));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }));
    }
}
