package nu.metacraft.rivals.gun;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import nu.metacraft.rivals.Rivals;
import nu.metacraft.rivals.paint.Painter;

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
 * Every number a shot is made of, per weapon, live. One instance per {@link Weapon} holds a value
 * for every {@link Param}; the fire modes read it at the moment they fire, so a value changed with
 * {@code /rivals tune} is in the next shot without a restart.
 *
 * <p>The defaults are the constants the weapons were written with — the {@link Weapon} enum fields,
 * the grouped {@code SLOSHER_*}/{@code CHARGE_*} statics and {@link PaintBall}'s flight constants,
 * read from the code rather than copied, so there is still exactly one place a default lives. Only
 * what has been changed away from them is kept in the file: a tuning nobody has touched is
 * {@code {}}, and a default that is edited in the code moves with it.
 */
public final class WeaponTuning {
	/**
	 * The tunable numbers. The id is what a player types and what the json is keyed by; the rest of the
	 * code uses the constant. Not every parameter applies to every weapon — the charger fires no ball
	 * and the other three take no charge — so {@link #applies} decides which of these a weapon shows.
	 *
	 * <p>Each carries the range it is allowed, because several of them are loops and spawns: a
	 * {@code splat_radius} of 500 is a million block writes in one splat, and a {@code count} of 5000
	 * is five thousand entities on one click. The bounds are wide enough that anything worth trying is
	 * inside them and narrow enough that nothing inside them can wedge the server; a value outside is
	 * refused by the command and clamped, with a warning, on the way in from the file.
	 */
	public enum Param {
		/** Launch speed, blocks per tick, into {@code shootFromRotation}. */
		VELOCITY("velocity", 0.0, 10.0),
		/** Random inaccuracy cone, in degrees-ish; vanilla's {@code inaccuracy}. */
		SPREAD("spread", 0.0, 90.0),
		/** The same, for a shot fired with both feet off the ground. Splatoon doubles it. */
		SPREAD_AIR("spread_air", 0.0, 90.0),
		/**
		 * How far a ball flies straight and fast before it decays, in blocks; 0 for a shot that falls from
		 * the moment it leaves. Splatoon's shots have exactly this shape, and it is what separates an
		 * accurate weapon from a lobbing one more than any other number here.
		 */
		STRAIGHT_BLOCKS("straight_blocks", 0.0, 64.0),
		/** The speed it keeps once the straight stretch is over, blocks per tick. */
		DECAYED_SPEED("decayed_speed", 0.0, 10.0),
		/** Fall rate per tick. A vanilla snowball is 0.03; negative floats. */
		GRAVITY("gravity", -1.0, 1.0),
		/** How many block hits reflect the ball instead of ending it. Rounded. */
		BOUNCES("bounces", 0.0, 8.0),
		/** How much speed a bounce keeps. Over 1 would be a ball that gains energy off a wall. */
		RESTITUTION("restitution", 0.0, 1.0),
		/** Ticks before an unhit ball splashes the floor under itself; 0 for no limit. Rounded. */
		LIFETIME("lifetime", 0.0, 400.0),
		/** How far the impact splat reaches: 0 a single face, 1 a 3x3, 2 a 5x5. Rounded. */
		SPLAT_RADIUS("splat_radius", 0.0, 4.0),
		/** Ink one shot costs, and the tank a shot needs before it is allowed. Rounded. */
		INK("ink", 0.0, 100.0),
		/** Ticks of item cooldown after a shot — the fire rate. Rounded. */
		COOLDOWN("cooldown", 0.0, 200.0),
		/** Camera kick, in pitch degrees; negative is up. */
		KICK("kick", -45.0, 45.0),
		/** Ticks after a shot before standing in your own ink tops the tank up again. Rounded. */
		REFILL_DELAY("refill_delay", 0.0, 200.0),
		/** Hearts off a direct hit on someone from another team, per projectile, before it decays. */
		DAMAGE("damage", 0.0, 40.0),
		/**
		 * The damage falloff: full {@code damage} until {@code decay_start} ticks of flight, then
		 * {@code decay_per_tick} off every tick until it reaches {@code decayed_damage}. A
		 * {@code decay_per_tick} of 0 is a weapon that does not care how far it has thrown.
		 */
		DECAY_START("decay_start", 0.0, 200.0),
		DECAY_PER_TICK("decay_per_tick", 0.0, 40.0),
		DECAYED_DAMAGE("decayed_damage", 0.0, 40.0),
		/** Balls thrown per shot. Rounded. */
		COUNT("count", 1.0, 16.0),
		/** Degrees of yaw between neighbouring balls of a multi-ball shot; the fan is centred on the view. */
		FAN_YAW("fan_yaw", 0.0, 90.0),
		/** Degrees of pitch added to the view before the throw; negative aims above the crosshair. */
		FAN_PITCH("fan_pitch", -89.0, 89.0),
		/** Droplets one bounce throws off. Rounded. */
		SPATTER_COUNT("spatter_count", 0.0, 8.0),
		/** Ticks one of those droplets lives. Rounded. */
		SPATTER_LIFETIME("spatter_lifetime", 0.0, 400.0),
		/** The fraction of the reflected speed a droplet leaves at. */
		SPATTER_SPEED("spatter_speed", 0.0, 4.0),
		/** How far a droplet is nudged off that line. */
		SPATTER_SCATTER("spatter_scatter", 0.0, 2.0),
		/** Hearts off a droplet hit — a graze, not a shot. */
		SPATTER_DAMAGE("spatter_damage", 0.0, 40.0),
		/** Roller: how wide the rolled strip is, in cells. Rounded. */
		ROLL_WIDTH("roll_width", 1.0, 9.0),
		/** Roller: hearts off someone the head runs over, and how long before it can happen again. */
		ROLL_DAMAGE("roll_damage", 0.0, 40.0),
		ROLL_HIT_COOLDOWN("roll_hit_cooldown", 1.0, 200.0),
		/** Roller: one ink every this many ticks of rolling. Rounded. */
		ROLL_INK_EVERY("roll_ink_every", 1.0, 200.0),
		/** Roller: the movement bonus while rolling, as a fraction. */
		ROLL_SPEED("roll_speed", 0.0, 1.0),
		/** Charger: ticks held below which the release is a tap, not a shot. Rounded. */
		CHARGE_MIN("charge_min", 1.0, 200.0),
		/** Charger: ticks held for a full charge; holding longer adds nothing. Rounded, never 0. */
		CHARGE_FULL("charge_full", 1.0, 200.0),
		/** Charger: hitscan reach in blocks at no charge, and at a full one. */
		RANGE_MIN("range_min", 0.0, 128.0),
		RANGE_FULL("range_full", 0.0, 128.0),
		/** Charger: ink a release costs at no charge, and at a full one. Rounded. */
		CHARGE_INK_MIN("charge_ink_min", 0.0, 100.0),
		CHARGE_INK_FULL("charge_ink_full", 0.0, 100.0),
		/**
		 * Charger: hearts off whoever stops the line — at no charge, at the top of a partial charge, and
		 * at a full one. The first two interpolate; the third is a step, because a charger that is held to
		 * the top is meant to be a splat and one let go a moment early is meant not to be.
		 */
		CHARGE_DAMAGE_MIN("charge_damage_min", 0.0, 40.0),
		CHARGE_DAMAGE_PARTIAL("charge_damage_partial", 0.0, 40.0),
		CHARGE_DAMAGE_FULL("charge_damage_full", 0.0, 40.0);

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

		/** Whether a value is one this parameter will take at all. */
		public boolean holds(double value) {
			return Double.isFinite(value) && value >= min && value <= max;
		}

		/** The allowed range, as a command message says it. */
		public String range() {
			return number(min) + " to " + number(max);
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

	/** The charger's own, which only it reads. */
	private static final List<Param> CHARGE_ONLY = List.of(Param.CHARGE_MIN, Param.CHARGE_FULL,
			Param.RANGE_MIN, Param.RANGE_FULL, Param.CHARGE_INK_MIN, Param.CHARGE_INK_FULL,
			Param.CHARGE_DAMAGE_MIN, Param.CHARGE_DAMAGE_PARTIAL, Param.CHARGE_DAMAGE_FULL);

	/**
	 * The roller's own: the roll. {@code flick_tap} was here too — the ticks a right click could be held
	 * and still count as a tap rather than a roll — and it went with the tap itself: the flick is the left
	 * button now, so there is nothing to time. A tuning file that still names it is accepted and the key
	 * dropped with a line in the log, the same as any other name no weapon reads.
	 */
	private static final List<Param> ROLL_ONLY = List.of(Param.ROLL_WIDTH, Param.ROLL_DAMAGE,
			Param.ROLL_HIT_COOLDOWN, Param.ROLL_INK_EVERY, Param.ROLL_SPEED);

	/**
	 * Every parameter that belongs to a weapon simply because it is a weapon, rather than because of what
	 * it throws. These are the ones the charger's whitelist has to name, and the list any new
	 * every-weapon parameter has to join — {@code refill_delay} did not, and a charger that had just
	 * fired went on refilling from its own paint because {@link nu.metacraft.rivals.PlayerTick} reads a
	 * value the command would not show and the file would have thrown away.
	 */
	private static final List<Param> EVERY_WEAPON = List.of(Param.INK, Param.COOLDOWN, Param.REFILL_DELAY, Param.KICK);

	/**
	 * Does this parameter mean anything for this weapon? The charger throws no ball, so none of a ball's
	 * numbers reach it; nothing but the roller rolls, and nothing but the charger charges.
	 * {@link #EVERY_WEAPON} belongs to all four. Only used for what the commands offer and accept —
	 * {@link #value} answers for any of them.
	 *
	 * <p>The special is not here at all any more: what F throws is the thrower's own pick rather than the
	 * weapon's, so its numbers live in {@link SpecialTuning}, keyed by {@link Special}.
	 */
	public static boolean applies(Weapon weapon, Param param) {
		boolean charge = CHARGE_ONLY.contains(param);
		boolean roll = ROLL_ONLY.contains(param);
		// The charger's list is a whitelist, so anything new falls outside it by construction.
		if (weapon == Weapon.CHARGER) {
			return charge || EVERY_WEAPON.contains(param);
		}
		return !charge && (!roll || weapon == Weapon.ROLLER);
	}

	/** What every weapon reads whatever it fires. For the test that guards against the next omission. */
	public static List<Param> everyWeapon() {
		return EVERY_WEAPON;
	}

	/** The parameters a weapon shows and accepts, in declaration order. */
	public static List<Param> params(Weapon weapon) {
		List<Param> list = new ArrayList<>();
		for (Param param : Param.values()) {
			if (applies(weapon, param)) list.add(param);
		}
		return list;
	}

	/** The names that weapon answers to, comma-separated, for a failure message. */
	public static String paramList(Weapon weapon) {
		return params(weapon).stream().map(param -> param.id).collect(Collectors.joining(", "));
	}

	private static final EnumMap<Weapon, EnumMap<Param, Double>> DEFAULTS = defaults();
	private static final EnumMap<Weapon, WeaponTuning> ACTIVE = new EnumMap<>(Weapon.class);

	static {
		for (Weapon weapon : Weapon.values()) ACTIVE.put(weapon, new WeaponTuning(weapon));
	}

	/**
	 * Today's numbers, read off the constants they were written as. The three ball weapons share one
	 * shape — {@code count} balls, fanned by {@code fan_yaw} around the view and pitched by
	 * {@code fan_pitch} — which the old per-weapon arms of {@code fire} spelled out separately: the
	 * slosher's {@code {-4, 4}} is two pellets eight degrees apart, and the shooter's single ball is that
	 * same fan with one in it.
	 */
	private static EnumMap<Weapon, EnumMap<Param, Double>> defaults() {
		EnumMap<Weapon, EnumMap<Param, Double>> all = new EnumMap<>(Weapon.class);
		for (Weapon weapon : Weapon.values()) {
			EnumMap<Param, Double> values = new EnumMap<>(Param.class);
			// Shared by all four, straight off the enum.
			values.put(Param.VELOCITY, (double) weapon.velocity);
			values.put(Param.SPREAD, (double) weapon.inaccuracy);
			values.put(Param.SPREAD_AIR, (double) weapon.inaccuracy);
			values.put(Param.INK, (double) weapon.inkPerShot);
			values.put(Param.COOLDOWN, (double) weapon.cooldownTicks);
			values.put(Param.REFILL_DELAY, (double) weapon.refillDelay);
			values.put(Param.KICK, (double) weapon.kickPitch);
			values.put(Param.DAMAGE, (double) weapon.damage);
			// No falloff unless the weapon asks for one: a bucketful does not care how far it has flown.
			values.put(Param.DECAY_START, 0.0);
			values.put(Param.DECAY_PER_TICK, 0.0);
			values.put(Param.DECAYED_DAMAGE, (double) weapon.damage);
			// The ball's own, from PaintBall's flight constants unless the weapon overrode them.
			values.put(Param.STRAIGHT_BLOCKS, 0.0);
			values.put(Param.DECAYED_SPEED, (double) weapon.velocity);
			values.put(Param.GRAVITY, PaintBall.GRAVITY);
			values.put(Param.BOUNCES, 0.0);
			values.put(Param.RESTITUTION, PaintBall.BOUNCE_RESTITUTION);
			values.put(Param.LIFETIME, 0.0);
			values.put(Param.SPLAT_RADIUS, (double) Painter.RADIUS);
			values.put(Param.COUNT, 1.0);
			values.put(Param.FAN_YAW, 0.0);
			values.put(Param.FAN_PITCH, 0.0);
			values.put(Param.SPATTER_COUNT, (double) PaintBall.BOUNCE_DROPLETS);
			values.put(Param.SPATTER_LIFETIME, (double) PaintBall.DROPLET_LIFETIME);
			values.put(Param.SPATTER_SPEED, PaintBall.DROPLET_SPEED);
			values.put(Param.SPATTER_SCATTER, PaintBall.DROPLET_SCATTER);
			values.put(Param.SPATTER_DAMAGE, (double) Weapon.DROPLET_DAMAGE);
			values.put(Param.ROLL_WIDTH, (double) Weapon.ROLL_WIDTH);
			values.put(Param.ROLL_DAMAGE, (double) Weapon.ROLL_DAMAGE);
			values.put(Param.ROLL_HIT_COOLDOWN, (double) Weapon.ROLL_HIT_COOLDOWN);
			values.put(Param.ROLL_INK_EVERY, (double) Weapon.ROLL_INK_EVERY);
			values.put(Param.ROLL_SPEED, Weapon.ROLL_SPEED_BONUS);
			values.put(Param.CHARGE_MIN, (double) Weapon.MIN_CHARGE_TICKS);
			values.put(Param.CHARGE_FULL, (double) Weapon.CHARGE_FULL_TICKS);
			values.put(Param.RANGE_MIN, Weapon.CHARGE_BASE_RANGE);
			values.put(Param.RANGE_FULL, Weapon.CHARGE_BASE_RANGE + Weapon.CHARGE_EXTRA_RANGE);
			values.put(Param.CHARGE_INK_MIN, (double) Weapon.CHARGE_BASE_COST);
			values.put(Param.CHARGE_INK_FULL, (double) (Weapon.CHARGE_BASE_COST + Weapon.CHARGE_EXTRA_COST));
			values.put(Param.CHARGE_DAMAGE_MIN, (double) Weapon.CHARGE_BASE_DAMAGE);
			values.put(Param.CHARGE_DAMAGE_PARTIAL, (double) Weapon.CHARGE_PARTIAL_DAMAGE);
			values.put(Param.CHARGE_DAMAGE_FULL, (double) Weapon.CHARGE_FULL_DAMAGE);
			switch (weapon) {
				case SHOOTER -> {
					values.put(Param.BOUNCES, (double) Weapon.SHOOTER_BOUNCES);
					values.put(Param.SPREAD_AIR, (double) Weapon.SHOOTER_SPREAD_AIR);
					values.put(Param.STRAIGHT_BLOCKS, Weapon.SHOOTER_STRAIGHT_BLOCKS);
					values.put(Param.DECAYED_SPEED, Weapon.SHOOTER_DECAYED_SPEED);
					values.put(Param.GRAVITY, Weapon.SHOOTER_GRAVITY);
					values.put(Param.DECAY_START, (double) Weapon.SHOOTER_DECAY_START);
					values.put(Param.DECAY_PER_TICK, (double) Weapon.SHOOTER_DECAY_PER_TICK);
					values.put(Param.DECAYED_DAMAGE, (double) Weapon.SHOOTER_DECAYED_DAMAGE);
				}
				case ROLLER -> {
					values.put(Param.COUNT, (double) Weapon.ROLLER_FLICK_BALLS);
					values.put(Param.FAN_YAW, (double) Weapon.ROLLER_FAN_YAW);
					values.put(Param.FAN_PITCH, (double) Weapon.ROLLER_PITCH);
					values.put(Param.GRAVITY, Weapon.ROLLER_GRAVITY);
					values.put(Param.SPLAT_RADIUS, (double) Weapon.ROLLER_SPLAT_RADIUS);
					values.put(Param.DECAY_START, (double) Weapon.ROLLER_DECAY_START);
					values.put(Param.DECAY_PER_TICK, (double) Weapon.ROLLER_DECAY_PER_TICK);
					values.put(Param.DECAYED_DAMAGE, (double) Weapon.ROLLER_DECAYED_DAMAGE);
				}
				case SLOSHER -> {
					values.put(Param.COUNT, (double) Weapon.SLOSHER_FAN.length);
					values.put(Param.FAN_YAW, fanStep(Weapon.SLOSHER_FAN));
					values.put(Param.FAN_PITCH, (double) Weapon.SLOSHER_PITCH);
					values.put(Param.GRAVITY, Weapon.SLOSHER_GRAVITY);
					values.put(Param.SPLAT_RADIUS, (double) Weapon.SLOSHER_SPLAT_RADIUS);
				}
				case CHARGER -> {} // it throws nothing; its own numbers are the CHARGE_* above
			}
			all.put(weapon, values);
		}
		return all;
	}

	/** The gap between neighbouring offsets of a hand-written fan, which is the fan as one number. */
	private static double fanStep(float[] fan) {
		return fan.length < 2 ? 0.0 : fan[1] - fan[0];
	}

	private final Weapon weapon;
	private final EnumMap<Param, Double> values;

	private WeaponTuning(Weapon weapon) {
		this.weapon = weapon;
		this.values = new EnumMap<>(DEFAULTS.get(weapon));
	}

	/** The live tuning for a weapon. Never null, and the same instance for the life of the server. */
	public static WeaponTuning get(Weapon weapon) {
		return ACTIVE.get(weapon);
	}

	public Weapon weapon() {
		return weapon;
	}

	public double value(Param param) {
		return values.get(param);
	}

	/** By name, as a player types it. Unknown names throw rather than answer with a default. */
	public double value(String param) {
		return value(Param.byId(param).orElseThrow(() ->
				new IllegalArgumentException("No weapon parameter called \"" + param + "\". Try one of: " + paramList(weapon))));
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
		return DEFAULTS.get(weapon).get(param);
	}

	public boolean isDefault(Param param) {
		return value(param) == defaultValue(param);
	}

	/** Every parameter of this weapon that has been moved off its default, in declaration order. */
	public List<Param> changed() {
		List<Param> changed = new ArrayList<>();
		for (Param param : params(weapon)) {
			if (!isDefault(param)) changed.add(param);
		}
		return changed;
	}

	/**
	 * Returns what the parameter was, so the caller can report old → new. The value has to be one the
	 * parameter {@link Param#holds}: callers that take it from a player check first and say so, and a
	 * caller that has not checked is a bug, not a reason to let a {@code splat_radius} of 500 through.
	 */
	public double set(Param param, double value) {
		if (!param.holds(value)) {
			throw new IllegalArgumentException(param.id + " must be " + param.range() + ", not " + value);
		}
		double was = value(param);
		values.put(param, value);
		return was;
	}

	/** This weapon back to the numbers it shipped with. */
	public void reset() {
		values.putAll(DEFAULTS.get(weapon));
	}

	/** Every weapon back to the numbers it shipped with. */
	public static void resetAll() {
		for (Weapon weapon : Weapon.values()) get(weapon).reset();
	}

	/** Is anything tuned at all? What {@code /rivals tune} with no arguments answers. */
	public static boolean allDefault() {
		for (Weapon weapon : Weapon.values()) {
			if (!get(weapon).changed().isEmpty()) return false;
		}
		return true;
	}

	/**
	 * A tuning number as a message wants to read it: whole numbers without the {@code .0} that would
	 * make {@code bounces} look like a fraction, everything else to three decimals.
	 */
	public static String number(double value) {
		if (value == Math.rint(value) && Math.abs(value) < 1.0e9) return String.valueOf((long) value);
		return String.valueOf(Math.round(value * 1000.0) / 1000.0);
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** {@code config/rivals-paint/weapons.json}, the file the tuning lives in between sessions. */
	public static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(Rivals.MOD_ID).resolve("weapons.json");
	}

	/**
	 * Write what has been changed, and only that: a weapon with nothing tuned is left out entirely, so
	 * a file nobody has touched is {@code {}} and every default stays the code's to change.
	 */
	public static void save(Path path) {
		JsonObject root = new JsonObject();
		for (Weapon weapon : Weapon.values()) {
			WeaponTuning tuning = get(weapon);
			List<Param> changed = tuning.changed();
			if (changed.isEmpty()) continue;
			JsonObject object = new JsonObject();
			for (Param param : changed) object.addProperty(param.id, tuning.value(param));
			root.add(weapon.commandId(), object);
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
	 * Read a tuning file over the defaults: every weapon starts from its default and takes whatever the
	 * file names, so a key that has been dropped from the file is a parameter back at its default.
	 * Missing file, unreadable file, unknown weapon or unknown parameter — none of them is worth
	 * refusing to start over, so each is a line in the log and the defaults stand.
	 */
	public static void load(Path path) {
		resetAll();
		if (!Files.isRegularFile(path)) return;
		JsonObject root;
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader);
			if (!parsed.isJsonObject()) {
				Rivals.LOGGER.warn("[{}] {} is not a json object; weapon tuning stays at the defaults", Rivals.MOD_ID, path);
				return;
			}
			root = parsed.getAsJsonObject();
		} catch (IOException | RuntimeException failure) {
			Rivals.LOGGER.warn("[{}] could not read {} ({}); weapon tuning stays at the defaults",
					Rivals.MOD_ID, path, failure.toString());
			return;
		}
		int taken = 0;
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			Optional<Weapon> weapon = Weapon.byId(entry.getKey());
			if (weapon.isEmpty() || !entry.getValue().isJsonObject()) {
				Rivals.LOGGER.warn("[{}] {}: no weapon called \"{}\", ignored", Rivals.MOD_ID, path, entry.getKey());
				continue;
			}
			WeaponTuning tuning = get(weapon.get());
			for (Map.Entry<String, JsonElement> field : entry.getValue().getAsJsonObject().entrySet()) {
				Optional<Param> found = Param.byId(field.getKey());
				if (found.isEmpty() || !field.getValue().isJsonPrimitive()) {
					Rivals.LOGGER.warn("[{}] {}: {} has no parameter called \"{}\", ignored",
							Rivals.MOD_ID, path, entry.getKey(), field.getKey());
					continue;
				}
				Param param = found.get();
				// A parameter the weapon does not read would be dropped again by the next save, so it is
				// worth a word now rather than a value that silently disappears.
				if (!applies(weapon.get(), param)) {
					Rivals.LOGGER.warn("[{}] {}: {} does not read {}, ignored", Rivals.MOD_ID, path, entry.getKey(), param.id);
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
				// Gson parses leniently, so NaN and Infinity are both things a hand-edited file can say;
				// either one in a velocity is a ball at no position at all.
				if (!Double.isFinite(value)) {
					Rivals.LOGGER.warn("[{}] {}: {}.{} is {}, not a number to fly by; ignored",
							Rivals.MOD_ID, path, entry.getKey(), param.id, value);
					continue;
				}
				if (!param.holds(value)) {
					double clamped = Math.clamp(value, param.min, param.max);
					Rivals.LOGGER.warn("[{}] {}: {}.{} is {}, outside {}; clamped to {}",
							Rivals.MOD_ID, path, entry.getKey(), param.id, number(value), param.range(), number(clamped));
					value = clamped;
				}
				tuning.set(param, value);
				taken++;
			}
		}
		if (taken > 0) Rivals.LOGGER.info("[{}] weapon tuning: {} values from {}", Rivals.MOD_ID, taken, path);
	}

	/** The live file, read at server start. */
	public static void load() {
		load(configPath());
	}
}
