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
import metacraft.ovvar.content.Spot;
import metacraft.ovvar.pack.EquipmentJson;
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

        // The preview layer per half: every patch's art in the library, the cell and patch tables,
        // marker kind 2. The shader draws what the dye colour's slots name.
        Map<String, int[]> library = new LinkedHashMap<>();
        int next = 0;
        for (Patches.Patch patch : Patches.all()) {
            require(next + patch.cells() <= LIBRARY.size(), "the preview library is full (" + LIBRARY.size() + " cells); make it bigger");
            library.put(patch.id(), LIBRARY.get(next));
            next += patch.cells();
        }
        for (Piece piece : Piece.values()) {
            Tex tex = Tex.blank(64, 32).with(MARKER_KIND_X, MARKER_Y, rgb(KIND_PREVIEW, 0, 0));
            for (Patches.Patch patch : Patches.all()) {
                Tex art = arts.get(patch.id());
                int[] at = library.get(patch.id());
                tex = tex.blit(art, 0, 0, art.width, art.height, at[0], at[1]);
                int code = Patches.code(patch.id());
                tex = tex.with(PATCH_TABLE_X + code / 16, code % 16, rgb(at[0], at[1], patch.cells()));
            }
            for (Spot spot : Spot.values()) {
                int index = spot.ordinal() + 1;
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
            layer(chapter, Piece.TOP, "top", marked(withMirror(top, RIGHT_ARM)));
            equipment(chapter, Piece.TOP, false);

            // The bottom: legs and waistband, on the legs slot's layer; under the top when it's up.
            Tex bottom = Tex.blank(64, 32).blit(overlay, RIGHT_LEG[0], RIGHT_LEG[1], RIGHT_LEG[2], RIGHT_LEG[3], RIGHT_LEG[0], RIGHT_LEG[1])
                    .blit(overlay, WAIST[0], WAIST[1], WAIST[2], WAIST[3], WAIST[0], WAIST[1]);
            require(!bottom.isEmpty(), chapter.overlay + ".png has an empty leg box");
            layer(chapter, Piece.BOTTOM, "bottom", marked(withMirror(bottom, RIGHT_LEG)));
            equipment(chapter, Piece.BOTTOM, false);

            if (chapter.rollable) {
                // Rolled down: legs plus the top hanging at the waist, all on the legs slot's layer.
                Tex rolled = overlay(chapter.nercabbadOverlay, chapter);
                Tex nercabbad = Tex.blank(64, 32).blit(rolled, RIGHT_LEG[0], RIGHT_LEG[1], RIGHT_LEG[2], RIGHT_LEG[3], RIGHT_LEG[0], RIGHT_LEG[1])
                        .blit(rolled, BODY[0], BODY[1], BODY[2], BODY[3], BODY[0], BODY[1]);
                layer(chapter, Piece.BOTTOM, "bottom_nercabbad", marked(withMirror(nercabbad, RIGHT_LEG)));
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

    /** (62,15): R = kind, G = side for sided textures. Base textures have no kind texel (0). */
    private static final int MARKER_KIND_X = 62, KIND_SIDED = 1, KIND_PREVIEW = 2;
    /** Always transparent in a patch texture: what the shader draws where there is nothing. */
    private static final int BLANK_X = 63, BLANK_Y = 14;
    /** Preview texture tables, column-major 16 tall: cell index → (u, v, side); patch code → (library x, y, cells). */
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
     * A copy of a limb box one strip up, with every face mirrored in place: what the model shows
     * on the left limb, drawn there so the shader-remapped left limb looks exactly like the right
     * one until a left-side patch says otherwise. Box layout: top and bottom faces (4×4) at
     * +4 and +8 on the first four rows, then four 4×12 side faces.
     */
    private static Tex withMirror(Tex tex, int[] box) {
        int x = box[0], y = box[1], my = y - Spot.MIRROR_SHIFT;
        Tex out = tex.blit(tex, x, y, box[2], box[3], x, my);
        out = out.flipX(x + 4, my, 4, 4).flipX(x + 8, my, 4, 4);
        for (int face = 0; face < 4; face++) out = out.flipX(x + face * 4, my + 4, 4, 12);
        return out;
    }

    private static Tex art(String name) {
        return Tex.read(Vanilla.open("art/" + MOD + "/" + name + ".png"));
    }

    private static Tex overlay(String name, Chapter chapter) {
        Tex tex = art(name);
        require(tex.width == 64 && tex.height == 64, name + ".png is not a 64×64 skin overlay");
        tex = tex.withoutGreenKey();
        return chapter.tint == null ? tex : tex.tinted(chapter.tint);
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
