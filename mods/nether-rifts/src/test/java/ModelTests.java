import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tomalbrc.bil.core.model.Node;
import de.tomalbrc.bil.file.loader.BbModelLoader;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import nu.metacraft.nether_rifts.NetherRifts;
import nu.metacraft.nether_rifts.mob.WarGhast;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated models load in BIL with the bones and animations the mobs play; the Grenadier's goggles are an item
 * model, with its texture.
 */
public class ModelTests {

	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	static void assertModel(String name, Set<String> bones, Set<String> animations) {
		var model = BbModelLoader.load(NetherRifts.getID(name));
		assertTrue(model.animations().keySet().containsAll(animations), name + " animations: " + model.animations().keySet());
		var names = model.nodeMap().values().stream().map(Node::name).collect(Collectors.toSet());
		assertTrue(names.containsAll(bones), name + " bones: " + names + ", expected " + bones);
	}

	@Test
	public void warDrummer() {
		assertModel("war_drummer", Set.of("body", "head", "left_arm", "right_arm", "left_leg", "right_leg", "drum", "stick", "stick2"),
			Set.of("idle", "walk", "beat", "big_beat"));
	}

	/**
	 * The Grenadier on foot is a vanilla piglin: its goggles are the artist's, an item model in the mod's resource pack
	 * (tools/bbmodel_to_item.py) on its own texture, drawn on the head by its head display.
	 */
	@Test
	public void bombardierHelmet() throws IOException {
		var item = json("/assets/nether_rifts/items/bombardier_helmet.json").getAsJsonObject("model");
		assertEquals("nether_rifts:item/bombardier_helmet", item.get("model").getAsString());
		var model = itemModel("bombardier_helmet", "nether_rifts:item/bombardier_helmet", Set.of("strap", "left lens", "right lens"));
		assertTrue(model.getAsJsonObject("display").has("head"), "the goggles have no head display");
	}

	/**
	 * An item model in the mod's pack, its elements those named (within the -16..32 models allow), on one texture of
	 * the mod's, which exists.
	 */
	static JsonObject itemModel(String name, String texture, Set<String> elements) throws IOException {
		var model = json("/assets/nether_rifts/models/item/" + name + ".json");
		for (var t : model.getAsJsonObject("textures").entrySet()) {
			assertEquals(texture, t.getValue().getAsString(), name + "'s texture " + t.getKey());
		}
		try (var png = NetherRifts.class.getResourceAsStream("/assets/nether_rifts/textures/" + texture.split(":")[1] + ".png")) {
			assertNotNull(png, "no texture " + texture);
		}
		var names = new java.util.HashSet<String>();
		for (var element : model.getAsJsonArray("elements")) {
			var e = element.getAsJsonObject();
			names.add(e.get("name").getAsString());
			for (var corner : List.of("from", "to")) {
				for (var v : e.getAsJsonArray(corner)) {
					assertTrue(v.getAsDouble() >= -16 && v.getAsDouble() <= 32, name + ": " + e.get("name") + " " + corner + " out of bounds");
				}
			}
		}
		assertEquals(elements, names, name + "'s elements");
		return model;
	}

	static JsonObject json(String path) throws IOException {
		try (var in = NetherRifts.class.getResourceAsStream(path)) {
			assertNotNull(in, "no " + path);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	/**
	 * The artist's Chained Ghast, as tools/import_ghast.py brings it in: the ghast, its faces and deck, each crew figure
	 * with the markers WarGhast places them by, the held-item markers and the spear piglin's armour. No bone BIL would
	 * turn with the ghast's look (a name starting with "head").
	 */
	@Test
	public void warGhast() {
		var bones = new java.util.HashSet<>(Set.of("construct", "ghast", "body", "face_sad", "face_shooting", "platform", "deck", "magma_bomb",
			"item_right", "item_left", "item_head", "armor_chest", "armor_legs", "armor_feet_left"));
		IntStream.rangeClosed(1, 9).forEach(i -> bones.add("tentacle" + i));
		for (var crew : WarGhast.Crew.values()) {
			bones.addAll(Set.of(crew.figure, crew.figure + "_feet", crew.figure + "_facing"));
		}
		assertModel("war_ghast", bones, Set.of(WarGhast.IDLE, WarGhast.SHOOT, WarGhast.DIVE, WarGhast.CLIMB, WarGhast.DROP_BOMB));
		var model = BbModelLoader.load(NetherRifts.getID("war_ghast"));
		for (var node : model.nodeMap().values()) {
			assertFalse(node.tag().isHead(), node.name() + " turns with the ghast's look");
		}
	}

	/**
	 * The import makes each animation but idle whole (BIL plays one at a time over every bone): shoot, dive, climb and
	 * drop_bomb key every channel idle does, and a bone they don't move themselves (a tentacle, the drummer's arm) with
	 * idle's very keys; drop_bomb is idle's length, so it goes in step with it. Shoot swaps the faces at its shot
	 * ({@link WarGhast#SHOT_FRAME}); idle keeps the shooting face shrunk away.
	 */
	@Test
	public void warGhastAnimationsOverIdle() throws IOException {
		var file = json("/model/nether_rifts/war_ghast.bbmodel");
		var animations = new java.util.HashMap<String, JsonObject>();
		file.getAsJsonArray("animations").forEach(a -> animations.put(a.getAsJsonObject().get("name").getAsString(), a.getAsJsonObject().getAsJsonObject("animators")));
		var idle = animations.get(WarGhast.IDLE);
		for (var name : List.of(WarGhast.SHOOT, WarGhast.DIVE, WarGhast.CLIMB, WarGhast.DROP_BOMB)) {
			var animation = animations.get(name);
			for (var bone : idle.entrySet()) {
				var mine = animation.getAsJsonObject(bone.getKey());
				assertNotNull(mine, name + " has no keys for " + bone.getValue().getAsJsonObject().get("name"));
				assertTrue(channels(mine).containsAll(channels(bone.getValue().getAsJsonObject())), name + " leaves channels of "
					+ bone.getValue().getAsJsonObject().get("name") + " unkeyed");
			}
			for (var bone : idle.entrySet()) {
				var boneName = bone.getValue().getAsJsonObject().get("name").getAsString();
				if (boneName.equals("tentacle1") || boneName.equals("drummer_right_arm")) {
					assertEquals(keys(bone.getValue().getAsJsonObject()), keys(animation.getAsJsonObject(bone.getKey())), name + " moves " + boneName + " otherwise than idle");
				}
			}
		}
		var model = BbModelLoader.load(NetherRifts.getID("war_ghast"));
		var ids = new java.util.HashMap<String, java.util.UUID>();
		model.nodeMap().values().forEach(node -> ids.put(node.name(), node.uuid()));
		var idleFrames = model.animations().get(WarGhast.IDLE);
		assertEquals(idleFrames.frames().length, model.animations().get(WarGhast.DROP_BOMB).frames().length, "the bombing isn't as long as idle");
		var shoot = model.animations().get(WarGhast.SHOOT);
		assertEquals(0, scale(idleFrames, 0, ids.get(WarGhast.SHOOTING_FACE)), 1e-4, "idle shows the shooting face");
		assertEquals(1, scale(idleFrames, 0, ids.get(WarGhast.FACE)), 0.05, "idle hides the sad face");
		assertEquals(0, scale(shoot, 0, ids.get(WarGhast.SHOOTING_FACE)), 1e-4, "shoot starts with the shooting face");
		assertEquals(1, scale(shoot, WarGhast.SHOT_FRAME, ids.get(WarGhast.SHOOTING_FACE)), 0.05, "no shooting face at the shot");
		assertEquals(0, scale(shoot, WarGhast.SHOT_FRAME, ids.get(WarGhast.FACE)), 1e-4, "the sad face shows at the shot");
	}

	private static Set<String> channels(JsonObject animator) {
		var out = new java.util.HashSet<String>();
		if (animator.has("keyframes")) {
			animator.getAsJsonArray("keyframes").forEach(k -> out.add(k.getAsJsonObject().get("channel").getAsString()));
		}
		return out;
	}

	/** An animator's keys, without their ids, in order. */
	private static List<String> keys(JsonObject animator) {
		if (!animator.has("keyframes")) {
			return List.of();
		}
		return animator.getAsJsonArray("keyframes").asList().stream().map(k -> {
			var key = k.getAsJsonObject().deepCopy();
			key.remove("uuid");
			return key.toString();
		}).sorted().toList();
	}

	/** A bone's scale (relative to its size at rest) in a frame of an animation. */
	private static float scale(de.tomalbrc.bil.core.model.Animation animation, int frame, java.util.UUID bone) {
		var model = BbModelLoader.load(NetherRifts.getID("war_ghast"));
		float rest = model.defaultPose().get(bone).scale().x();
		return animation.frames()[frame].poses().get(bone).scale().x() / rest;
	}

	/** WarGhast.DECK_DROP is how far the model's lowest cube (the deck's underside) hangs below the ghast's feet. */
	@Test
	public void warGhastDeckDrop() throws IOException {
		double lowest = Double.MAX_VALUE;
		for (var element : json("/model/nether_rifts/war_ghast.bbmodel").getAsJsonArray("elements")) {
			var e = element.getAsJsonObject();
			double inflate = e.has("inflate") ? e.get("inflate").getAsDouble() : 0;
			lowest = Math.min(lowest, e.getAsJsonArray("from").get(1).getAsDouble() - inflate);
		}
		assertEquals(-WarGhast.DECK_DROP * 16, lowest, 1e-6, "the deck's underside");
	}

	/** The deck's floor (its widest cube) is WarGhast's deck hitbox: DECK_WIDTH square, DECK_THICKNESS thick, at the bottom. */
	@Test
	public void warGhastDeckFloor() throws IOException {
		var model = json("/model/nether_rifts/war_ghast.bbmodel");
		var deck = model.getAsJsonArray("groups").asList().stream().map(g -> g.getAsJsonObject())
			.filter(g -> g.get("name").getAsString().equals("deck")).findFirst().orElseThrow().get("uuid").getAsString();
		var cubes = new java.util.HashSet<String>();
		collectCubes(model.getAsJsonArray("outliner"), deck, false, cubes);
		double[] floor = null;
		for (var element : model.getAsJsonArray("elements")) {
			var e = element.getAsJsonObject();
			if (!cubes.contains(e.get("uuid").getAsString())) {
				continue;
			}
			var from = e.getAsJsonArray("from");
			var to = e.getAsJsonArray("to");
			double[] box = {from.get(0).getAsDouble(), from.get(1).getAsDouble(), to.get(0).getAsDouble(), to.get(1).getAsDouble(),
				from.get(2).getAsDouble(), to.get(2).getAsDouble()};
			if (floor == null || box[2] - box[0] > floor[2] - floor[0]) {
				floor = box;
			}
		}
		assertNotNull(floor, "the deck has no cubes");
		assertEquals(WarGhast.DECK_WIDTH * 16, floor[2] - floor[0], 1e-6, "the deck floor's width");
		assertEquals(WarGhast.DECK_WIDTH * 16, floor[5] - floor[4], 1e-6, "the deck floor's depth");
		assertEquals(WarGhast.DECK_THICKNESS * 16, floor[3] - floor[1], 1e-6, "the deck floor's thickness");
		assertEquals(-WarGhast.DECK_DROP * 16, floor[1], 1e-6, "the deck floor's underside");
	}

	/** The cube uuids directly in the outliner group {@code uuid}. */
	private static void collectCubes(com.google.gson.JsonArray nodes, String uuid, boolean in, java.util.Set<String> out) {
		for (var n : nodes) {
			if (n.isJsonPrimitive()) {
				if (in) {
					out.add(n.getAsString());
				}
			} else {
				var o = n.getAsJsonObject();
				collectCubes(o.getAsJsonArray("children"), uuid, o.get("uuid").getAsString().equals(uuid), out);
			}
		}
	}

	/** Each bone is a display entity for every player in view: keep the war ghast's count in check. */
	@Test
	public void warGhastBoneBudget() {
		var model = BbModelLoader.load(NetherRifts.getID("war_ghast"));
		long bones = model.nodeMap().values().stream().filter(node -> node.modelData() != null).count();
		assertTrue(bones <= 58, "war_ghast has " + bones + " bones");
	}
}
