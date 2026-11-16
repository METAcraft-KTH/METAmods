package se.metacraft.ovvar.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tomalbrc.danse.util.MinecraftSkinParser.BodyPart;
import se.metacraft.ovvar.compat.danse.DanseModels;
import se.metacraft.ovvar.content.Piece;
import se.metacraft.ovvar.content.Spot;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** What {@link DanseModels} puts in the pack: complete, consistent, and the seat split by leg. */
public final class DanseModelsTests {

	private static Map<String, String> pack() {
		Map<String, String> files = new TreeMap<>();
		DanseModels.write((path, bytes) -> files.put(path, new String(bytes, StandardCharsets.UTF_8)));
		return files;
	}

	@GameTest
	public void everyReferencedModelAndTextureExists(GameTestHelper helper) {
		if (!danse()) { helper.succeed(); return; }
		Map<String, String> files = pack();
		int definitions = 0;
		for (var e : files.entrySet()) {
			if (!e.getKey().startsWith("assets/ovvar/items/danse/")) continue;
			definitions++;
			for (String model : models(JsonParser.parseString(e.getValue()))) {
				String path = "assets/ovvar/models/" + model.substring("ovvar:".length()) + ".json";
				String json = files.get(path);
				if (json == null) { helper.fail(e.getKey() + " names " + model + ", which is not in the pack"); return; }
				String texture = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures").get("t").getAsString();
				String png = "assets/ovvar/textures/" + texture.substring("ovvar:".length()) + ".png";
				if (!files.containsKey(png)) { helper.fail(path + " uses " + texture + ", which is not in the pack"); return; }
			}
		}
		if (definitions != 6) { helper.fail(definitions + " item definitions, expected 6 (3 parts × 2 pieces)"); return; }
		helper.succeed();
	}

	@GameTest
	public void theDataTopBodyCarriesItsClothAndAnItkOnTheChest(GameTestHelper helper) {
		if (!danse()) { helper.succeed(); return; }
		String def = pack().get("assets/ovvar/items/danse/top/body.json");
		if (def == null) { helper.fail("no top/body definition"); return; }
		int index = 1 + Spot.cells(Piece.TOP).indexOf(Spot.FRONT_TOP_LEFT);
		if (!def.contains("\"when\":\"data/top\"")) { helper.fail("no base case for data/top"); return; }
		if (!def.contains("\"index\":" + index)) { helper.fail("no select on string " + index + " (FRONT_TOP_LEFT)"); return; }
		if (!def.contains("\"model\":\"ovvar:danse/top/body/front_top_left/itk\"")) { helper.fail("no itk case on the chest cell"); return; }
		if (def.contains("sleeve_")) { helper.fail("a sleeve cell on the body"); return; }
		helper.succeed();
	}

	@GameTest
	public void theSeatDrawsEachHalfOnItsOwnLeg(GameTestHelper helper) {
		if (!danse()) { helper.succeed(); return; }
		Map<String, String> files = pack();
		String right = firstWith(files, "assets/ovvar/models/danse/bottom/leg_r/seat/");
		String left = firstWith(files, "assets/ovvar/models/danse/bottom/leg_l/seat/");
		if (right == null || left == null) { helper.fail("no seat models on both legs"); return; }
		if (!right.contains("_r\"")) { helper.fail("the right leg's seat is not the _r half: " + right); return; }
		if (!left.contains("_l\"")) { helper.fail("the left leg's seat is not the _l half: " + left); return; }
		helper.succeed();
	}

	@GameTest
	public void onlyTheRightPiecesAreDrawnOnEachPart(GameTestHelper helper) {
		if (!danse()) { helper.succeed(); return; }
		if (!DanseModels.draws(Piece.TOP, BodyPart.LEFT_ARM) || DanseModels.draws(Piece.TOP, BodyPart.LEFT_LEG)
				|| !DanseModels.draws(Piece.BOTTOM, BodyPart.BODY) || DanseModels.draws(Piece.BOTTOM, BodyPart.HEAD)) {
			helper.fail("draws(piece, part) disagrees with the top = body+arms, bottom = body+legs rule");
			return;
		}
		helper.succeed();
	}

	/**
	 * Every texture is a crop of one part's rectangle (at most 24×16 skin texels, the body's strip),
	 * under {@code item/} where the vanilla items atlas already looks — no atlas source of our own,
	 * so no full 256×128 layer sheets stitched into every player's atlas.
	 */
	@GameTest
	public void texturesAreCroppedToTheirPartAndNeedNoAtlasSource(GameTestHelper helper) {
		if (!danse()) { helper.succeed(); return; }
		Map<String, byte[]> files = new TreeMap<>();
		DanseModels.write(files::put);
		if (files.keySet().stream().anyMatch(p -> p.contains("/atlases/"))) { helper.fail("an atlas file in the pack"); return; }
		int pngs = 0;
		for (var e : files.entrySet()) {
			if (!e.getKey().endsWith(".png")) continue;
			pngs++;
			if (!e.getKey().startsWith("assets/ovvar/textures/item/danse/")) { helper.fail(e.getKey() + " is outside item/danse/"); return; }
			try {
				java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(e.getValue()));
				if (image.getWidth() > 24 * Spot.DETAIL || image.getHeight() > 16 * Spot.DETAIL) {
					helper.fail(e.getKey() + " is " + image.getWidth() + "×" + image.getHeight() + ", bigger than a part's strip");
					return;
				}
			} catch (java.io.IOException ex) {
				helper.fail(e.getKey() + " does not read as a PNG: " + ex.getMessage());
				return;
			}
		}
		if (pngs == 0) { helper.fail("no textures in the pack"); return; }
		helper.succeed();
	}

	/**
	 * A base layer's right-limb box is the checker an unpatched shaderpack shows; the cloth is its
	 * copy in the head rows (Spot.RIGHT_COPY_X). The shells are item-atlas crops with no marker, so
	 * the shader never redirects them: every base crop, right limbs included, must carry the cloth.
	 */
	@GameTest
	public void rightLimbCropsHaveNoChecker(GameTestHelper helper) {
		if (!danse()) { helper.succeed(); return; }
		Map<String, byte[]> files = new TreeMap<>();
		DanseModels.write(files::put);
		List<String> checkered = new ArrayList<>();
		int crops = 0;
		for (var e : files.entrySet()) {
			String path = e.getKey();
			if (!path.startsWith("assets/ovvar/textures/item/danse/") || !path.endsWith(".png")) continue;
			// item/danse/<piece>/<part>/<texture>: a base crop's texture is <chapter>/<layer>, a patch's patch/...
			String[] parts = path.substring("assets/ovvar/textures/item/danse/".length()).split("/", 3);
			if (parts.length < 3 || parts[2].startsWith("patch/")) continue;
			crops++;
			try {
				java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(e.getValue()));
				scan:
				for (int y = 0; y < image.getHeight(); y++) {
					for (int x = 0; x < image.getWidth(); x++) {
						if (image.getRGB(x, y) == Spot.CHECKER_A) { checkered.add(path); break scan; }
					}
				}
			} catch (java.io.IOException ex) {
				helper.fail(path + " does not read as a PNG: " + ex.getMessage());
				return;
			}
		}
		if (crops == 0) { helper.fail("no base crops in the pack"); return; }
		if (!checkered.isEmpty()) { helper.fail(checkered.size() + " base crop(s) show the checker: " + checkered); return; }
		helper.succeed();
	}

	/**
	 * Every base crop is stitched into the one 2048×2048 items atlas, patch crops and vanilla items
	 * alongside. The base crops alone must leave at least three quarters of it free; the pixel
	 * total is logged so growth is visible as chapters are added.
	 */
	@GameTest
	public void baseCropsFitTheirShareOfTheItemsAtlas(GameTestHelper helper) {
		if (!danse()) { helper.succeed(); return; }
		Map<String, byte[]> files = new TreeMap<>();
		DanseModels.write(files::put);
		String root = "assets/ovvar/textures/item/danse/";
		long pixels = 0;
		int crops = 0;
		for (var e : files.entrySet()) {
			String path = e.getKey();
			if (!path.startsWith(root) || !path.endsWith(".png")) continue;
			// item/danse/<piece>/<part>/<texture>: a patch's texture is patch/..., everything else is a base crop
			String[] parts = path.substring(root.length()).split("/", 3);
			if (parts.length < 3 || parts[2].startsWith("patch/")) continue;
			try {
				java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(e.getValue()));
				pixels += (long) image.getWidth() * image.getHeight();
				crops++;
			} catch (java.io.IOException ex) {
				helper.fail(path + " does not read as a PNG: " + ex.getMessage());
				return;
			}
		}
		long bound = 2048L * 2048L / 4;
		System.out.println("[ovvar] Danse base crops: " + crops + " crops, " + pixels + " pixels of " + bound + " allowed");
		if (crops == 0) { helper.fail("no base crops in the pack"); return; }
		if (pixels > bound) { helper.fail("base crops take " + pixels + " pixels, over the " + bound + " (a quarter of the items atlas)"); return; }
		helper.succeed();
	}

	private static String firstWith(Map<String, String> files, String prefix) {
		return files.entrySet().stream().filter(e -> e.getKey().startsWith(prefix)).map(Map.Entry::getValue).findFirst().orElse(null);
	}

	/** Every {"type":"minecraft:model","model":...} reference in an item definition. */
	private static List<String> models(JsonElement e) {
		List<String> out = new ArrayList<>();
		if (e.isJsonObject()) {
			JsonObject o = e.getAsJsonObject();
			if (o.has("type") && o.get("type").getAsString().equals("minecraft:model")) out.add(o.get("model").getAsString());
			for (var entry : o.entrySet()) out.addAll(models(entry.getValue()));
		} else if (e.isJsonArray()) {
			for (JsonElement c : e.getAsJsonArray()) out.addAll(models(c));
		}
		return out;
	}

	private static boolean danse() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("danse");
	}
}
