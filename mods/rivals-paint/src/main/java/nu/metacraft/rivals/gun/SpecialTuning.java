package nu.metacraft.rivals.gun;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import nu.metacraft.rivals.Rivals;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Every number a special is made of, per {@link Special}, live — {@link WeaponTuning} for the thing on
 * F. One instance per special holds a value for every {@link Param}, {@link PaintWeapon#special} reads
 * it at the throw and {@link PaintBall} reads it again at the landing, so a value moved with
 * {@code /rivals tune special <id> <param> <value>} is in the next bomb without a restart.
 *
 * <p>A sheet of its own rather than more {@code special_*} parameters on each weapon, which is what the
 * splat bomb had when it was the only one. Three specials times a dozen numbers on each of three
 * weapons would have been nine columns of the same thing, and every one of them a number a player could
 * set differently for a bomb that is not the weapon's — a splat bomb thrown from a roller is the same
 * splat bomb. The specials are keyed by special; the weapon is no longer part of it.
 *
 * <p>The defaults are the constants the specials were written with — {@link Special}'s own
 * {@code BURST_*} and {@code CURLING_*}, and the {@code Weapon.SPECIAL_*} the splat bomb has always
 * used — read from the code rather than copied. Only what has been changed away from them is written,
 * so a file nobody has touched is {@code {}}.
 */
public final class SpecialTuning {
	/**
	 * The tunable numbers of a special. The id is what a player types and what the json is keyed by.
	 * Not every one applies to every special: only the splat bomb has a fuse, and only the curling bomb
	 * slides, so {@link #applies} decides which of these a special shows.
	 *
	 * <p>Each carries the range it is allowed, for the same reason {@link WeaponTuning.Param}'s do: a
	 * {@code radius} of 500 is a million block writes in one blast, and a {@code slide_ticks} of 100000
	 * is a bomb that paints the level one cell at a time for an hour and a half.
	 */
	public enum Param {
		/** Ink one throw costs, of a hundred-unit tank. Rounded. */
		INK("ink", 0.0, 100.0),
		/** Ticks before this special can be thrown again — its own wait, not the weapon's cooldown. Rounded. */
		COOLDOWN("cooldown", 0.0, 600.0),
		/**
		 * Ticks after the throw before standing in your own paint starts refilling the tank. The special's
		 * own rather than the weapon it was thrown from: most of a tank out at once wants a beat of its own
		 * before it comes back. Rounded.
		 */
		REFILL_DELAY("refill_delay", 0.0, 200.0),
		/** How far the splash reaches where it goes off: 2 is 5×5, 3 is 7×7. Rounded. */
		RADIUS("radius", 0.0, 6.0),
		/** Hearts at the centre of the blast, at its edge, and how far out the edge is. */
		DAMAGE("damage", 0.0, 40.0),
		EDGE_DAMAGE("edge_damage", 0.0, 40.0),
		BLAST("blast", 0.0, 16.0),
		/** Inside this the blast is at full: the falloff starts at the edge of the bomb, not at a point. */
		CORE("core", 0.0, 16.0),
		/** How it leaves the hand: launch speed, fall rate, and degrees of pitch off the crosshair. */
		VELOCITY("velocity", 0.0, 10.0),
		GRAVITY("gravity", -1.0, 1.0),
		PITCH("pitch", -90.0, 90.0),
		/** Ticks before one that has found nothing at all goes off where it is. Rounded, never 0. */
		LIFETIME("lifetime", 1.0, 400.0),
		/** The blob's display size outright, not a multiple of a ball's. */
		SCALE("scale", 0.1, 4.0),
		/** Splat bomb only: ticks between landing and going off. Rounded. */
		FUSE("fuse", 0.0, 200.0),
		/** Curling bomb only: how long the slide lasts at the most. Rounded, never 0. */
		SLIDE_TICKS("slide_ticks", 1.0, 200.0),
		/** Curling bomb only: how much of its speed the slide keeps each tick. 1 would never stop. */
		FRICTION("friction", 0.0, 1.0);

		/** What a player types and what the json is keyed by. */
		public final String id;
		/** The range this parameter is allowed, inclusive. */
		public final double min;
		public final double max;

		Param(String id, double min, double max) {
			this.id = id;
			this.min = min;
			this.max = max;
		}

		public boolean holds(double value) {
			return Double.isFinite(value) && value >= min && value <= max;
		}

		/** The allowed range, as a command message says it. */
		public String range() {
			return WeaponTuning.number(min) + " to " + WeaponTuning.number(max);
		}

		public static Optional<Param> byId(String id) {
			String wanted = id.toLowerCase(Locale.ROOT);
			for (Param param : values()) {
				if (param.id.equals(wanted)) return Optional.of(param);
			}
			return Optional.empty();
		}

		/** Every name, comma-separated, for command help and failure messages. */
		public static String idList() {
			return Stream.of(values()).map(param -> param.id).collect(Collectors.joining(", "));
		}
	}

	/**
	 * Does this parameter mean anything for this special? A burst bomb has no fuse — it is gone the
	 * instant it touches anything — and nothing but the curling bomb slides, so offering either number
	 * anywhere else would be offering one nothing reads.
	 */
	public static boolean applies(Special special, Param param) {
		return switch (param) {
			case FUSE -> special.mode == Special.Mode.FUSE;
			case SLIDE_TICKS, FRICTION -> special.mode == Special.Mode.CURL;
			default -> true;
		};
	}

	/** The parameters a special shows and accepts, in declaration order. */
	public static List<Param> params(Special special) {
		List<Param> list = new ArrayList<>();
		for (Param param : Param.values()) {
			if (applies(special, param)) list.add(param);
		}
		return list;
	}

	/** The names that special answers to, comma-separated, for a failure message. */
	public static String paramList(Special special) {
		return params(special).stream().map(param -> param.id).collect(Collectors.joining(", "));
	}

	private static final EnumMap<Special, EnumMap<Param, Double>> DEFAULTS = defaults();
	private static final EnumMap<Special, SpecialTuning> ACTIVE = new EnumMap<>(Special.class);

	static {
		for (Special special : Special.values()) ACTIVE.put(special, new SpecialTuning(special));
	}

	/**
	 * Today's numbers, read off the constants the specials were written as. The splat bomb's are the
	 * {@code Weapon.SPECIAL_*} they have been since round 7, unmoved: what changed in round 10 is that
	 * there are two more columns beside them, not what is in this one.
	 */
	private static EnumMap<Special, EnumMap<Param, Double>> defaults() {
		EnumMap<Special, EnumMap<Param, Double>> all = new EnumMap<>(Special.class);
		for (Special special : Special.values()) {
			EnumMap<Param, Double> values = new EnumMap<>(Param.class);
			switch (special) {
				case SPLAT_BOMB -> {
					values.put(Param.INK, (double) Weapon.SPECIAL_INK);
					values.put(Param.COOLDOWN, (double) Weapon.SPECIAL_COOLDOWN);
					values.put(Param.REFILL_DELAY, (double) Weapon.SPECIAL_REFILL_DELAY);
					values.put(Param.RADIUS, (double) Weapon.SPECIAL_RADIUS);
					values.put(Param.DAMAGE, (double) Weapon.SPECIAL_DAMAGE);
					values.put(Param.EDGE_DAMAGE, (double) Weapon.SPECIAL_EDGE_DAMAGE);
					values.put(Param.BLAST, Weapon.SPECIAL_BLAST);
					values.put(Param.CORE, Weapon.SPECIAL_CORE);
					values.put(Param.VELOCITY, (double) Weapon.SPECIAL_VELOCITY);
					values.put(Param.GRAVITY, Weapon.SPECIAL_GRAVITY);
					values.put(Param.PITCH, (double) Weapon.SPECIAL_PITCH);
					values.put(Param.LIFETIME, (double) Weapon.SPECIAL_LIFETIME);
					values.put(Param.SCALE, (double) Weapon.SPECIAL_SCALE);
					values.put(Param.FUSE, (double) Weapon.SPECIAL_FUSE);
					// It does not slide, but every special holds a value for every parameter: applies()
					// decides what is shown, value() answers for any of them.
					values.put(Param.SLIDE_TICKS, (double) Special.CURLING_SLIDE_TICKS);
					values.put(Param.FRICTION, Special.CURLING_FRICTION);
				}
				case BURST_BOMB -> {
					values.put(Param.INK, (double) Special.BURST_INK);
					values.put(Param.COOLDOWN, (double) Special.BURST_COOLDOWN);
					values.put(Param.REFILL_DELAY, (double) Special.BURST_REFILL_DELAY);
					values.put(Param.RADIUS, (double) Special.BURST_RADIUS);
					values.put(Param.DAMAGE, (double) Special.BURST_DAMAGE);
					values.put(Param.EDGE_DAMAGE, (double) Special.BURST_EDGE_DAMAGE);
					values.put(Param.BLAST, Special.BURST_BLAST);
					values.put(Param.CORE, Special.BURST_CORE);
					values.put(Param.VELOCITY, (double) Special.BURST_VELOCITY);
					values.put(Param.GRAVITY, Special.BURST_GRAVITY);
					values.put(Param.PITCH, (double) Special.BURST_PITCH);
					values.put(Param.LIFETIME, (double) Special.BURST_LIFETIME);
					values.put(Param.SCALE, (double) Special.BURST_SCALE);
					values.put(Param.FUSE, 0.0); // there is no fuse: it is gone on contact
					values.put(Param.SLIDE_TICKS, (double) Special.CURLING_SLIDE_TICKS);
					values.put(Param.FRICTION, Special.CURLING_FRICTION);
				}
				case CURLING_BOMB -> {
					values.put(Param.INK, (double) Special.CURLING_INK);
					values.put(Param.COOLDOWN, (double) Special.CURLING_COOLDOWN);
					values.put(Param.REFILL_DELAY, (double) Special.CURLING_REFILL_DELAY);
					values.put(Param.RADIUS, (double) Special.CURLING_RADIUS);
					values.put(Param.DAMAGE, (double) Special.CURLING_DAMAGE);
					values.put(Param.EDGE_DAMAGE, (double) Special.CURLING_EDGE_DAMAGE);
					values.put(Param.BLAST, Special.CURLING_BLAST);
					values.put(Param.CORE, Special.CURLING_CORE);
					values.put(Param.VELOCITY, (double) Special.CURLING_VELOCITY);
					values.put(Param.GRAVITY, Special.CURLING_GRAVITY);
					values.put(Param.PITCH, (double) Special.CURLING_PITCH);
					values.put(Param.LIFETIME, (double) Special.CURLING_LIFETIME);
					values.put(Param.SCALE, (double) Special.CURLING_SCALE);
					values.put(Param.FUSE, 0.0); // it bursts at the end of the slide, not on a count
					values.put(Param.SLIDE_TICKS, (double) Special.CURLING_SLIDE_TICKS);
					values.put(Param.FRICTION, Special.CURLING_FRICTION);
				}
			}
			all.put(special, values);
		}
		return all;
	}

	private final Special special;
	private final EnumMap<Param, Double> values;

	private SpecialTuning(Special special) {
		this.special = special;
		this.values = new EnumMap<>(DEFAULTS.get(special));
	}

	/** The live tuning for a special. Never null, and the same instance for the life of the server. */
	public static SpecialTuning get(Special special) {
		return ACTIVE.get(special);
	}

	public Special special() {
		return special;
	}

	public double value(Param param) {
		return values.get(param);
	}

	/** By name, as a player types it. Unknown names throw rather than answer with a default. */
	public double value(String param) {
		return value(Param.byId(param).orElseThrow(() -> new IllegalArgumentException(
				"No special parameter called \"" + param + "\". Try one of: " + paramList(special))));
	}

	/** For the counts and the tick numbers, which are whole even when the file is not. */
	public int intValue(Param param) {
		return (int) Math.round(value(param));
	}

	public float floatValue(Param param) {
		return (float) value(param);
	}

	/** What this parameter was before anybody tuned it. */
	public double defaultValue(Param param) {
		return DEFAULTS.get(special).get(param);
	}

	public boolean isDefault(Param param) {
		return value(param) == defaultValue(param);
	}

	/** Every parameter of this special that has been moved off its default, in declaration order. */
	public List<Param> changed() {
		List<Param> changed = new ArrayList<>();
		for (Param param : params(special)) {
			if (!isDefault(param)) changed.add(param);
		}
		return changed;
	}

	/** Returns what the parameter was, so the caller can report old → new. */
	public double set(Param param, double value) {
		if (!param.holds(value)) {
			throw new IllegalArgumentException(param.id + " must be " + param.range() + ", not " + value);
		}
		double was = value(param);
		values.put(param, value);
		return was;
	}

	/** This special back to the numbers it shipped with. */
	public void reset() {
		values.putAll(DEFAULTS.get(special));
	}

	/** Every special back to the numbers it shipped with. */
	public static void resetAll() {
		for (Special special : Special.values()) get(special).reset();
	}

	/** Is anything tuned at all? */
	public static boolean allDefault() {
		for (Special special : Special.values()) {
			if (!get(special).changed().isEmpty()) return false;
		}
		return true;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** {@code config/rivals-paint/specials.json}, the file the tuning lives in between sessions. */
	public static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(Rivals.MOD_ID).resolve("specials.json");
	}

	/** Write what has been changed, and only that; a special with nothing tuned is left out entirely. */
	public static void save(Path path) {
		JsonObject root = new JsonObject();
		for (Special special : Special.values()) {
			SpecialTuning tuning = get(special);
			List<Param> changed = tuning.changed();
			if (changed.isEmpty()) continue;
			JsonObject object = new JsonObject();
			for (Param param : changed) object.addProperty(param.id, tuning.value(param));
			root.add(special.commandId(), object);
		}
		try {
			Path parent = path.getParent();
			if (parent != null) Files.createDirectories(parent);
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
		} catch (IOException | RuntimeException failure) {
			Rivals.LOGGER.warn("[{}] could not write {}: {}", Rivals.MOD_ID, path, failure.toString());
		}
	}

	/** The live file. */
	public static void save() {
		save(configPath());
	}

	/**
	 * Read a tuning file over the defaults, the same way {@link WeaponTuning#load} does: every special
	 * starts from its own and takes whatever the file names, and a missing file, an unknown special or an
	 * unknown parameter is a line in the log rather than a refusal to start.
	 */
	public static void load(Path path) {
		resetAll();
		if (!Files.isRegularFile(path)) return;
		JsonObject root;
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader);
			if (!parsed.isJsonObject()) {
				Rivals.LOGGER.warn("[{}] {} is not a json object; special tuning stays at the defaults", Rivals.MOD_ID, path);
				return;
			}
			root = parsed.getAsJsonObject();
		} catch (IOException | RuntimeException failure) {
			Rivals.LOGGER.warn("[{}] could not read {} ({}); special tuning stays at the defaults",
					Rivals.MOD_ID, path, failure.toString());
			return;
		}
		int taken = 0;
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			Optional<Special> special = Special.byId(entry.getKey());
			if (special.isEmpty() || !entry.getValue().isJsonObject()) {
				Rivals.LOGGER.warn("[{}] {}: no special called \"{}\", ignored", Rivals.MOD_ID, path, entry.getKey());
				continue;
			}
			SpecialTuning tuning = get(special.get());
			for (Map.Entry<String, JsonElement> field : entry.getValue().getAsJsonObject().entrySet()) {
				Optional<Param> found = Param.byId(field.getKey());
				if (found.isEmpty() || !field.getValue().isJsonPrimitive()) {
					Rivals.LOGGER.warn("[{}] {}: {} has no parameter called \"{}\", ignored",
							Rivals.MOD_ID, path, entry.getKey(), field.getKey());
					continue;
				}
				Param param = found.get();
				if (!applies(special.get(), param)) {
					Rivals.LOGGER.warn("[{}] {}: {} does not read {}, ignored",
							Rivals.MOD_ID, path, entry.getKey(), param.id);
					continue;
				}
				double value;
				try {
					value = field.getValue().getAsDouble();
				} catch (RuntimeException notANumber) {
					Rivals.LOGGER.warn("[{}] {}: {}.{} is not a number, ignored",
							Rivals.MOD_ID, path, entry.getKey(), param.id);
					continue;
				}
				// Gson parses leniently, so NaN and Infinity are both things a hand-edited file can say.
				if (!Double.isFinite(value)) {
					Rivals.LOGGER.warn("[{}] {}: {}.{} is {}, not a number to throw by; ignored",
							Rivals.MOD_ID, path, entry.getKey(), param.id, value);
					continue;
				}
				if (!param.holds(value)) {
					double clamped = Math.clamp(value, param.min, param.max);
					Rivals.LOGGER.warn("[{}] {}: {}.{} is {}, outside {}; clamped to {}", Rivals.MOD_ID, path,
							entry.getKey(), param.id, WeaponTuning.number(value), param.range(), WeaponTuning.number(clamped));
					value = clamped;
				}
				tuning.set(param, value);
				taken++;
			}
		}
		if (taken > 0) Rivals.LOGGER.info("[{}] special tuning: {} values from {}", Rivals.MOD_ID, taken, path);
	}

	/** The live file, read at server start. */
	public static void load() {
		load(configPath());
	}
}
