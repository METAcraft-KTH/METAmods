package nu.metacraft.rivals.paint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import nu.metacraft.rivals.Rivals;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Blocks paint never sticks to — the grates, bars and panes Splatoon's own stages are full of, where
 * ink falls straight through rather than covering anything.
 *
 * <p>Two sources, and a block in either is unpaintable:
 *
 * <ul>
 *   <li>the block tag {@code #rivals-paint:unpaintable}, shipped with the mod's defaults and
 *       overridable by a data pack like any other tag;</li>
 *   <li>the list in {@code config/rivals-paint/unpaintable.json}, read on server start and by
 *       {@code /rivals reload}, for an arena builder who wants one more block out without writing a
 *       data pack.</li>
 * </ul>
 *
 * <p>The check runs in {@link Painter#paintable} — so a shot, a splash, a roll and the charger's line
 * all skip such a block without a special case of their own — and again in {@link PaintDisplays#paint},
 * which is the path a face that is not full takes: a grate is exactly that shape, so that is the branch
 * that would otherwise have hung quads on it.
 *
 * <p>The config is a file of registry ids rather than a tag because it is the thing a server operator
 * edits between rounds; the tag is the thing the mod ships and a map maker overrides. Ids that no
 * block answers to are warned about once and ignored, so a typo does not take a start down, and the
 * file is written with its own {@code _help} on first start the way the ovvar module's configs are.
 */
public final class Unpaintable {
	/** The tag paint refuses. Shipped at {@code data/rivals-paint/tags/block/unpaintable.json}. */
	public static final TagKey<Block> TAG = TagKey.create(Registries.BLOCK, Rivals.id("unpaintable"));

	/** What the config file says, as registry ids: resolved per test, since a block may come from a mod. */
	private static final Set<Identifier> LISTED = new LinkedHashSet<>();

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String HELP = "Blocks paint never sticks to, on top of the #rivals-paint:unpaintable "
			+ "block tag (which already holds the grates, bars, panes, rails, ladders, scaffolding, chains and "
			+ "trapdoors). Add full registry ids here, e.g. \"minecraft:copper_grate\", and run /rivals reload. "
			+ "An id no block answers to is warned about in the log and ignored.";

	private Unpaintable() {}

	public static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(Rivals.MOD_ID).resolve("unpaintable.json");
	}

	/** Read the list on start, and write the file with its {@code _help} the first time there is none. */
	public static void init() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> reload());
	}

	/** Re-read {@code config/rivals-paint/unpaintable.json}. Returns how many ids it now holds. */
	public static int reload() {
		return load(configPath());
	}

	/**
	 * The same from a given file, which is what the tests use: the list is one table for the whole
	 * server, so a test that changes it puts it back itself.
	 */
	public static int load(Path path) {
		LISTED.clear();
		if (!Files.isRegularFile(path)) {
			write(path);
			return 0;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (!root.isJsonObject()) {
				Rivals.LOGGER.warn("[{}] {}: not a json object, no extra unpaintable blocks", Rivals.MOD_ID, path);
				return 0;
			}
			JsonElement blocks = root.getAsJsonObject().get("blocks");
			if (blocks == null || !blocks.isJsonArray()) return 0;
			for (JsonElement element : blocks.getAsJsonArray()) {
				if (!element.isJsonPrimitive()) continue;
				String id = element.getAsString();
				Identifier parsed = Identifier.tryParse(id);
				if (parsed == null) {
					Rivals.LOGGER.warn("[{}] {}: \"{}\" is not a block id, ignored", Rivals.MOD_ID, path, id);
					continue;
				}
				// Not resolved to a Block here: a modded block may register after this runs, and the
				// registry lookup at test time costs nothing measurable beside a splat's block writes.
				if (!BuiltInRegistries.BLOCK.containsKey(parsed)) {
					Rivals.LOGGER.warn("[{}] {}: no block called \"{}\", ignored", Rivals.MOD_ID, path, id);
					continue;
				}
				LISTED.add(parsed);
			}
		} catch (IOException | RuntimeException e) {
			Rivals.LOGGER.warn("[{}] {}: {}; no extra unpaintable blocks", Rivals.MOD_ID, path, e);
			return 0;
		}
		if (!LISTED.isEmpty()) {
			Rivals.LOGGER.info("[{}] {} extra unpaintable blocks from {}", Rivals.MOD_ID, LISTED.size(), path);
		}
		return LISTED.size();
	}

	/** The starting file: an empty list and the note that says what to put in it. */
	private static void write(Path path) {
		JsonObject root = new JsonObject();
		root.addProperty("_help", HELP);
		root.add("blocks", new JsonArray());
		try {
			Path parent = path.getParent();
			if (parent != null) Files.createDirectories(parent);
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
		} catch (IOException e) {
			Rivals.LOGGER.warn("[{}] could not write {}: {}", Rivals.MOD_ID, path, e.toString());
		}
	}

	/** The ids the config adds, for a command that reports them and for the tests. */
	public static Set<Identifier> listed() {
		return Set.copyOf(LISTED);
	}

	/** For tests: the config list back to empty, leaving the tag alone. */
	public static void clearListed() {
		LISTED.clear();
	}

	/** For tests: put one id on the config list without a file. */
	public static void list(Identifier id) {
		LISTED.add(id);
	}

	/** Is this block one paint never sticks to — by tag or by the config list? */
	public static boolean test(BlockState state) {
		if (state.is(TAG)) return true;
		return !LISTED.isEmpty() && LISTED.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
	}
}
