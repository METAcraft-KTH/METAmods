import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tomalbrc.bil.core.model.Node;
import de.tomalbrc.bil.file.loader.BbModelLoader;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import se.metacraft.nether_rifts.NetherRifts;
import se.metacraft.nether_rifts.mob.WarGhast;
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
 * The generated models load in BIL with the bones and animations the mobs play; the Grenadier's goggles and gloved arm
 * are item models, with their textures.
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

	static final Set<String> GENERAL_BONES = Set.of("general", "body", "torso", "jacket", "front_curtain", "back_curtain", "head", "left_ear",
		"right_ear", "helmet", "right_arm", "mace", "left_arm", "flagpole", "flag", "left_leg", "right_leg");

	/**
	 * The artist's Piglin General, as tools/import_general.py brings it in at 1.5x: BIL plays one animation over every
	 * bone, so each animation keys them all; his spin turns the root bone in steps BIL's quaternions can follow; and he
	 * stands as tall as a boss should, the flag aside.
	 */
	@Test
	public void piglinGeneral() throws IOException {
		var animations = Set.of("idle", "walk", "attack", "spin_windup", "spin", "dizzy", "roar", "death");
		assertModel("piglin_general", GENERAL_BONES, animations);
		var file = json("/model/nether_rifts/piglin_general.bbmodel");
		var groups = new java.util.HashMap<String, String>();
		file.getAsJsonArray("groups").forEach(g -> groups.put(g.getAsJsonObject().get("uuid").getAsString(), g.getAsJsonObject().get("name").getAsString()));
		assertEquals(GENERAL_BONES, new java.util.HashSet<>(groups.values()), "the model's bones");
		var seen = new java.util.HashSet<String>();
		for (var element : file.getAsJsonArray("animations")) {
			var animation = element.getAsJsonObject();
			var name = animation.get("name").getAsString();
			seen.add(name);
			var keyed = new java.util.HashSet<String>();
			animation.getAsJsonObject("animators").entrySet().forEach(e -> {
				if (channels(e.getValue().getAsJsonObject()).size() > 0) {
					keyed.add(groups.get(e.getKey()));
				}
			});
			assertEquals(GENERAL_BONES, keyed, name + " leaves bones unkeyed");
			if (name.equals("spin") || name.equals("dizzy")) {
				var root = groups.entrySet().stream().filter(g -> g.getValue().equals("general")).findFirst().orElseThrow().getKey();
				var turn = animation.getAsJsonObject("animators").getAsJsonObject(root).getAsJsonArray("keyframes").asList().stream()
					.map(k -> k.getAsJsonObject()).filter(k -> k.get("channel").getAsString().equals("rotation")).toList();
				if (name.equals("spin")) {
					assertTrue(turn.size() >= 4, "spin turns the General in " + turn.size() + " keys");
				}
				double last = 0;
				for (var key : turn) {
					double y = key.getAsJsonArray("data_points").get(0).getAsJsonObject().get("y").getAsDouble();
					assertTrue(Math.abs(y - last) <= 90 + 1e-6, name + " turns " + (y - last) + " degrees between keys");
					last = y;
				}
				assertEquals(360, last, 1e-6, name + " ends a full turn");
			}
		}
		assertEquals(animations, seen, "the model's animations");
		// his height: the highest cube top, but for the flagpole's (the flag stands above him)
		var flag = new java.util.HashSet<String>();
		var flagpole = groups.entrySet().stream().filter(g -> g.getValue().equals("flagpole")).findFirst().orElseThrow().getKey();
		collectSubtreeCubes(file.getAsJsonArray("outliner"), flagpole, false, flag);
		double top = 0;
		for (var element : file.getAsJsonArray("elements")) {
			var e = element.getAsJsonObject();
			if (!flag.contains(e.get("uuid").getAsString())) {
				top = Math.max(top, e.getAsJsonArray("to").get(1).getAsDouble());
			}
		}
		assertTrue(top / 16 >= 3.2 && top / 16 <= 3.4, "the General is " + top / 16 + " blocks tall");
	}

	/** The cube uuids in the outliner group {@code uuid} and all it holds. */
	private static void collectSubtreeCubes(com.google.gson.JsonArray nodes, String uuid, boolean in, java.util.Set<String> out) {
		for (var n : nodes) {
			if (n.isJsonPrimitive()) {
				if (in) {
					out.add(n.getAsString());
				}
			} else {
				var o = n.getAsJsonObject();
				collectSubtreeCubes(o.getAsJsonArray("children"), uuid, in || o.get("uuid").getAsString().equals(uuid), out);
			}
		}
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
	 * The Grenadier's sword: clients see a golden sword in his gloved right arm, a composite of vanilla's golden sword
	 * and the artist's arm and sleeve; through a throw, the arm alone, with the magma bomb till it lets go. The arm's
	 * models are placed over a piglin's arm by their third-person hand displays.
	 */
	@Test
	public void grenadierSword() throws IOException {
		var sword = json("/assets/nether_rifts/items/grenadier_sword.json").getAsJsonObject("model");
		assertEquals("minecraft:composite", sword.get("type").getAsString());
		var parts = sword.getAsJsonArray("models").asList().stream().map(m -> m.getAsJsonObject().get("model").getAsString()).toList();
		assertEquals(List.of("minecraft:item/golden_sword", "nether_rifts:item/bombardier_arm"), parts);
		assertEquals("nether_rifts:item/bombardier_arm_bomb", json("/assets/nether_rifts/items/grenadier_sword_bomb.json").getAsJsonObject("model").get("model").getAsString());
		assertEquals("nether_rifts:item/bombardier_arm", json("/assets/nether_rifts/items/grenadier_sword_thrown.json").getAsJsonObject("model").get("model").getAsString());
		var arm = itemModel("bombardier_arm", "nether_rifts:item/bombardier_arm", Set.of("right_arm", "right_sleeve"));
		var bomb = itemModel("bombardier_arm_bomb", "nether_rifts:item/bombardier_arm", Set.of("right_arm", "right_sleeve", "core", "shell"));
		for (var model : List.of(arm, bomb)) {
			assertTrue(model.getAsJsonObject("display").has("thirdperson_righthand"), "the arm has no right-hand display");
		}
	}

	/**
	 * The magma bomb in flight is the artist's bomb (out of the Grenadier's arm) on its own, its middle at the model's
	 * middle, so a thrown bomb (drawn in the ground display, turned to the camera) is drawn round where it is.
	 */
	@Test
	public void magmaBomb() throws IOException {
		assertEquals("nether_rifts:item/magma_bomb", json("/assets/nether_rifts/items/magma_bomb.json").getAsJsonObject("model").get("model").getAsString());
		var bomb = json("/assets/nether_rifts/models/item/magma_bomb.json");
		assertEquals("nether_rifts:item/magma_bomb_cubes", bomb.get("parent").getAsString());
		var ground = bomb.getAsJsonObject("display").getAsJsonObject("ground");
		assertTrue(ground.getAsJsonArray("scale").get(0).getAsDouble() >= 1, "the bomb is drawn smaller than its model in flight");
		var cubes = itemModel("magma_bomb_cubes", "nether_rifts:item/bombardier_arm", Set.of("core", "shell"));
		var shell = cubes.getAsJsonArray("elements").asList().stream().map(e -> e.getAsJsonObject())
				.filter(e -> e.get("name").getAsString().equals("shell")).findFirst().orElseThrow();
		// the shell turns about x only, so its middle across is where it is drawn
		double middle = (shell.getAsJsonArray("from").get(0).getAsDouble() + shell.getAsJsonArray("to").get(0).getAsDouble()) / 2;
		assertEquals(8, middle, 0.01, "the bomb's middle across");
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
	 * idle's very keys; drop_bomb is idle's length, so it goes in step with it. No animation scales a face (the ghast
	 * hides one and shows the other: BIL eased the artist's scale swap into a shrinking body), and the shot
	 * ({@link WarGhast#SHOT_FRAME}) is while the shooting face shows.
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
		for (var name : List.of(WarGhast.IDLE, WarGhast.SHOOT, WarGhast.DIVE, WarGhast.CLIMB, WarGhast.DROP_BOMB)) {
			var frames = model.animations().get(name);
			for (int frame = 0; frame < frames.frames().length; frame++) {
				for (var face : List.of(WarGhast.FACE, WarGhast.SHOOTING_FACE)) {
					assertEquals(1, scale(frames, frame, ids.get(face)), 1e-4, name + " scales " + face + " at frame " + frame);
				}
			}
		}
		assertTrue(WarGhast.SHOOTING_FACE_FROM <= WarGhast.SHOT_FRAME && WarGhast.SHOT_FRAME < WarGhast.SHOOTING_FACE_UNTIL
				&& WarGhast.SHOOTING_FACE_UNTIL <= shoot.frames().length, "the shot isn't while the shooting face shows");
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
