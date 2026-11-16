import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import org.junit.jupiter.api.Test;
import se.metacraft.ovvarclient.combo.ComboAssets;
import se.metacraft.ovvarclient.ClientCombos;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client builds a combination's equipment definition from its name alone, and it is byte for byte what
 * the server's combination pack holds. {@code goldens.json} is written by ovvar's game test
 * (ComboAssetsParityTests, which also compares it to ComboPack's output live): every chapter, ovve and frack,
 * rolled down, overlapping cells, the seat, given-only designs and every instant design.
 */
public class ComboAssetsTest {
	private static final Path GOLDENS = Paths.get("../ovvar/src/combo/goldens.json");
	private static final String PREFIX = "assets/ovvar/equipment/";

	private static Map<String, JsonElement> goldens() throws IOException {
		JsonObject root = JsonParser.parseString(Files.readString(GOLDENS, StandardCharsets.UTF_8)).getAsJsonObject();
		return root.asMap();
	}

	private static String assetPath(String packPath) {
		assertTrue(packPath.startsWith(PREFIX) && packPath.endsWith(".json"), packPath);
		return packPath.substring(PREFIX.length(), packPath.length() - ".json".length());
	}

	@Test
	void everyGoldenCombinationBuildsByteForByteFromItsName() throws IOException {
		Map<String, JsonElement> goldens = goldens();
		Set<String> chapters = new HashSet<>(), chestChapters = new HashSet<>(), materials = new HashSet<>();
		boolean frack = false, rolled = false, seat = false, overlap = false;
		for (Map.Entry<String, JsonElement> golden : goldens.entrySet()) {
			String path = assetPath(golden.getKey());
			assertEquals(Optional.of(golden.getValue().getAsString()), ComboAssets.jsonFor(path), path);
			if (path.startsWith(ComboAssets.CHEST + "/")) {
				String[] parts = path.split("/");
				chestChapters.add(parts[1]);
				materials.add(parts[2]);
				continue;
			}
			chapters.add(path.substring(0, path.indexOf('/')));
			frack |= path.startsWith("media/") || path.startsWith("b/");
			rolled |= path.contains("_nercabbad/");
			seat |= path.contains("seat.");
			overlap |= path.contains("back_big.") && path.contains("back_top_left.");
		}
		assertTrue(chapters.size() >= 18, "the goldens cover only " + chapters);
		assertTrue(frack && rolled && seat && overlap, "the goldens miss a case: frack " + frack + ", rolled " + rolled + ", seat " + seat + ", overlap " + overlap);
		// the chestplate wrap with patches: every material the server composites, on every chapter with a top
		assertEquals(Set.of("chainmail", "copper", "diamond", "gold", "iron", "netherite"), materials, "the goldens' chestplate wraps");
		assertTrue(chestChapters.size() >= 17, "the goldens' chestplate wraps cover only " + chestChapters);
	}

	/** A chestplate wrap with patches: the top's cloth and patches and its preview, then the chestplate on top. */
	@Test
	void aChestplateWrapWithPatchesIsTheTopsCombinationUnderTheArmour() {
		String json = ComboAssets.jsonFor("chest/data/iron/back_top_left.tmeit-marshal-back_big.kth").orElseThrow();
		List<String> textures = new java.util.ArrayList<>();
		for (JsonElement layer : JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("layers").getAsJsonArray("humanoid")) {
			textures.add(layer.getAsJsonObject().get("texture").getAsString() + (layer.getAsJsonObject().has("dyeable") ? " (dyed)" : ""));
		}
		assertEquals(List.of("ovvar:data/top", "ovvar:patch/back_big/kth", "ovvar:patch/back_top_left/tmeit-marshal", "ovvar:patch/preview_top (dyed)", "minecraft:iron"), textures);
		assertEquals("chest/data/iron/back_big.kth", ComboAssets.chestPath("data", "iron", "back_big.kth"));
		assertEquals("chest/data/iron", ComboAssets.chestPath("data", "iron", ""));
	}

	/** And the definition parses into what the client draws: one layer per texture, the preview dyeable. */
	@Test
	void everyGoldenParsesIntoTheClientsEquipmentInfo() throws IOException {
		for (Map.Entry<String, JsonElement> golden : goldens().entrySet()) {
			String path = assetPath(golden.getKey());
			Optional<EquipmentClientInfo> built = ClientCombos.build(path);
			assertTrue(built.isPresent(), path);
			EquipmentClientInfo expected = EquipmentClientInfo.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(golden.getValue().getAsString())).getOrThrow();
			assertEquals(expected, built.get(), path);
		}
	}

	@Test
	void namesThatAreNotCombinationsBuildNothing() {
		for (String path : List.of("data/top", "feet/iron", "chest/data/iron", "data/top/", "data/top_nercabbad/front_top_left.itk",
				"data/bottom/front_top_left.itk", "data/top/front_top_left", "data/top/front_top_left.", "data/top/.itk",
				"data/top/no_such_cell.itk", "/top/front_top_left.itk", "data/top/front_top_left.itk/x", "data/sleeve/front_top_left.itk",
				"chest/data/iron/leg_out_top_r.itk", "chest/data/iron/", "chest//iron/front_top_left.itk", "chest/data//front_top_left.itk",
				"chest/data/iron/front_top_left.itk/x", "chest/data/iron/no_such_cell.itk", "chests/data/iron/front_top_left.itk", "data/chest/iron/front_top_left.itk")) {
			assertEquals(Optional.empty(), ComboAssets.jsonFor(path), path);
		}
	}

	/** A name the client cannot read falls back to the garment one level up, never to nothing it could draw instead. */
	@Test
	void anUnreadableNameFallsBackToTheGarmentWithoutPatches() {
		EquipmentClientInfo garment = EquipmentClientInfo.builder().build();
		Map<ResourceKey<EquipmentAsset>, EquipmentClientInfo> loaded = Map.of(key("data/top"), garment);
		assertSame(garment, ClientCombos.buildOrFallBack(key("data/top/no_such_cell.itk"), loaded));
		assertSame(EquipmentAssetManager.MISSING, ClientCombos.buildOrFallBack(key("nothing"), loaded));
		assertSame(EquipmentAssetManager.MISSING, ClientCombos.buildOrFallBack(key("feet/iron"), loaded));
		EquipmentClientInfo built = ClientCombos.buildOrFallBack(key("data/top/front_top_left.itk"), loaded);
		assertNotSame(garment, built);
		assertNotSame(EquipmentAssetManager.MISSING, built);
	}

	/** A chestplate wrap the client cannot read falls back to the wrap without patches (what a vanilla client is sent). */
	@Test
	void anUnreadableChestplateWrapFallsBackToTheWrapWithoutPatches() {
		EquipmentClientInfo wrap = EquipmentClientInfo.builder().build();
		Map<ResourceKey<EquipmentAsset>, EquipmentClientInfo> loaded = Map.of(key("chest/data/iron"), wrap);
		assertSame(wrap, ClientCombos.buildOrFallBack(key("chest/data/iron/no_such_cell.itk"), loaded));
		assertSame(wrap, ClientCombos.buildOrFallBack(key("chest/data/iron/leg_out_top_r.itk"), loaded));
		assertEquals(ClientCombos.build("chest/data/iron/front_top_left.itk").orElseThrow(), ClientCombos.buildOrFallBack(key("chest/data/iron/front_top_left.itk"), loaded));
		assertSame(EquipmentAssetManager.MISSING, ClientCombos.buildOrFallBack(key("chest/data/iron/front_top_left.itk"), Map.of()));
		assertSame(EquipmentAssetManager.MISSING, ClientCombos.buildOrFallBack(key("chest/data/gold/front_top_left.itk"), loaded));
	}

	/** Without the server's Polymer pack (the garment is not loaded) nothing is built: the textures would be missing. */
	@Test
	void withoutTheGarmentLoadedNothingIsBuilt() {
		assertSame(EquipmentAssetManager.MISSING, ClientCombos.buildOrFallBack(key("data/top/front_top_left.itk"), Map.of()));
	}

	/**
	 * The mixin itself (fabric-loader-junit runs the tests under Knot with the mod's mixins): an asset
	 * manager holding only the base garment (as the Polymer pack would) hands out a built combination for a
	 * combination's name, the same object the next time, and still MISSING for anything else. Its reload
	 * marks the hook ready, which is what lets the client handshake.
	 */
	@Test
	void theAssetManagerBuildsMissingCombinations() throws ReflectiveOperationException {
		EquipmentAssetManager manager = new EquipmentAssetManager();
		Method apply = EquipmentAssetManager.class.getDeclaredMethod("apply", Map.class, ResourceManager.class, ProfilerFiller.class);
		apply.setAccessible(true);
		apply.invoke(manager, Map.of(Identifier.fromNamespaceAndPath("ovvar", "data/top"), EquipmentClientInfo.builder().build()), null, InactiveProfiler.INSTANCE);
		assertTrue(ClientCombos.ready(), "a reload through the asset manager did not mark the hook ready");
		EquipmentClientInfo built = manager.get(key("data/top/front_top_left.itk"));
		assertEquals(ClientCombos.build("data/top/front_top_left.itk").orElseThrow(), built);
		assertSame(built, manager.get(key("data/top/front_top_left.itk")));
		assertSame(EquipmentAssetManager.MISSING, manager.get(ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath("minecraft", "data/top/front_top_left.itk"))));
		assertSame(EquipmentAssetManager.MISSING, manager.get(key("feet")));
	}

	/** The mixin builds a chestplate wrap with patches too, once the wrap without them (the Polymer pack's) is loaded. */
	@Test
	void theAssetManagerBuildsChestplateWrapsWithPatches() throws ReflectiveOperationException {
		EquipmentAssetManager manager = new EquipmentAssetManager();
		Method apply = EquipmentAssetManager.class.getDeclaredMethod("apply", Map.class, ResourceManager.class, ProfilerFiller.class);
		apply.setAccessible(true);
		apply.invoke(manager, Map.of(Identifier.fromNamespaceAndPath("ovvar", "chest/data/copper"), EquipmentClientInfo.builder().build()), null, InactiveProfiler.INSTANCE);
		String path = "chest/data/copper/front_top_left.itk-sleeve_out_top_l.metacraft";
		EquipmentClientInfo built = manager.get(key(path));
		assertEquals(ClientCombos.build(path).orElseThrow(), built);
		assertEquals(5, built.getLayers(EquipmentClientInfo.LayerType.HUMANOID).size(), "cloth, two patches, preview, chestplate: " + built);
		assertSame(built, manager.get(key(path)));
		assertSame(EquipmentAssetManager.MISSING, manager.get(key("chest/data/iron/front_top_left.itk")));
	}

	/**
	 * The combination OvvarClientTests films twice for its stability check (the Data top, four front patches; in the
	 * goldens through ComboAssetsParityTests): every lookup, before and after other combinations are built, hands
	 * the renderer the one object, equal field for field to what the server's pack holds. (The cloud run's
	 * frame-to-frame difference was the mannequin's arm sway, not this asset; this pins that it is not.)
	 */
	@Test
	void theStabilityShotsCombinationIsOneObjectEqualToThePacks() throws ReflectiveOperationException, IOException {
		String path = "data/top/front_top_left.itk-front_top_right.nyckeln0x2-front_low_left.it-front_low_right.data";
		JsonElement golden = goldens().get(PREFIX + path + ".json");
		assertTrue(golden != null, "the goldens lack the stability shot's combination " + path);
		EquipmentClientInfo pack = EquipmentClientInfo.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(golden.getAsString())).getOrThrow();
		EquipmentAssetManager manager = new EquipmentAssetManager();
		Method apply = EquipmentAssetManager.class.getDeclaredMethod("apply", Map.class, ResourceManager.class, ProfilerFiller.class);
		apply.setAccessible(true);
		apply.invoke(manager, Map.of(Identifier.fromNamespaceAndPath("ovvar", "data/top"), EquipmentClientInfo.builder().build()), null, InactiveProfiler.INSTANCE);
		EquipmentClientInfo first = manager.get(key(path));
		assertEquals(pack, first);
		assertEquals(pack.layers(), first.layers());
		for (int frame = 0; frame < 100; frame++) {
			if (frame == 50) manager.get(key("data/top/front_top_left.itk"));
			assertSame(first, manager.get(key(path)), "lookup " + frame + " handed out another object");
		}
	}

	private static ResourceKey<EquipmentAsset> key(String path) {
		return ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath("ovvar", path));
	}

	/**
	 * The protocol pin: a hash of the cell table and of the definitions of a made-up chapter's every cell.
	 * New chapters and designs don't move it (the client takes them as given); a cell added or moved, or a
	 * changed recipe, does. When it fails, bump ComboAssets.PROTOCOL and put the new hash here: a client on
	 * the old protocol then no longer handshakes and is pushed the pack, instead of building it wrong.
	 */
	@Test
	void theProtocolIsPinned() throws NoSuchAlgorithmException {
		StringBuilder all = new StringBuilder(ComboAssets.CELLS.toString()).append(ComboAssets.PIECE_LAYERS);
		for (String piece : ComboAssets.PIECE_LAYERS.keySet()) {
			StringBuilder combo = new StringBuilder();
			for (Map.Entry<String, ComboAssets.Cell> cell : ComboAssets.CELLS.entrySet()) {
				if (!cell.getValue().piece().equals(piece)) continue;
				if (!combo.isEmpty()) combo.append('-');
				combo.append(cell.getKey()).append(".x-y");
			}
			all.append(ComboAssets.jsonFor(ComboAssets.assetPath("x", piece, false, combo.toString())).orElseThrow());
			if (piece.equals("bottom")) all.append(ComboAssets.jsonFor(ComboAssets.assetPath("x", piece, true, combo.toString())).orElseThrow());
			else all.append(ComboAssets.jsonFor(ComboAssets.chestPath("x", "m", combo.toString())).orElseThrow());
		}
		String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(all.toString().getBytes(StandardCharsets.UTF_8)));
		assertEquals(2, ComboAssets.PROTOCOL);
		assertEquals("0b2d4e730066c1c746ab4f0095ef85e8780747df", hash, "the combination recipe changed: bump ComboAssets.PROTOCOL and pin the new hash");
		assertEquals("draws_combos/v2", ComboAssets.CHANNEL_PATH);
	}
}
