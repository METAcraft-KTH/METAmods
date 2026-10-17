package metacraft.ovvar.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tomalbrc.danse.util.MinecraftSkinParser.BodyPart;
import metacraft.ovvar.compat.danse.DanseModels;
import metacraft.ovvar.content.Piece;
import metacraft.ovvar.content.Spot;
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
