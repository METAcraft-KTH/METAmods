import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tomalbrc.bil.core.model.Model;
import de.tomalbrc.bil.core.model.Node;
import de.tomalbrc.bil.file.loader.BbModelLoader;
import de.tomalbrc.bil.util.ResourcePackUtil;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import se.metacraft.nether_rifts.NetherRifts;
import se.metacraft.nether_rifts.mob.ArmourPieces;
import se.metacraft.nether_rifts.mob.HeldItems;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The drummer's armour pieces (tools/import_drummer.py): bones in the limbs they cover, and item models per material that
 * draw the same cubes as BIL's own model for the bone, so a piece sits where the model puts it.
 */
public class ArmourPiecesTest {

	/** Each piece's bone, and the drummer bone it is in. */
	private static final Map<ArmourPieces.Piece, String> LIMBS = Map.of(
			ArmourPieces.Piece.HEAD, "head", ArmourPieces.Piece.CHEST, "body", ArmourPieces.Piece.CHEST_LEFT, "left_arm",
			ArmourPieces.Piece.CHEST_RIGHT, "right_arm", ArmourPieces.Piece.LEGS, "body", ArmourPieces.Piece.LEGS_LEFT, "left_leg",
			ArmourPieces.Piece.LEGS_RIGHT, "right_leg", ArmourPieces.Piece.FEET_LEFT, "left_leg", ArmourPieces.Piece.FEET_RIGHT, "right_leg");
	/** Vanilla's armour materials for people, and the slots they come in. */
	private static final List<String> MATERIALS = List.of("leather", "chainmail", "iron", "gold", "diamond", "netherite", "copper");

	private static Model model;

	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		model = BbModelLoader.load(NetherRifts.getID("war_drummer"));
	}

	private static Node node(String name) {
		var node = HeldItems.marker(model, name);
		assertNotNull(node, "no node " + name);
		return node;
	}

	/** Every piece is a bone with a model, inside the limb it covers, at full scale (BIL shrinks only a bone past 24 px). */
	@Test
	public void piecesAreBonesInTheirLimbs() {
		for (var piece : ArmourPieces.Piece.values()) {
			var node = node(piece.bone);
			assertEquals(Node.NodeType.BONE, node.type(), piece.bone);
			assertNotNull(node.modelData(), piece.bone + " has no model");
			assertEquals(LIMBS.get(piece), node.parent().name(), piece.bone + " is in " + node.parent().name());
			assertEquals(1, node.transform().localScale(), piece.bone + " is scaled");
		}
		assertEquals(Node.NodeTag.HEAD_CHILD, node("armor_head").tag(), "the helmet doesn't turn with the head");
	}

	/** In every frame of every animation, a piece keeps its place on its limb: the limb's move from rest, applied to it. */
	@Test
	public void piecesFollowTheirLimbs() {
		int frames = 0;
		for (var piece : ArmourPieces.Piece.values()) {
			var node = node(piece.bone);
			var limb = node.parent();
			Matrix4fc limbRest = model.defaultPose().get(limb.uuid()).matrix();
			Matrix4fc rest = model.defaultPose().get(node.uuid()).matrix();
			for (var animation : model.animations().entrySet()) {
				for (var frame : animation.getValue().frames()) {
					var limbPose = frame.poses().get(limb.uuid()).matrix();
					var expected = new Matrix4f(limbPose).mul(new Matrix4f(limbRest).invert()).mul(rest);
					var actual = frame.poses().get(node.uuid()).matrix();
					assertTrue(expected.equals(actual, 1e-4f), piece.bone + " in " + animation.getKey() + ": expected\n" + expected + "but\n" + actual);
					frames++;
				}
			}
		}
		assertTrue(frames > 100, "only " + frames + " frames");
	}

	/**
	 * Each material's model of a piece has BIL's cubes for the bone (where the bone's display puts them): the same corners
	 * and the same texture pixels. BIL's model has iron, with the leggings beside the rest on one 128 x 32 texture; ours
	 * have vanilla's 64 x 32 textures. A second layer (leather's overlay) repeats the cubes a little further out.
	 */
	@Test
	public void modelsMatchBils() throws Exception {
		var field = ResourcePackUtil.class.getDeclaredField("data");
		field.setAccessible(true);
		@SuppressWarnings("unchecked")
		var data = (Map<Identifier, byte[]>) field.get(null);
		for (var piece : ArmourPieces.Piece.values()) {
			var node = node(piece.bone);
			var path = "assets/bil/models/item/war_drummer/war_drummer_" + node.uuid() + ".json";
			var bytes = data.entrySet().stream().filter(e -> e.getKey().getPath().equals(path)).findFirst().orElseThrow().getValue();
			var bil = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
			boolean leggings = piece.slot == EquipmentSlot.LEGS;
			for (var material : MATERIALS) {
				var ours = read("models/item/war_drummer/armour/" + material + "/" + piece.bone + ".json");
				assertEquals(bil.get("display"), ours.get("display"), piece.bone);
				var bilElements = bil.getAsJsonArray("elements");
				var ourElements = ours.getAsJsonArray("elements");
				int layers = ours.getAsJsonObject("textures").size() - 1;
				assertEquals(bilElements.size() * layers, ourElements.size(), material + " " + piece.bone);
				assertEquals("minecraft:entity/equipment/" + (leggings ? "humanoid_leggings/" : "humanoid/") + material,
						ours.getAsJsonObject("textures").get("0").getAsString());
				for (int i = 0; i < ourElements.size(); i++) {
					var b = bilElements.get(i % bilElements.size()).getAsJsonObject();
					var o = ourElements.get(i).getAsJsonObject();
					float gap = i / bilElements.size() * 0.05f;
					String what = material + " " + piece.bone + " cube " + i;
					assertVector(b.getAsJsonArray("from"), o.getAsJsonArray("from"), -gap, what + " from");
					assertVector(b.getAsJsonArray("to"), o.getAsJsonArray("to"), gap, what + " to");
					for (var face : List.of("north", "east", "south", "west", "up", "down")) {
						var bu = b.getAsJsonObject("faces").getAsJsonObject(face).getAsJsonArray("uv");
						var ou = o.getAsJsonObject("faces").getAsJsonObject(face).getAsJsonArray("uv");
						for (int k = 0; k < 4; k++) {
							// in texture pixels
							float bilPx = bu.get(k).getAsFloat() * (k % 2 == 0 ? 128 : 32) / 16 - (k % 2 == 0 && leggings ? 64 : 0);
							float ourPx = ou.get(k).getAsFloat() * (k % 2 == 0 ? 64 : 32) / 16;
							assertEquals(bilPx, ourPx, 1e-4f, what + " " + face + " uv");
						}
					}
				}
			}
		}
	}

	private static void assertVector(JsonArray expected, JsonArray actual, float offset, String what) {
		for (int k = 0; k < 3; k++) {
			assertEquals(expected.get(k).getAsFloat() + offset, actual.get(k).getAsFloat(), 1e-4f, what);
		}
	}

	private static JsonObject read(String path) throws IOException {
		var in = ArmourPiecesTest.class.getResourceAsStream("/assets/nether_rifts/" + path);
		assertNotNull(in, "no " + path);
		try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		}
	}

	/** Every vanilla armour material has a model of each piece; the turtle shell only a helmet; an elytra none. */
	@Test
	public void everyVanillaMaterialHasModels() {
		for (var material : MATERIALS) {
			for (var piece : ArmourPieces.Piece.values()) {
				assertNotNull(ArmourPieces.model("war_drummer", material, piece), material + " " + piece.bone);
			}
		}
		assertNotNull(ArmourPieces.model("war_drummer", "turtle_scute", ArmourPieces.Piece.HEAD));
		assertNull(ArmourPieces.model("war_drummer", "turtle_scute", ArmourPieces.Piece.LEGS), "turtle leggings");
		assertNull(ArmourPieces.model("war_drummer", "elytra", ArmourPieces.Piece.CHEST), "an elytra has a chestplate model");
	}
}
