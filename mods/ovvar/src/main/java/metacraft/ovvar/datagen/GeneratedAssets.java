package metacraft.ovvar.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import metacraft.ovvar.Ovvar;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.Looks;
import metacraft.ovvar.content.ModContent;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Piece;
import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.Spot;
import metacraft.ovvar.pack.EquipmentJson;
import metacraft.ovvar.pack.Trims;
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
import java.util.concurrent.CompletableFuture;

import static metacraft.ovvar.datagen.J.arr;
import static metacraft.ovvar.datagen.J.obj;

/**
 * Everything the client needs, derived from the art in {@code art/ovvar}:
 *
 * <ul>
 *   <li>armour layer textures cut out of the website's skin overlays (64×64 skin layout → 64×32 armour layout;
 *       the boxes the armour model reads — body (16,16), right arm (40,16), right leg (0,16) — sit at the same
 *       coordinates in both, and the left limbs are the model's mirrors of the right, so nothing moves),</li>
 *   <li>one texture per (cell, patch) placement, drawn on one side of the model by the shader, and per half a
 *       preview texture holding every patch's art plus the tables the shader uses to draw the dye colour's slots,</li>
 *   <li>the equipment definition of every garment half with nothing sewn on (the sewn ones come from Combos),</li>
 *   <li>item definitions, models, icons and lang for every ovve, top and patch.</li>
 * </ul>
 */
public final class GeneratedAssets implements DataProvider {
    private static final String MOD = Ovvar.MOD_ID;

    /** Skin-layout boxes (x, y, w, h) that the armour model draws, and which garment owns each. */
    private static final int[] BODY = {16, 16, 24, 16};
    private static final int[] RIGHT_ARM = {40, 16, 16, 16};
    private static final int[] RIGHT_LEG = {0, 16, 16, 16};
    /** The skin's second layer for each, and the left limbs (base, second layer) — the website draws these in 3D. */
    private static final int[] BODY_OUTER = {16, 32, 24, 16};
    private static final int[] RIGHT_ARM_OUTER = {40, 32, 16, 16}, RIGHT_LEG_OUTER = {0, 32, 16, 16};
    private static final int[] LEFT_ARM = {32, 48, 16, 16}, LEFT_ARM_OUTER = {48, 48, 16, 16};
    private static final int[] LEFT_LEG = {16, 48, 16, 16}, LEFT_LEG_OUTER = {0, 48, 16, 16};
    /** The trousers' share of the body box: the bottom two texel rows of its side faces (the waistband). */
    private static final int[] WAIST = {16, 30, 24, 2};
    /** The texel our core shader checks before treating a texture as ours: magenta at alpha 2. */
    private static final int MARKER_X = 63, MARKER_Y = 15, MARKER = 0x02FF00FF;

    private final Path assets, data;
    private final List<CompletableFuture<?>> writes = new ArrayList<>();
    private CachedOutput out;

    public GeneratedAssets(FabricPackOutput output) {
        this.assets = output.getOutputFolder(PackOutput.Target.RESOURCE_PACK).resolve(MOD);
        this.data = output.getOutputFolder(PackOutput.Target.DATA_PACK).resolve(MOD);
    }

    @Override
    public String getName() {
        return "Ovvar art-derived assets";
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        this.out = output;
        writes.clear();
        Map<String, String> lang = new LinkedHashMap<>();
        lang.put("itemGroup." + MOD, "Ovvar");
        Tex icon = art("icon");
        require(icon.width == 16 && icon.height == 16, "icon.png is not 16×16");

        // Patches: art, an icon, an item.
        Map<String, Tex> arts = new LinkedHashMap<>();
        for (Patches.Patch patch : Patches.all()) {
            Tex art = art("patches/" + patch.id());
            require(art.height == Spot.SIZE && art.width == Spot.SIZE * patch.cells(),
                    "patches/" + patch.id() + ".png must be " + (Spot.SIZE * patch.cells()) + "×" + Spot.SIZE + " (" + (patch.seat() ? "seat" : "plain") + " patch)");
            arts.put(patch.id(), art);
            String name = ModContent.patchId(patch).getPath();
            item(name, icon(art));
            lang.put("item." + MOD + "." + name, patch.name() + " patch");
        }

        // One static texture per (cell, patch): the art on its cell, drawn on one side only by the
        // shader (marker texel: kind 1, side). The seat is two of them, one per leg.
        int placementTextures = 0;
        for (Spot spot : Spot.values()) {
            for (Patches.Patch patch : Patches.all()) {
                if (!patch.fits(spot)) continue;
                Tex art = arts.get(patch.id());
                String dir = "textures/entity/equipment/" + spot.piece.layer + "/";
                if (spot == Spot.SEAT) {
                    Tex r = Tex.blank(64, 32).blit(art, 0, 0, Spot.SIZE, Spot.SIZE, spot.u, spot.v);
                    Tex l = Tex.blank(64, 32).blit(art, Spot.SIZE, 0, Spot.SIZE, Spot.SIZE, spot.u, spot.v).flipX(spot.u, spot.v, Spot.SIZE, Spot.SIZE);
                    png(assets.resolve(dir + "patch/seat/" + patch.id() + "_r.png"), sided(r, Spot.Side.RIGHT));
                    png(assets.resolve(dir + "patch/seat/" + patch.id() + "_l.png"), sided(l, Spot.Side.LEFT));
                    placementTextures += 2;
                    continue;
                }
                Tex tex = Tex.blank(64, 32).blit(art, 0, 0, Spot.SIZE, Spot.SIZE, spot.u, spot.v);
                if (spot.side == Spot.Side.LEFT) tex = tex.flipX(spot.u, spot.v, Spot.SIZE, Spot.SIZE);
                png(assets.resolve(dir + "patch/" + spot.id() + "/" + patch.id() + ".png"), sided(tex, spot.side));
                placementTextures++;
            }
        }

        // The trim channel: one trim pattern per (cell, plain patch). Limb cells are alpha-tagged
        // with their side; the atlas source and an identity palette make the art come through as is.
        List<String> trimTextures = new ArrayList<>();
        for (Spot spot : Spot.values()) {
            for (Patches.Patch patch : Patches.all()) {
                if (!patch.fits(spot) || spot == Spot.SEAT) continue;
                Placement placement = new Placement(spot, patch.id());
                String name = Trims.patternName(placement);
                Tex art = arts.get(patch.id());
                Tex tex = Tex.blank(64, 32).blit(art, 0, 0, Spot.SIZE, Spot.SIZE, spot.u, spot.v);
                if (spot.side == Spot.Side.LEFT) tex = tex.flipX(spot.u, spot.v, Spot.SIZE, Spot.SIZE).tagOpaque(Trims.ALPHA_LEFT);
                if (spot.side == Spot.Side.RIGHT) tex = tex.tagOpaque(Trims.ALPHA_RIGHT);
                png(assets.resolve("textures/trims/entity/" + spot.piece.layer + "/" + name + ".png"), tex);
                trimTextures.add(MOD + ":trims/entity/" + spot.piece.layer + "/" + name);
                json(data.resolve("trim_pattern/" + name + ".json"),
                        obj("asset_id", MOD + ":" + name, "decal", false, "description", obj("text", patch.name() + " on the " + spot.label())));
            }
        }
        json(data.resolve("trim_material/" + Trims.MATERIAL + ".json"), obj("asset_name", Trims.MATERIAL, "description", obj("text", "Patch")));
        png(assets.resolve("textures/trims/color_palettes/" + Trims.MATERIAL + ".png"), Vanilla.texture("trims/color_palettes/trim_palette"));
        json(assets.getParent().resolve("minecraft/atlases/armor_trims.json"), obj("sources", arr(obj(
                "type", "minecraft:paletted_permutations",
                "textures", arr(trimTextures.toArray()),
                "palette_key", "minecraft:trims/color_palettes/trim_palette",
                "permutations", obj(Trims.MATERIAL, MOD + ":trims/color_palettes/" + Trims.MATERIAL)))));
        Ovvar.LOGGER.info("[{} datagen] {} trim patterns", MOD, trimTextures.size());

        // The preview layer per half: every patch's art in the library, the cell and patch tables,
        // marker kind 2. The shader draws what the dye colour's slots name.
        Map<String, int[]> library = new LinkedHashMap<>();
        int next = 0;
        for (Patches.Patch patch : Patches.all()) {
            if (Patches.code(patch.id()) > Looks.INSTANT_DESIGNS) continue;   // never previewed: no library entry
            require(next + patch.cells() <= LIBRARY.size(), "the preview library is full (" + LIBRARY.size() + " cells); make it bigger");
            library.put(patch.id(), LIBRARY.get(next));
            next += patch.cells();
        }
        for (Piece piece : Piece.values()) {
            List<Spot> cells = Spot.cells(piece);
            require(cells.size() <= 64 && Looks.INSTANT_DESIGNS <= 64, "the preview tables hold 64 cells and 64 designs");
            Tex tex = Tex.blank(64, 32).with(MARKER_KIND_X, MARKER_Y, rgb(KIND_PREVIEW, cells.size(), Looks.INSTANT_DESIGNS));
            for (Patches.Patch patch : Patches.all()) {
                int[] at = library.get(patch.id());
                if (at == null) continue;
                Tex art = arts.get(patch.id());
                tex = tex.blit(art, 0, 0, art.width, art.height, at[0], at[1]);
                int design = Patches.code(patch.id()) - 1;
                tex = tex.with(PATCH_TABLE_X + design / 16, design % 16, rgb(at[0], at[1], patch.cells()));
            }
            for (int index = 0; index < cells.size(); index++) {
                Spot spot = cells.get(index);
                tex = tex.with(CELL_TABLE_X + index / 16, index % 16, rgb(spot.u, spot.v, spot.side.ordinal()));
            }
            require(tex.get(BLANK_X, BLANK_Y) == 0, "the preview texture draws on the blank texel");
            png(assets.resolve("textures/entity/equipment/" + piece.layer + "/" + EquipmentJson.previewTexture(piece) + ".png"), marked(tex));
        }
        Ovvar.LOGGER.info("[{} datagen] {} placement textures, {} patches in the preview library", MOD, placementTextures, library.size());

        for (Chapter chapter : Chapter.values()) {
            Tex overlay = overlay(chapter.overlay, chapter);
            int colour = overlay.dominant();

            // The top: body and sleeves, on the chest slot's layer.
            Tex top = Tex.blank(64, 32).blit(overlay, BODY[0], BODY[1], BODY[2], BODY[3], BODY[0], BODY[1])
                    .blit(overlay, RIGHT_ARM[0], RIGHT_ARM[1], RIGHT_ARM[2], RIGHT_ARM[3], RIGHT_ARM[0], RIGHT_ARM[1]);
            require(!top.isEmpty(), chapter.overlay + ".png has an empty body or arm box");
            layer(chapter, Piece.TOP, "top", marked(withLeft(top, RIGHT_ARM, overlay, LEFT_ARM)));
            equipment(chapter, Piece.TOP, false);

            // The bottom: legs and waistband, on the legs slot's layer; under the top when it's up.
            Tex bottom = Tex.blank(64, 32).blit(overlay, RIGHT_LEG[0], RIGHT_LEG[1], RIGHT_LEG[2], RIGHT_LEG[3], RIGHT_LEG[0], RIGHT_LEG[1])
                    .blit(overlay, WAIST[0], WAIST[1], WAIST[2], WAIST[3], WAIST[0], WAIST[1]);
            require(!bottom.isEmpty(), chapter.overlay + ".png has an empty leg box");
            layer(chapter, Piece.BOTTOM, "bottom", marked(withLeft(bottom, RIGHT_LEG, overlay, LEFT_LEG)));
            equipment(chapter, Piece.BOTTOM, false);

            if (chapter.rollable) {
                // Rolled down: legs plus the top hanging at the waist, all on the legs slot's layer.
                Tex rolled = overlay(chapter.nercabbadOverlay, chapter);
                Tex nercabbad = Tex.blank(64, 32).blit(rolled, RIGHT_LEG[0], RIGHT_LEG[1], RIGHT_LEG[2], RIGHT_LEG[3], RIGHT_LEG[0], RIGHT_LEG[1])
                        .blit(rolled, BODY[0], BODY[1], BODY[2], BODY[3], BODY[0], BODY[1]);
                layer(chapter, Piece.BOTTOM, "bottom_nercabbad", marked(withLeft(nercabbad, RIGHT_LEG, rolled, LEFT_LEG)));
                equipment(chapter, Piece.BOTTOM, true);
            }

            String ovve = ModContent.ovveId(chapter).getPath();
            String topItem = ModContent.topId(chapter).getPath();
            Tex tinted = icon.tinted(colour);
            item(ovve, tinted);
            item(topItem, Tex.blank(16, 16).blit(tinted, 0, 0, 16, 8, 0, 0));
            lang.put("item." + MOD + "." + ovve, chapter.name + " " + chapter.garmentWord());
            lang.put("item." + MOD + "." + topItem, chapter.name + " " + chapter.garmentWord() + " (top)");
        }
        Ovvar.LOGGER.info("[{} datagen] {} patches, {} cells, {} chapters", MOD, Patches.all().size(), Spot.values().length, Chapter.values().length);

        JsonObject langJson = new JsonObject();
        lang.forEach(langJson::addProperty);
        json(assets.resolve("lang/en_us.json"), langJson);
        // Sewing pinned patches at the smithing table (SewRecipe): the one recipe, nothing to configure.
        json(data.resolve("recipe/sew.json"), obj("type", MOD + ":sew"));
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    private static int rgb(int r, int g, int b) {
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** A patch's inventory icon: its art scaled to fill 16 px, centred. */
    private static Tex icon(Tex art) {
        int scale = Math.max(1, 16 / Math.max(art.width, art.height));
        Tex big = art.scale(scale);
        return Tex.blank(16, 16).blit(big, 0, 0, big.width, big.height, (16 - big.width) / 2, (16 - big.height) / 2);
    }

    /** The equipment definition of one half with nothing sewn on; Combos writes the others into the pack. */
    private void equipment(Chapter chapter, Piece piece, boolean nercabbad) {
        json(assets.resolve("equipment/" + Looks.assetPath(chapter, piece, nercabbad, "") + ".json"),
                JsonParser.parseString(EquipmentJson.json(chapter, piece, nercabbad, List.of())));
    }

    /** Our textures carry the marker texel the core shader looks for (see ovvar.glsl). */
    private static Tex marked(Tex tex) {
        return tex.with(MARKER_X, MARKER_Y, MARKER);
    }

    /** A placement texture: drawn on one side of the model only (both, for body cells). */
    private static Tex sided(Tex tex, Spot.Side side) {
        return marked(tex.with(MARKER_KIND_X, MARKER_Y, rgb(KIND_SIDED, side.ordinal(), 0)));
    }

    // ---- the texel contract with ovvar.glsl

    /** (62,15): R = kind; sided: G = side; preview: G = cells in the half, B = instant designs. Base textures have none (0). */
    private static final int MARKER_KIND_X = 62, KIND_SIDED = 1, KIND_PREVIEW = 2;
    /** Always transparent in a patch texture: what the shader draws where there is nothing. */
    private static final int BLANK_X = 63, BLANK_Y = 14;
    /** Preview texture tables, column-major 16 tall: cell index (in the half) → (u, v, side); design index → (library x, y, cells). */
    private static final int CELL_TABLE_X = 40, PATCH_TABLE_X = 44;
    /** Preview library: 4×4 cells in the head rows nothing else uses (not the tables, not the marker row). */
    private static final List<int[]> LIBRARY = library();

    private static List<int[]> library() {
        List<int[]> out = new ArrayList<>();
        for (int y = 0; y < 16; y += 4) for (int x = 16; x < 40; x += 4) out.add(new int[]{x, y});
        for (int y = 0; y < 12; y += 4) { out.add(new int[]{56, y}); out.add(new int[]{60, y}); }
        for (int y = 0; y < 16; y += 4) for (int x = 0; x < 16; x += 4) out.add(new int[]{x, y});
        return List.copyOf(out);
    }

    /**
     * The left limb's art one strip up from the right limb's box, with every face mirrored in
     * place: the model draws the left limb as a mirror image off the right strips, and the shader
     * sends those fragments here, so the art must be pre-mirrored to come out straight. The art is
     * the skin's own left limb (flattened with its second layer); a skin without one gets a copy of
     * the right limb. Box layout: top and bottom faces (4×4) at +4 and +8 on the first four rows,
     * then four 4×12 side faces.
     */
    private static Tex withLeft(Tex tex, int[] box, Tex skin, int[] leftBox) {
        int x = box[0], y = box[1], my = y - Spot.MIRROR_SHIFT;
        Tex left = Tex.blank(64, 64).blit(skin, leftBox[0], leftBox[1], leftBox[2], leftBox[3], 0, 0);
        Tex out;
        if (left.isEmpty()) {
            out = tex.blit(tex, x, y, box[2], box[3], x, my);
        } else {
            // The skin lays the left limb out for an unmirrored cube: its first side strip is the
            // inner face and its third the outer, the other way round from the right limb's strips
            // the model reads. Swap them so the outer art lands on the outer face.
            out = tex.blit(skin, leftBox[0], leftBox[1], leftBox[2], leftBox[3], x, my)
                    .blit(skin, leftBox[0] + 8, leftBox[1] + 4, 4, 12, x, my + 4)
                    .blit(skin, leftBox[0], leftBox[1] + 4, 4, 12, x + 8, my + 4);
        }
        out = out.flipX(x + 4, my, 4, 4).flipX(x + 8, my, 4, 4);
        for (int face = 0; face < 4; face++) out = out.flipX(x + face * 4, my + 4, 4, 12);
        return out;
    }

    /**
     * The website renders the skin's second layer as a raised 3D layer — belt folds, pockets, the
     * hanging top of a rolled-down ovve. The armour model has one box per part, so that layer is
     * painted onto the base boxes (and onto the left limbs' own boxes for {@link #withLeft}).
     */
    private static Tex flattened(Tex skin) {
        Tex out = skin;
        int[][][] pairs = {{BODY, BODY_OUTER}, {RIGHT_ARM, RIGHT_ARM_OUTER}, {RIGHT_LEG, RIGHT_LEG_OUTER}, {LEFT_ARM, LEFT_ARM_OUTER}, {LEFT_LEG, LEFT_LEG_OUTER}};
        for (int[][] pair : pairs) {
            int[] base = pair[0], outer = pair[1];
            Tex over = Tex.blank(64, 64).blit(skin, outer[0], outer[1], outer[2], outer[3], base[0], base[1]);
            out = out.composite(over);
        }
        return out;
    }

    private static Tex art(String name) {
        return Tex.read(Vanilla.open("art/" + MOD + "/" + name + ".png"));
    }

    private static Tex overlay(String name, Chapter chapter) {
        Tex tex = art(name);
        require(tex.width == 64 && tex.height == 64, name + ".png is not a 64×64 skin overlay");
        tex = tex.withoutGreenKey();
        tex = chapter.tint == null ? tex : tex.tinted(chapter.tint);
        return flattened(tex);
    }

    private void layer(Chapter chapter, Piece piece, String name, Tex tex) {
        png(assets.resolve("textures/entity/equipment/" + piece.layer + "/" + chapter.id + "/" + name + ".png"), tex);
    }

    /** Item definition, flat model and texture for one item. */
    private void item(String name, Tex texture) {
        require(texture.width == 16 && texture.height == 16, name + " icon is not 16×16");
        json(assets.resolve("items/" + name + ".json"), J.itemDef(MOD + ":item/" + name));
        json(assets.resolve("models/item/" + name + ".json"),
                obj("parent", "minecraft:item/generated", "textures", obj("layer0", MOD + ":item/" + name)));
        png(assets.resolve("textures/item/" + name + ".png"), texture);
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException("[" + MOD + " datagen] " + message);
    }

    private void json(Path path, JsonElement element) {
        writes.add(DataProvider.saveStable(out, element, path));
    }

    private void png(Path path, Tex tex) {
        byte[] data = tex.png();
        writes.add(CompletableFuture.runAsync(() -> {
            try {
                out.writeIfNeeded(path, data, Hashing.sha1().hashBytes(data));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }));
    }
}
