package metacraft.ovvar.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import metacraft.ovvar.Ovvar;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.Layout;
import metacraft.ovvar.content.Looks;
import metacraft.ovvar.content.ModContent;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Piece;
import metacraft.ovvar.content.Spot;
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
 *   <li>one equipment definition per garment half: the base and one dyeable layer per {@link Layout} field,
 *       whose texture carries the marker row the shader reads and a library of the field's patch art,</li>
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
            require(art.height == Spot.SIZE && art.width % Spot.SIZE == 0 && art.width <= Spot.SIZE * Layout.MAX_CELLS,
                    "patches/" + patch.id() + ".png must be " + Spot.SIZE + " tall and a multiple of " + Spot.SIZE + " wide");
            arts.put(patch.id(), art);
            String name = ModContent.patchId(patch).getPath();
            item(name, icon(art));
            lang.put("item." + MOD + "." + name, patch.name() + " patch");
        }

        // Fields: one dyeable layer texture each, shared by every chapter: the marker row telling
        // the shader which bits and cells are its own, and a library of its patches' art.
        for (Layout.Field field : Layout.all()) {
            Tex tex = Tex.blank(64, 32)
                    .with(Layout.MARKER_FIELD_X, Layout.MARKER_ROW, rgb(field.offset(), field.bits(), field.cells().size()));
            for (int i = 0; i < field.cells().size(); i++) {
                Spot cell = field.cells().get(i);
                tex = tex.with(Layout.MARKER_CELLS_X - i, Layout.MARKER_ROW, rgb(cell.u, cell.v, cell.side.ordinal()));
            }
            int cells = field.cells().size();
            for (int entry = 0; entry < field.patches().size(); entry++) {
                Tex art = arts.get(field.patches().get(entry));
                require(art.width == Spot.SIZE * cells, "patch " + field.patches().get(entry) + " is " + art.width / Spot.SIZE
                        + " cell(s) wide but field " + field.id() + " has " + cells);
                for (int c = 0; c < cells; c++) {
                    int[] at = Layout.LIBRARY_CELLS.get(entry * cells + c);
                    tex = tex.blit(art, c * Spot.SIZE, 0, Spot.SIZE, Spot.SIZE, at[0], at[1]);
                }
            }
            require(tex.get(Layout.BLANK_X, Layout.BLANK_Y) == 0, "field " + field.id() + " draws on the blank texel");
            png(assets.resolve("textures/entity/equipment/" + field.piece().layer + "/patch/" + field.id() + ".png"), marked(tex));
        }

        for (Chapter chapter : Chapter.values()) {
            Tex overlay = overlay(chapter.overlay, chapter);
            int colour = overlay.dominant();

            // The top: body and sleeves, on the chest slot's layer.
            Tex top = Tex.blank(64, 32).blit(overlay, BODY[0], BODY[1], BODY[2], BODY[3], BODY[0], BODY[1])
                    .blit(overlay, RIGHT_ARM[0], RIGHT_ARM[1], RIGHT_ARM[2], RIGHT_ARM[3], RIGHT_ARM[0], RIGHT_ARM[1]);
            require(!top.isEmpty(), chapter.overlay + ".png has an empty body or arm box");
            layer(chapter, Piece.TOP, "top", marked(withMirror(top, RIGHT_ARM)));
            equipment(Looks.asset(chapter, Piece.TOP, false), Piece.TOP, chapter.id + "/top");

            // The bottom: legs and waistband, on the legs slot's layer; under the top when it's up.
            Tex bottom = Tex.blank(64, 32).blit(overlay, RIGHT_LEG[0], RIGHT_LEG[1], RIGHT_LEG[2], RIGHT_LEG[3], RIGHT_LEG[0], RIGHT_LEG[1])
                    .blit(overlay, WAIST[0], WAIST[1], WAIST[2], WAIST[3], WAIST[0], WAIST[1]);
            require(!bottom.isEmpty(), chapter.overlay + ".png has an empty leg box");
            layer(chapter, Piece.BOTTOM, "bottom", marked(withMirror(bottom, RIGHT_LEG)));
            equipment(Looks.asset(chapter, Piece.BOTTOM, false), Piece.BOTTOM, chapter.id + "/bottom");

            if (chapter.rollable) {
                // Rolled down: legs plus the top hanging at the waist, all on the legs slot's layer.
                Tex rolled = overlay(chapter.nercabbadOverlay, chapter);
                Tex nercabbad = Tex.blank(64, 32).blit(rolled, RIGHT_LEG[0], RIGHT_LEG[1], RIGHT_LEG[2], RIGHT_LEG[3], RIGHT_LEG[0], RIGHT_LEG[1])
                        .blit(rolled, BODY[0], BODY[1], BODY[2], BODY[3], BODY[0], BODY[1]);
                layer(chapter, Piece.BOTTOM, "bottom_nercabbad", marked(withMirror(nercabbad, RIGHT_LEG)));
                equipment(Looks.asset(chapter, Piece.BOTTOM, true), Piece.BOTTOM, chapter.id + "/bottom_nercabbad");
            }

            String ovve = ModContent.ovveId(chapter).getPath();
            String topItem = ModContent.topId(chapter).getPath();
            Tex tinted = icon.tinted(colour);
            item(ovve, tinted);
            item(topItem, Tex.blank(16, 16).blit(tinted, 0, 0, 16, 8, 0, 0));
            lang.put("item." + MOD + "." + ovve, chapter.name + " " + chapter.garmentWord());
            lang.put("item." + MOD + "." + topItem, chapter.name + " " + chapter.garmentWord() + " (top)");
        }
        Ovvar.LOGGER.info("[{} datagen] {} patches, {} fields, {} chapters", MOD, Patches.all().size(), Layout.all().size(), Chapter.values().length);

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

    /** The equipment definition of one half: the base, then every field's layer, dyeable so the dye bits reach it. */
    private void equipment(net.minecraft.resources.ResourceKey<?> key, Piece piece, String base) {
        List<Object> layers = new ArrayList<>();
        layers.add(obj("texture", MOD + ":" + base));
        for (Layout.Field field : Layout.fields(piece)) {
            layers.add(obj("texture", MOD + ":patch/" + field.id(), "dyeable", obj()));
        }
        json(assets.resolve("equipment/" + key.identifier().getPath() + ".json"), obj("layers", obj(piece.layer, arr(layers.toArray()))));
    }

    /** Our textures carry the marker texel the core shader looks for (see Spot). */
    private static Tex marked(Tex tex) {
        return tex.with(MARKER_X, MARKER_Y, MARKER);
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
