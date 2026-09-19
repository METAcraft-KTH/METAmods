package nu.metacraft.rivals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Which vanilla scoreboard team each of the two sides is, by name.
 *
 * <p>A side in this mod is a {@link PaintColor} — a colour, a bar, an ink — and the thing that puts a
 * player on one is a plain {@code /team join}. The two are tied together by the team's <em>name</em>, and
 * the name is configurable, because a server that already runs teams called {@code red} and {@code blue}
 * (or {@code data-2026} and {@code it-2026}, or two house names) should not have to keep a second pair
 * called {@code main.data} and {@code main.it} beside them.
 *
 * <p>{@code config/rivals-paint/teams.json}:
 *
 * <pre>{"teams": {"data": "main.data", "it": "main.it"}}</pre>
 *
 * <p>The keys are the two colour slots and never change; the values are the scoreboard team names those
 * slots use, and default to the slot's own id, which is what the mod did before the file existed. Read on
 * server start and again on {@code /rivals reload}, the same as the unpaintable list, and written with
 * its own {@code _help} the first time there is no file — json has no comments.
 *
 * <p>Everything that asks "which side is this player on" goes through {@link PaintColor#byTeam}, which
 * asks this. Nothing else needs to know the names are configurable.
 */
public final class TeamNames {
	/** The configured name per slot. Absent means {@code main.} plus the slot's id, which is the default. */
	private static final Map<PaintColor, String> NAMES = new EnumMap<>(PaintColor.class);
	/** What a side's team is called when the file says nothing: MAIN's own {@code main.data} and {@code main.it}. */
	public static final String DEFAULT_PREFIX = "main.";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String HELP = "Which vanilla scoreboard team each side of a match is. The keys are "
			+ "the two colour slots and do not change; each value is the name of the scoreboard team that slot "
			+ "uses, so a server with teams of its own can point a slot at one instead of keeping a second pair. "
			+ "Players join a side with /team join <name>. Run /rivals setup to create any that do not exist and "
			+ "/rivals reload after editing this file.";

	private TeamNames() {}

	public static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(Rivals.MOD_ID).resolve("teams.json");
	}

	/** Read the names on start, writing the file with its {@code _help} the first time there is none. */
	public static void init() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> reload());
	}

	/** Re-read {@code config/rivals-paint/teams.json}. Returns how many names are off their default. */
	public static int reload() {
		return load(configPath());
	}

	/**
	 * The same from a given file. The names are one table for the whole server, so a test that changes
	 * them puts them back itself — {@link #resetAll()}.
	 */
	public static int load(Path path) {
		NAMES.clear();
		if (!Files.isRegularFile(path)) {
			write(path);
			return 0;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (!root.isJsonObject()) {
				Rivals.LOGGER.warn("[{}] {}: not a json object, the default team names stand", Rivals.MOD_ID, path);
				return 0;
			}
			JsonElement teams = root.getAsJsonObject().get("teams");
			if (teams == null || !teams.isJsonObject()) return 0;
			for (Map.Entry<String, JsonElement> entry : teams.getAsJsonObject().entrySet()) {
				Optional<PaintColor> slot = PaintColor.byId(entry.getKey());
				if (slot.isEmpty()) {
					Rivals.LOGGER.warn("[{}] {}: there is no side called \"{}\", ignored — the sides are {}",
							Rivals.MOD_ID, path, entry.getKey(), PaintColor.idList());
					continue;
				}
				if (!entry.getValue().isJsonPrimitive()) continue;
				String name = entry.getValue().getAsString().trim();
				// An empty name would be a team nobody can join and a lookup that matched the wrong thing.
				if (name.isEmpty()) {
					Rivals.LOGGER.warn("[{}] {}: \"{}\" has no team name, left at \"{}\"",
							Rivals.MOD_ID, path, entry.getKey(), slot.get().id);
					continue;
				}
				set(slot.get(), name);
			}
		} catch (IOException | RuntimeException e) {
			Rivals.LOGGER.warn("[{}] {}: {}; the default team names stand", Rivals.MOD_ID, path, e);
			NAMES.clear();
			return 0;
		}
		int renamed = renamed().size();
		if (renamed > 0) Rivals.LOGGER.info("[{}] team names from {}: {}", Rivals.MOD_ID, path, describe());
		return renamed;
	}

	/** The starting file: the defaults written out, so the names to change are there to be seen. */
	private static void write(Path path) {
		JsonObject root = new JsonObject();
		root.addProperty("_help", HELP);
		JsonObject teams = new JsonObject();
		for (PaintColor slot : PaintColor.values()) teams.addProperty(slot.id, nameOf(slot));
		root.add("teams", teams);
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

	/** The scoreboard team name this side uses. */
	public static String nameOf(PaintColor slot) {
		return NAMES.getOrDefault(slot, DEFAULT_PREFIX + slot.id);
	}

	/** Which side a scoreboard team name is, if it is one of ours. */
	public static Optional<PaintColor> slotOf(String teamName) {
		for (PaintColor slot : PaintColor.values()) {
			if (nameOf(slot).equals(teamName)) return Optional.of(slot);
		}
		return Optional.empty();
	}

	/** Every side's team name, comma-separated, for the messages that tell a player what to join. */
	public static String nameList() {
		List<String> names = new ArrayList<>();
		for (PaintColor slot : PaintColor.values()) names.add(nameOf(slot));
		return String.join(", ", names);
	}

	/** {@code DATA → red, IT → blue}, for the log line and {@code /rivals reload}. */
	public static String describe() {
		List<String> parts = new ArrayList<>();
		for (PaintColor slot : PaintColor.values()) parts.add(slot.displayName + " → " + nameOf(slot));
		return String.join(", ", parts);
	}

	/** The sides whose team name is off the default. */
	public static List<PaintColor> renamed() {
		List<PaintColor> out = new ArrayList<>();
		for (PaintColor slot : PaintColor.values()) {
			if (!nameOf(slot).equals(DEFAULT_PREFIX + slot.id)) out.add(slot);
		}
		return out;
	}

	/**
	 * Point one side at a scoreboard team name. Public for the tests, which set the names through this
	 * rather than through a file: the file is read once per server, and a test that wrote one would be
	 * racing every other test in the batch.
	 *
	 * <p>Two sides may not share a name — then a team would be both sides at once and
	 * {@link #slotOf} would have to guess — so the second one is refused. Returns whether it took.
	 */
	public static boolean set(PaintColor slot, String teamName) {
		for (PaintColor other : PaintColor.values()) {
			if (other != slot && nameOf(other).equals(teamName)) {
				Rivals.LOGGER.warn("[{}] {} is already the team for {}, so {} keeps \"{}\"",
						Rivals.MOD_ID, teamName, other.displayName, slot.displayName, nameOf(slot));
				return false;
			}
		}
		NAMES.put(slot, teamName);
		return true;
	}

	/** Every side back to its own id. For the tests, and for a config that turned out to be unreadable. */
	public static void resetAll() {
		NAMES.clear();
	}
}
