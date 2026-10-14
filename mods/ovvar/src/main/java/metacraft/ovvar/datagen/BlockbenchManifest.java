package metacraft.ovvar.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import metacraft.ovvar.Ovvar;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Piece;
import metacraft.ovvar.content.Spot;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * The Blockbench plugin's copy of the model, as data. {@code tools/blockbench/ovvar.js} draws a
 * patch on an ovve without a game running, which means it has to know everything
 * {@link Spot}, {@link Patches} and {@link Chapter} know — where every cell is, which art each
 * cell shows, how the shader squeezes a strip round an inflated box. Rewriting that in
 * JavaScript would be two tables to keep in step; writing it out here is one, and it is
 * committed with the rest of {@code src/main/generated}, so the same {@code runDatagen} check CI
 * already runs catches a plugin that has fallen behind the enums.
 *
 * <p>Beside the manifest: a PNG of every art nobody drew ({@link Patches.Art#generated}), so the
 * plugin can show today's catalogue whole, and so the scaler it ports
 * ({@link Tex#downscaled}) has a golden to be held to. Nothing else is written — the plugin
 * reads the placement textures, the trims and the garment layers the generator already writes.
 *
 * <p><b>An absent key means null.</b> Every generated file of ours is written through
 * {@link DataProvider#saveStable}, which serialises without nulls, so a null field is a key that
 * is not there rather than a key whose value is {@code null} — {@code chapters[].tint},
 * {@code chapters[].nercabbad}, {@code chapters[].layers.bottomNercabbad},
 * {@code patches[].artist} and a drawn art's {@code arts[].source}. That is the convention, not
 * an accident of the writer, so the manifest says so out loud in {@code absentMeansNull} and the
 * plugin asserts it: reading a field is {@code x == null || x === undefined}, never
 * {@code 'x' in o}.
 *
 * <p>Note that {@code seatHeightMax} is {@link Patches#SEAT_HEIGHT_MAX}, which is 12
 * ({@link Spot#PX} plus a texel of overhang each way) — the design sketch's 20 was stale.
 */
public final class BlockbenchManifest implements DataProvider {
	/** Bumped when the schema changes in a way an older plugin could not read; the plugin refuses a newer one. */
	public static final int VERSION = 2;
	/** Under {@code src/main/generated}, beside {@code ovvar/outlines.json}; on the classpath at runtime. */
	public static final String DIR = Ovvar.MOD_ID + "/blockbench/";
	public static final String RESOURCE = "/" + DIR + "manifest.json";

	/**
	 * How many rows of {@link Spot#anchored} the manifest pins. The grid sweeps {@code skinX} in
	 * eighths of a texel across the whole layout — sample {@code k} is
	 * {@code (k / 8.0, k % 2 == 0 ? 0.5 : 1.0, k % 4)} — so 512 rows reach every strip (legs, body,
	 * arms), both inflations, all four anchor faces, and the fractional columns between texels,
	 * which is where the squeeze's arithmetic actually differs.
	 */
	public static final int SAMPLES = 512;

	public static double sampleSkinX(int k) {
		return k / 8.0;
	}

	public static double sampleInflate(int k) {
		return k % 2 == 0 ? 0.5 : 1.0;
	}

	public static int sampleAnchor(int k) {
		return k % 4;
	}

	private static final int D = Spot.DETAIL, W = 64 * D, H = 32 * D;
	/** Reference art due for removal: the plugin does not offer them. */
	private static final String POLYMITER = "_polymiter";

	/** This run's files; made fresh in {@link #run}, which is where the cache to write them through arrives. */
	private Writes files;

	private final Path root;

	public BlockbenchManifest(FabricPackOutput output) {
		this.root = output.getOutputFolder();
	}

	@Override
	public String getName() {
		return "Ovvar Blockbench manifest";
	}

	@Override
	public CompletableFuture<?> run(CachedOutput output) {
		this.files = new Writes(output);
		JsonObject m = new JsonObject();
		m.addProperty("version", VERSION);
		// Stated rather than implied: see the class javadoc. A plugin that finds this false is
		// reading a manifest some other writer made and should not guess at its nulls.
		m.addProperty("absentMeansNull", true);
		m.addProperty("detail", Spot.DETAIL);
		// The art side of the two resolutions (Spot.ART_DETAIL): a patch PNG is drawn at artDetail px per
		// skin texel and scaled up by artScale as it is laid on a texture, which is at detail. A plugin
		// that composes art onto a texture without scaling it is composing at the wrong size.
		m.addProperty("artDetail", Spot.ART_DETAIL);
		m.addProperty("artScale", Spot.ART_SCALE);
		m.addProperty("artPx", Spot.ART_PX);
		m.addProperty("faceRow", Spot.FACE_ROW);
		m.addProperty("faceRows", Spot.FACE_ROWS);
		m.addProperty("topRow", Spot.TOP_ROW);
		m.addProperty("topRows", Spot.TOP_ROWS);
		m.addProperty("topFace", Spot.TOP_FACE);
		m.addProperty("mirrorShift", Spot.MIRROR_SHIFT);
		m.addProperty("cellSize", Spot.SIZE);
		m.addProperty("bigCell", Spot.BIG);
		m.addProperty("px", Spot.PX);
		m.addProperty("maxArt", Patches.MAX_ART);
		m.addProperty("overMax", Patches.OVER_MAX);
		m.addProperty("seatHeightMax", Patches.SEAT_HEIGHT_MAX);
		m.addProperty("icon", Patches.ICON);
		m.add("skin", ints(64, 32));
		m.add("texture", ints(W, H));
		JsonObject inflate = new JsonObject();
		for (Piece piece : Piece.values()) inflate.addProperty(piece.id, Spot.inflate(piece));
		m.add("inflate", inflate);
		m.add("markerTexels", markerTexels());
		m.add("skinBoxes", skinBoxes());
		m.add("chapters", chapters());
		m.add("cells", cells());
		m.add("patches", patches());
		m.add("anchoredSamples", anchoredSamples());
		// Spot.stacked has no table to write out — it is a sort — so the manifest states the rule
		// and the plugin's own stacking test is what holds it.
		m.addProperty("stackedOrder", "layer ascending, then sewing order");
		files.json(root.resolve(DIR + "manifest.json"), m);
		Ovvar.LOGGER.info("[{} datagen] Blockbench manifest: {} cells, {} patches, {} anchored samples",
				Ovvar.MOD_ID, Spot.values().length, Patches.all().size(), SAMPLES);
		return files.allOf();
	}

	/**
	 * The texels every garment and placement texture carries for {@code ovvar.glsl} — the marker,
	 * its kind, the layer's inflation, the debug palette, and the texel the shader promises is
	 * always clear. The plugin composes the cloth, not the shader's contract, so its goldens are
	 * compared everywhere <em>but</em> here; writing the list out means a texel moved in
	 * {@link GeneratedAssets} moves in the plugin's tests too.
	 */
	private static JsonArray markerTexels() {
		JsonArray out = new JsonArray();
		for (int i = 0; i < GeneratedAssets.DEBUG.length; i++) {
			out.add(ints(GeneratedAssets.DEBUG_X + i, GeneratedAssets.MARKER_Y));
		}
		out.add(ints(GeneratedAssets.LAYER_X, GeneratedAssets.MARKER_Y));
		out.add(ints(GeneratedAssets.MARKER_KIND_X, GeneratedAssets.MARKER_Y));
		out.add(ints(GeneratedAssets.MARKER_X, GeneratedAssets.MARKER_Y));
		out.add(ints(GeneratedAssets.BLANK_X, GeneratedAssets.BLANK_Y));
		return out;
	}

	private static JsonObject skinBoxes() {
		JsonObject out = new JsonObject();
		out.add("body", ints(GeneratedAssets.BODY));
		out.add("rightArm", ints(GeneratedAssets.RIGHT_ARM));
		out.add("rightLeg", ints(GeneratedAssets.RIGHT_LEG));
		out.add("bodyOuter", ints(GeneratedAssets.BODY_OUTER));
		out.add("rightArmOuter", ints(GeneratedAssets.RIGHT_ARM_OUTER));
		out.add("rightLegOuter", ints(GeneratedAssets.RIGHT_LEG_OUTER));
		out.add("leftArm", ints(GeneratedAssets.LEFT_ARM));
		out.add("leftArmOuter", ints(GeneratedAssets.LEFT_ARM_OUTER));
		out.add("leftLeg", ints(GeneratedAssets.LEFT_LEG));
		out.add("leftLegOuter", ints(GeneratedAssets.LEFT_LEG_OUTER));
		out.add("waist", ints(GeneratedAssets.WAIST));
		return out;
	}

	/**
	 * The chapters the plugin offers, with the layer textures the generator already writes for
	 * each: the plugin loads those rather than cutting the overlays again, so a chapter whose
	 * cloth is tinted or hand-drawn (PolymITer's leggings) costs it nothing.
	 */
	private static JsonArray chapters() {
		JsonArray out = new JsonArray();
		for (Chapter chapter : Chapter.values()) {
			if (chapter.id.endsWith(POLYMITER)) continue;
			JsonObject c = new JsonObject();
			c.addProperty("id", chapter.id);
			c.addProperty("name", chapter.name);
			c.addProperty("art", chapter.overlay + ".png");
			c.addProperty("nercabbad", chapter.nercabbadOverlay == null ? null : chapter.nercabbadOverlay + ".png");
			c.addProperty("tint", chapter.tint);
			c.addProperty("rollable", chapter.rollable);
			c.addProperty("slot", chapter.slot.getName());
			JsonObject layers = new JsonObject();
			layers.addProperty("top", Piece.TOP.layer + "/" + chapter.id + "/top.png");
			// A frack has no bottom half: nothing is drawn on the legs.
			layers.addProperty("bottom", chapter.pieces().contains(Piece.BOTTOM) ? Piece.BOTTOM.layer + "/" + chapter.id + "/bottom.png" : null);
			layers.addProperty("bottomNercabbad", chapter.rollable
					? Piece.BOTTOM.layer + "/" + chapter.id + "/bottom_nercabbad.png" : null);
			c.add("layers", layers);
			out.add(c);
		}
		return out;
	}

	/** The cell table, in {@link Spot}'s own order — which is the order the game test compares. */
	private static JsonArray cells() {
		JsonArray out = new JsonArray();
		for (Spot spot : Spot.values()) {
			JsonObject c = new JsonObject();
			c.addProperty("id", spot.id());
			c.addProperty("piece", spot.piece.id);
			c.addProperty("layerFolder", spot.piece.layer);
			c.addProperty("u", spot.u);
			c.addProperty("v", spot.v);
			c.addProperty("w", spot.width);
			c.addProperty("h", spot.height);
			c.addProperty("side", spot.side.name().toLowerCase(Locale.ROOT));
			c.addProperty("top", spot.top());
			c.addProperty("face", Spot.face(spot));
			c.addProperty("layer", spot.layer());
			c.addProperty("stripStart", Spot.stripStart(spot));
			c.addProperty("stripWidth", Spot.stripWidth(spot));
			c.addProperty("fit", Patches.Fit.of(spot).name().toLowerCase(Locale.ROOT));
			c.addProperty("label", spot.label());
			out.add(c);
		}
		return out;
	}

	/**
	 * The catalogue, and — as a side effect — a PNG of every art nobody drew. A generated art has
	 * no file anywhere in the source tree ({@link Patches.Art#resource} says so out loud), so this
	 * is the only place its pixels are ever written down, and the plugin's ported scaler is held
	 * to them.
	 */
	private JsonArray patches() {
		JsonArray out = new JsonArray();
		for (Patches.Patch patch : Patches.all()) {
			JsonObject p = new JsonObject();
			p.addProperty("id", patch.id());
			p.addProperty("name", patch.name());
			p.addProperty("seat", patch.seat());
			p.addProperty("w", patch.width());
			p.addProperty("h", patch.height());
			p.addProperty("artist", patch.artist());
			JsonArray arts = new JsonArray();
			for (Patches.Art art : patch.variants()) {
				JsonObject a = new JsonObject();
				a.addProperty("file", art.file() + ".png");
				a.addProperty("w", art.width());
				a.addProperty("h", art.height());
				a.addProperty("default", art.byDefault());
				a.addProperty("generated", art.generated());
				a.addProperty("source", art.generated() ? art.source().file() + ".png" : null);
				arts.add(a);
				if (art.generated()) {
					String name = art.file().substring(art.file().lastIndexOf('/') + 1);
					files.png(root.resolve(DIR + "art/" + name + ".png"), Tex.art(art));
				}
			}
			p.add("arts", arts);
			JsonObject fits = new JsonObject();
			for (Patches.Fit fit : Patches.Fit.values()) {
				fits.addProperty(fit.name().toLowerCase(Locale.ROOT), Patches.artFor(patch, fit).file() + ".png");
			}
			p.add("fits", fits);
			out.add(p);
		}
		return out;
	}

	private static JsonArray anchoredSamples() {
		JsonArray out = new JsonArray();
		for (int k = 0; k < SAMPLES; k++) {
			double skinX = sampleSkinX(k), inflate = sampleInflate(k);
			int anchor = sampleAnchor(k);
			JsonArray row = new JsonArray();
			row.add(skinX);
			row.add(inflate);
			row.add(anchor);
			row.add(Spot.anchored(skinX, inflate, anchor));
			out.add(row);
		}
		return out;
	}

	private static JsonArray ints(int... values) {
		JsonArray out = new JsonArray();
		for (int v : values) out.add(v);
		return out;
	}
}
