package metacraft.ovvar.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Piece;
import metacraft.ovvar.content.Spot;
import metacraft.ovvar.datagen.BlockbenchManifest;
import metacraft.ovvar.datagen.GeneratedAssets;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The Blockbench plugin's goldens are the pack's own textures, and what tells it how to read
 * them is {@link BlockbenchManifest}. So the manifest has to be the enums, not a copy of them:
 * a cell added, moved or resized in {@link Spot}, a patch added to {@link Patches}, a change to
 * {@link Spot#anchored} — each of those must come out of {@code runDatagen} as a changed
 * manifest, and if it does not, this fails rather than the plugin silently drawing last week's
 * model.
 *
 * <p>Every field the manifest writes is compared here against the Java it was written from, not a
 * chosen few: a field nobody pins is a field that may quietly go stale, and the plugin has no
 * other way of noticing. Where the manifest's convention is that an absent key means null
 * ({@code tint}, {@code nercabbad}, {@code source}, {@code artist}, {@code bottomNercabbad}) the
 * comparison reads the key as null and holds it against the Java field being null, so a key that
 * starts being written — or stops — is caught either way.
 */
public final class BlockbenchManifestTests {
	private static JsonObject manifest(GameTestHelper helper) {
		try (InputStream in = BlockbenchManifest.class.getResourceAsStream(BlockbenchManifest.RESOURCE)) {
			if (in == null) {
				helper.fail(BlockbenchManifest.RESOURCE + " missing: run './gradlew :mods:ovvar:runDatagen'");
				return null;
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException e) {
			helper.fail("could not read " + BlockbenchManifest.RESOURCE + ": " + e);
			return null;
		}
	}

	@GameTest
	public void theManifestsCellTableIsSpot(GameTestHelper helper) {
		JsonObject m = manifest(helper);
		JsonArray cells = m.getAsJsonArray("cells");
		if (cells.size() != Spot.values().length) {
			helper.fail("the manifest has " + cells.size() + " cells, Spot has " + Spot.values().length);
		}
		List<String> wrong = new ArrayList<>();
		for (int i = 0; i < Spot.values().length; i++) {
			Spot spot = Spot.values()[i];
			JsonObject c = cells.get(i).getAsJsonObject();
			check(wrong, spot.id(), "id", c.get("id").getAsString(), spot.id());
			check(wrong, spot.id(), "piece", c.get("piece").getAsString(), spot.piece.id);
			check(wrong, spot.id(), "u", c.get("u").getAsInt(), spot.u);
			check(wrong, spot.id(), "v", c.get("v").getAsInt(), spot.v);
			check(wrong, spot.id(), "w", c.get("w").getAsInt(), spot.width);
			check(wrong, spot.id(), "h", c.get("h").getAsInt(), spot.height);
			check(wrong, spot.id(), "side", c.get("side").getAsString(), spot.side.name().toLowerCase(Locale.ROOT));
			check(wrong, spot.id(), "top", c.get("top").getAsBoolean(), spot.top());
			check(wrong, spot.id(), "face", c.get("face").getAsInt(), Spot.face(spot));
			check(wrong, spot.id(), "layer", c.get("layer").getAsInt(), spot.layer());
			check(wrong, spot.id(), "stripStart", c.get("stripStart").getAsInt(), Spot.stripStart(spot));
			check(wrong, spot.id(), "stripWidth", c.get("stripWidth").getAsInt(), Spot.stripWidth(spot));
			check(wrong, spot.id(), "fit", c.get("fit").getAsString(), Patches.Fit.of(spot).name().toLowerCase(Locale.ROOT));
			check(wrong, spot.id(), "label", c.get("label").getAsString(), spot.label());
		}
		if (!wrong.isEmpty()) helper.fail("the manifest's cell table has drifted from Spot: " + wrong);
		helper.succeed();
	}

	@GameTest
	public void theManifestsAnchoredSamplesAreSpotAnchored(GameTestHelper helper) {
		JsonObject m = manifest(helper);
		JsonArray samples = m.getAsJsonArray("anchoredSamples");
		if (samples.size() != BlockbenchManifest.SAMPLES) {
			helper.fail("the manifest has " + samples.size() + " anchored samples, not " + BlockbenchManifest.SAMPLES);
		}
		for (int k = 0; k < samples.size(); k++) {
			JsonArray row = samples.get(k).getAsJsonArray();
			double skinX = row.get(0).getAsDouble(), inflate = row.get(1).getAsDouble();
			int anchor = row.get(2).getAsInt();
			if (skinX != BlockbenchManifest.sampleSkinX(k) || inflate != BlockbenchManifest.sampleInflate(k)
					|| anchor != BlockbenchManifest.sampleAnchor(k)) {
				helper.fail("anchored sample " + k + " is not the grid's own row: " + row);
			}
			double want = Spot.anchored(skinX, inflate, anchor);
			if (Math.abs(row.get(3).getAsDouble() - want) > 1e-12) {
				helper.fail("anchored sample " + k + " (" + row + ") should be " + want);
			}
		}
		helper.succeed();
	}

	@GameTest
	public void theManifestsCatalogueIsPatchesAndChapter(GameTestHelper helper) {
		JsonObject m = manifest(helper);
		JsonArray patches = m.getAsJsonArray("patches");
		if (patches.size() != Patches.all().size()) {
			helper.fail("the manifest has " + patches.size() + " patches, the catalogue has " + Patches.all().size());
		}
		List<String> wrong = new ArrayList<>();
		for (int i = 0; i < Patches.all().size(); i++) {
			Patches.Patch patch = Patches.all().get(i);
			JsonObject p = patches.get(i).getAsJsonObject();
			check(wrong, patch.id(), "id", p.get("id").getAsString(), patch.id());
			check(wrong, patch.id(), "name", p.get("name").getAsString(), patch.name());
			check(wrong, patch.id(), "seat", p.get("seat").getAsBoolean(), patch.seat());
			check(wrong, patch.id(), "w", p.get("w").getAsInt(), patch.width());
			check(wrong, patch.id(), "h", p.get("h").getAsInt(), patch.height());
			JsonArray arts = p.getAsJsonArray("arts");
			check(wrong, patch.id(), "arts", arts.size(), patch.variants().size());
			check(wrong, patch.id(), "artist", str(p, "artist"), patch.artist());
			for (int a = 0; a < Math.min(arts.size(), patch.variants().size()); a++) {
				Patches.Art art = patch.variants().get(a);
				JsonObject j = arts.get(a).getAsJsonObject();
				String who = patch.id() + ".art " + a;
				check(wrong, who, "file", j.get("file").getAsString(), art.file() + ".png");
				check(wrong, who, "w", j.get("w").getAsInt(), art.width());
				check(wrong, who, "h", j.get("h").getAsInt(), art.height());
				check(wrong, who, "default", j.get("default").getAsBoolean(), art.byDefault());
				check(wrong, who, "generated", j.get("generated").getAsBoolean(), art.generated());
				// A drawn art has no source, so the manifest has no such key: absent means null.
				check(wrong, who, "source", str(j, "source"), art.generated() ? art.source().file() + ".png" : null);
			}
			for (Patches.Fit fit : Patches.Fit.values()) {
				String key = fit.name().toLowerCase(Locale.ROOT);
				check(wrong, patch.id(), "fit " + key, p.getAsJsonObject("fits").get(key).getAsString(),
						Patches.artFor(patch, fit).file() + ".png");
			}
		}
		// The PolymITer chapters are reference art due for removal and are left out on purpose.
		List<Chapter> want = new ArrayList<>();
		for (Chapter chapter : Chapter.values()) if (!chapter.id.endsWith("_polymiter")) want.add(chapter);
		JsonArray chapters = m.getAsJsonArray("chapters");
		List<String> got = new ArrayList<>();
		for (var e : chapters) got.add(e.getAsJsonObject().get("id").getAsString());
		List<String> wantIds = new ArrayList<>();
		for (Chapter chapter : want) wantIds.add(chapter.id);
		if (!got.equals(wantIds)) {
			wrong.add("chapters " + got + " should be " + wantIds);
		} else {
			for (int i = 0; i < want.size(); i++) {
				Chapter chapter = want.get(i);
				JsonObject c = chapters.get(i).getAsJsonObject();
				check(wrong, chapter.id, "name", c.get("name").getAsString(), chapter.name);
				check(wrong, chapter.id, "art", c.get("art").getAsString(), chapter.overlay + ".png");
				check(wrong, chapter.id, "nercabbad", str(c, "nercabbad"),
						chapter.nercabbadOverlay == null ? null : chapter.nercabbadOverlay + ".png");
				check(wrong, chapter.id, "tint", num(c, "tint"), chapter.tint);
				check(wrong, chapter.id, "rollable", c.get("rollable").getAsBoolean(), chapter.rollable);
				JsonObject layers = c.getAsJsonObject("layers");
				check(wrong, chapter.id, "layers.top", layers.get("top").getAsString(),
						Piece.TOP.layer + "/" + chapter.id + "/top.png");
				check(wrong, chapter.id, "layers.bottom", layers.get("bottom").getAsString(),
						Piece.BOTTOM.layer + "/" + chapter.id + "/bottom.png");
				check(wrong, chapter.id, "layers.bottomNercabbad", str(layers, "bottomNercabbad"), chapter.rollable
						? Piece.BOTTOM.layer + "/" + chapter.id + "/bottom_nercabbad.png" : null);
			}
		}
		if (!wrong.isEmpty()) helper.fail("the manifest's catalogue has drifted: " + wrong);
		helper.succeed();
	}

	/**
	 * The numbers the plugin lays the model out by, and the texels every texture of ours carries for
	 * {@code ovvar.glsl}. None of them is a table, so nothing above would notice one of them moving:
	 * they are the constants a reader of the plugin would otherwise have to trust twice.
	 */
	@GameTest
	public void theManifestsConstantsAreSpotPatchesAndTheGenerator(GameTestHelper helper) {
		JsonObject m = manifest(helper);
		List<String> wrong = new ArrayList<>();
		check(wrong, "manifest", "version", m.get("version").getAsInt(), BlockbenchManifest.VERSION);
		check(wrong, "manifest", "absentMeansNull", m.get("absentMeansNull").getAsBoolean(), true);
		check(wrong, "manifest", "detail", m.get("detail").getAsInt(), Spot.DETAIL);
		check(wrong, "manifest", "faceRow", m.get("faceRow").getAsInt(), Spot.FACE_ROW);
		check(wrong, "manifest", "faceRows", m.get("faceRows").getAsInt(), Spot.FACE_ROWS);
		check(wrong, "manifest", "topRow", m.get("topRow").getAsInt(), Spot.TOP_ROW);
		check(wrong, "manifest", "topRows", m.get("topRows").getAsInt(), Spot.TOP_ROWS);
		check(wrong, "manifest", "topFace", m.get("topFace").getAsInt(), Spot.TOP_FACE);
		check(wrong, "manifest", "mirrorShift", m.get("mirrorShift").getAsInt(), Spot.MIRROR_SHIFT);
		check(wrong, "manifest", "cellSize", m.get("cellSize").getAsInt(), Spot.SIZE);
		check(wrong, "manifest", "bigCell", m.get("bigCell").getAsInt(), Spot.BIG);
		check(wrong, "manifest", "px", m.get("px").getAsInt(), Spot.PX);
		check(wrong, "manifest", "maxArt", m.get("maxArt").getAsInt(), Patches.MAX_ART);
		check(wrong, "manifest", "overMax", m.get("overMax").getAsInt(), Patches.OVER_MAX);
		check(wrong, "manifest", "seatHeightMax", m.get("seatHeightMax").getAsInt(), Patches.SEAT_HEIGHT_MAX);
		check(wrong, "manifest", "icon", m.get("icon").getAsInt(), Patches.ICON);
		check(wrong, "manifest", "stackedOrder", m.get("stackedOrder").getAsString(), "layer ascending, then sewing order");
		// The skin layout is 64×32 and the textures Spot.DETAIL times it, which is what every
		// coordinate above is in; the plugin allocates its canvases from these two.
		ints(wrong, "skin", m.getAsJsonArray("skin"), 64, 32);
		ints(wrong, "texture", m.getAsJsonArray("texture"), 64 * Spot.DETAIL, 32 * Spot.DETAIL);
		JsonObject inflate = m.getAsJsonObject("inflate");
		for (Piece piece : Piece.values()) {
			check(wrong, "inflate", piece.id, inflate.get(piece.id).getAsDouble(), Spot.inflate(piece));
		}

		JsonObject boxes = m.getAsJsonObject("skinBoxes");
		ints(wrong, "skinBoxes.body", boxes.getAsJsonArray("body"), GeneratedAssets.BODY);
		ints(wrong, "skinBoxes.rightArm", boxes.getAsJsonArray("rightArm"), GeneratedAssets.RIGHT_ARM);
		ints(wrong, "skinBoxes.rightLeg", boxes.getAsJsonArray("rightLeg"), GeneratedAssets.RIGHT_LEG);
		ints(wrong, "skinBoxes.bodyOuter", boxes.getAsJsonArray("bodyOuter"), GeneratedAssets.BODY_OUTER);
		ints(wrong, "skinBoxes.rightArmOuter", boxes.getAsJsonArray("rightArmOuter"), GeneratedAssets.RIGHT_ARM_OUTER);
		ints(wrong, "skinBoxes.rightLegOuter", boxes.getAsJsonArray("rightLegOuter"), GeneratedAssets.RIGHT_LEG_OUTER);
		ints(wrong, "skinBoxes.leftArm", boxes.getAsJsonArray("leftArm"), GeneratedAssets.LEFT_ARM);
		ints(wrong, "skinBoxes.leftArmOuter", boxes.getAsJsonArray("leftArmOuter"), GeneratedAssets.LEFT_ARM_OUTER);
		ints(wrong, "skinBoxes.leftLeg", boxes.getAsJsonArray("leftLeg"), GeneratedAssets.LEFT_LEG);
		ints(wrong, "skinBoxes.leftLegOuter", boxes.getAsJsonArray("leftLegOuter"), GeneratedAssets.LEFT_LEG_OUTER);
		ints(wrong, "skinBoxes.waist", boxes.getAsJsonArray("waist"), GeneratedAssets.WAIST);
		check(wrong, "skinBoxes", "count", boxes.size(), 11);

		// The debug palette first, then the layer, kind and marker texels, then the blank one: the
		// order the manifest writes them in, which is left to right along the marker row.
		List<int[]> texels = new ArrayList<>();
		for (int i = 0; i < GeneratedAssets.DEBUG.length; i++) {
			texels.add(new int[]{GeneratedAssets.DEBUG_X + i, GeneratedAssets.MARKER_Y});
		}
		texels.add(new int[]{GeneratedAssets.LAYER_X, GeneratedAssets.MARKER_Y});
		texels.add(new int[]{GeneratedAssets.MARKER_KIND_X, GeneratedAssets.MARKER_Y});
		texels.add(new int[]{GeneratedAssets.MARKER_X, GeneratedAssets.MARKER_Y});
		texels.add(new int[]{GeneratedAssets.BLANK_X, GeneratedAssets.BLANK_Y});
		JsonArray markers = m.getAsJsonArray("markerTexels");
		check(wrong, "markerTexels", "count", markers.size(), texels.size());
		for (int i = 0; i < Math.min(markers.size(), texels.size()); i++) {
			ints(wrong, "markerTexels[" + i + "]", markers.get(i).getAsJsonArray(), texels.get(i));
		}
		if (!wrong.isEmpty()) helper.fail("the manifest's constants have drifted: " + wrong);
		helper.succeed();
	}

	/** A string field, or null when the key is absent — which is how the manifest writes a null. */
	private static String str(JsonObject o, String key) {
		return o.has(key) ? o.get(key).getAsString() : null;
	}

	/** The same for a number: {@link Chapter#tint} is an Integer, and a chapter without one has no key. */
	private static Integer num(JsonObject o, String key) {
		return o.has(key) ? o.get(key).getAsInt() : null;
	}

	private static void ints(List<String> wrong, String owner, JsonArray got, int... want) {
		List<Integer> mine = new ArrayList<>();
		for (var e : got) mine.add(e.getAsInt());
		List<Integer> theirs = new ArrayList<>();
		for (int v : want) theirs.add(v);
		if (!mine.equals(theirs)) wrong.add(owner + " = " + mine + ", should be " + theirs);
	}

	/** Null-safe: an absent key reads as null here, and null is what a Java field that is not set is. */
	private static void check(List<String> wrong, String owner, String field, Object got, Object want) {
		if (!Objects.equals(got, want)) wrong.add(owner + "." + field + " = " + got + ", should be " + want);
	}
}
