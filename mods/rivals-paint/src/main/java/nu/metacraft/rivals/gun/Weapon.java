package nu.metacraft.rivals.gun;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The four weapons, and the numbers that separate them. One {@link PaintWeapon} item is registered
 * per value, at {@code rivals-paint:<id>}; the shooter keeps v2's {@code paint_gun} id so its
 * model, item definition and any stack already in a world carry over untouched.
 *
 * <p>Every number here is a <em>default</em>: {@link WeaponTuning} is built from them at startup and
 * the fire modes read it, not these fields, so {@code /rivals tune} can move any of them for the rest
 * of the session without a restart. Change one here and you have changed what a fresh config, and
 * {@code /rivals tune <weapon> reset}, go back to.
 *
 * <p>{@code velocity} and {@code inaccuracy} feed {@code shootFromRotation}; the charger fires a
 * hitscan line instead of a projectile, so both are 0 for it and its {@code inkPerShot} is only the
 * base cost, with the charge surcharge added at release. {@code damage} is the same story: a direct
 * hit on someone from another team hurts, and the charger's share of that is on the charge rather
 * than on a ball.
 *
 * <p><b>Where the numbers come from.</b> Since round 7 they are Splatoon 1's own, read off Splatcraft
 * (MIT), whose {@code data/splatcraft/weapon_settings/*.json} are that game's figures on the scale this
 * module already uses: 20 hit points, a hundred-unit ink tank, twenty ticks to the second. Each default
 * below names its file and its value, so the calibration can be checked rather than believed. What is
 * <em>not</em> Splatoon is anything about paint coverage — splat radii, bounces, the charger's trail —
 * because those are this game's geometry rather than that one's.
 */
public enum Weapon {
	/** Splattershot ({@code splattershot.json}): 3 ticks a shot, 0.9 ink, speed 2.0, damage 8. */
	SHOOTER("paint_gun", "Paint Gun", 1, 3, 2.0f, 6.0f, -2.5f, 8.0f, 7),
	/** Splat Charger ({@code splat_charger.json}): the charge is the weapon; see the CHARGE_* below. */
	CHARGER("charger", "Paint Charger", 2, 20, 0.0f, 0.0f, -6.0f, 0.0f, 7),
	/** Slosher ({@code slosher.json}): a 12-tick cycle (2 startup, 10 endlag), 7 ink, 7 flat damage. */
	SLOSHER("slosher", "Paint Slosher", 7, 12, 1.1f, 0.0f, -3.0f, 7.0f, 13),
	/** Splat Roller ({@code splat_roller.json}), the swing half: 9 ink, 15 recovery, 30 damage at 0.55. */
	ROLLER("roller", "Paint Roller", 9, 15, 0.55f, 0.0f, -4.0f, 30.0f, 15);

	/** Registry path and model path. Also accepted by {@code /rivals gun <weapon>}. */
	public final String id;
	public final String displayName;
	public final int inkPerShot;
	public final int cooldownTicks;
	public final float velocity;
	public final float inaccuracy;
	/** Relative pitch nudge on the shot, in degrees; negative is up. */
	public final float kickPitch;
	/**
	 * Hearts off a direct hit on someone from another team, per projectile, before the falloff. The
	 * slosher throws two and the roller's flick three, so a face full of either is worth rather more than
	 * the number here — and every one of them lands in full, which is what {@link PaintDamage} is for. The
	 * charger is 0 because it fires no projectile: its damage rides the charge, {@link #CHARGE_BASE_DAMAGE}
	 * through {@link #CHARGE_PARTIAL_DAMAGE} to {@link #CHARGE_FULL_DAMAGE}, dealt by the hitscan at release.
	 */
	public final float damage;

	// Everything else that separates one weapon from the next. They live here rather than on the item so
	// that one weapon is one place to look, instead of half its numbers being in PaintWeapon; each is the
	// default behind the WeaponTuning parameter of the same meaning, named in its javadoc.

	/**
	 * Shooter: how many block hits reflect the ball instead of ending it. One now rather than two: the
	 * ball spends most of its flight decayed and slow, and a second bounce off that was a dribble.
	 */
	public static final int SHOOTER_BOUNCES = 1;
	/**
	 * Shooter: the flight, from {@code splattershot.json}. A Splatoon shot is straight and fast for a
	 * fixed distance and then falls: speed 2.0 for eight blocks, then 0.5 with gravity 0.075 under it
	 * (Splatcraft's {@code InkProjectileEntity} keeps {@code isNoGravity} for the straight-shot window).
	 * That is the shape that makes a shooter accurate up close and a paint hose at range, and it is what
	 * {@link PaintBall} now flies.
	 */
	public static final double SHOOTER_STRAIGHT_BLOCKS = 8.0;
	public static final double SHOOTER_DECAYED_SPEED = 0.5;
	public static final double SHOOTER_GRAVITY = 0.075;
	/**
	 * Shooter: the damage falls off with time in the air — 8 for the first three ticks, then 0.34 a tick
	 * down to a floor of 4. Two shots kill at point-blank; four are needed across a room.
	 */
	public static final int SHOOTER_DECAY_START = 3;
	public static final float SHOOTER_DECAY_PER_TICK = 0.34f;
	public static final float SHOOTER_DECAYED_DAMAGE = 4.0f;
	/** Shooter: 6 degrees of spread standing, 12 in the air. Splatoon doubles it for a jumping shooter. */
	public static final float SHOOTER_SPREAD_AIR = 12.0f;

	/**
	 * Roller: the flick. Splatoon's Splat Roller swing throws three drops in a near-vertical arc that
	 * lands a few blocks ahead, which is a fan of three at {@link #ROLLER_FAN_YAW} degrees of yaw thrown
	 * {@link #ROLLER_PITCH} degrees above the crosshair at the enum's own low {@code velocity}: the
	 * pitch and the speed together are what makes it an arc rather than a shot.
	 * ({@code splat_roller.json}: 3 projectiles at speed 0.55, startup 6, recovery 15.)
	 */
	public static final int ROLLER_FLICK_BALLS = 3;
	public static final float ROLLER_FAN_YAW = 20.0f;
	public static final float ROLLER_PITCH = -67.0f;
	public static final double ROLLER_GRAVITY = 0.06;
	/**
	 * Roller: the flick's damage falls off hard — 30 from tick 8, then 3.45 a tick to a floor of 7. A
	 * flick that connects is a splat; the same flick caught at the end of its arc is a graze. The drops
	 * fall from the moment they leave, so there is no straight-shot window on them at all.
	 */
	public static final int ROLLER_DECAY_START = 8;
	public static final float ROLLER_DECAY_PER_TICK = 3.45f;
	public static final float ROLLER_DECAYED_DAMAGE = 7.0f;
	/** Roller: the flick lands as a bucketful, 5x5 on the face it finds. */
	public static final int ROLLER_SPLAT_RADIUS = 2;

	// The roll, from splat_roller.json's rolling half and RollerItem.weaponUseTick (Splatcraft, MIT).

	/** Roller: how wide the rolled strip is, in cells. Splatcraft rolls 3 wide. */
	public static final int ROLL_WIDTH = 3;
	/**
	 * Roller: what running someone over is worth. Splatcraft's roll does 25 on contact, which on a 20 HP
	 * scale is most of a player — a roller that reaches you has earned it.
	 */
	public static final float ROLL_DAMAGE = 25.0f;
	/** Roller: ticks before the same victim can be run over again, so a roll is a hit and not a grinder. */
	public static final int ROLL_HIT_COOLDOWN = 10;
	/**
	 * Roller: one ink every this many ticks of rolling. Splatcraft spends 0.06 of a 100-unit tank a tick,
	 * which is a unit every sixteen and change, and sixteen of those is a tank that lasts a minute and a
	 * half of solid rolling — which is to say it never ran out, which is what the user reported. Splatoon
	 * 1's Splat Roller empties in about 33 seconds of rolling; five ticks a unit is 25, close enough on a
	 * tank that refills as fast as ours does, and it makes the roller a weapon with a cost again.
	 */
	public static final int ROLL_INK_EVERY = 5;
	/** Roller: the movement bonus while rolling. Splatcraft's roll mobility is 1.08. */
	public static final double ROLL_SPEED_BONUS = 0.08;
	/** Roller: how far in front of the feet the head sweeps, in blocks. */
	public static final double ROLL_REACH = 1.5;

	/**
	 * Slosher: two pellets eight degrees apart, which is {@code slosher.json}'s two projectiles at its
	 * own 8° spread — not the four-ball spray this weapon threw before. Both carry flat damage: a
	 * slosher's bucketful does not care how far it has flown.
	 */
	public static final float[] SLOSHER_FAN = {-4.0f, 4.0f};
	/** Slosher: it lobs, so it aims above the crosshair and falls harder than a shooter's ball. */
	public static final float SLOSHER_PITCH = -15.0f;
	public static final double SLOSHER_GRAVITY = 0.06;
	/** Slosher: 5x5 on impact. */
	public static final int SLOSHER_SPLAT_RADIUS = 2;

	/** Charger: it is held to charge, so vanilla's cap for "as long as you like". */
	public static final int CHARGE_MAX_TICKS = 72000;
	/** Charger: a full charge, in ticks held; holding longer adds nothing. {@code splat_charger.json}. */
	public static final int CHARGE_FULL_TICKS = 20;
	/** Charger: below this the release is a tap, not a shot — no line, no ink, no cooldown. */
	public static final int MIN_CHARGE_TICKS = 5;
	/** Charger: ink at no charge, and what a full charge adds on top: 2.25 → 18 in Splatcraft. */
	public static final int CHARGE_BASE_COST = 2;
	public static final int CHARGE_EXTRA_COST = 16;
	/** Charger: hitscan reach in blocks, at no charge and what a full charge adds: 9 → 24. */
	public static final double CHARGE_BASE_RANGE = 9.0;
	public static final double CHARGE_EXTRA_RANGE = 15.0;
	/**
	 * Charger: hearts off whoever stops the line. Splatoon's curve, and it is a curve with a step in it:
	 * a partial charge climbs 8 → 16 with how long it was held, and a <em>full</em> charge jumps to 32,
	 * which is more than a player has. That jump is the weapon — the whole point of a charger is that
	 * holding it to the top is a splat and letting go a moment early is not — so it is a discontinuity on
	 * purpose rather than a line from 8 to 32 that happens to pass through 16.
	 * ({@code splat_charger.json}: 8 → 16 by charge, 32 at full.)
	 */
	public static final float CHARGE_BASE_DAMAGE = 8.0f;
	public static final float CHARGE_PARTIAL_DAMAGE = 16.0f;
	public static final float CHARGE_FULL_DAMAGE = 32.0f;

	/**
	 * The splat bomb: the special every weapon but the charger throws on a left click ({@code
	 * splat_bomb.json}, and {@code InkExplosion} for the blast). Seventy ink of a hundred-unit tank and a
	 * four-second wait of its own, so it is a decision rather than a second trigger — and seventy still
	 * leaves something to shoot with. The charger's left click is its shot instead; scoping is what its
	 * right click does.
	 *
	 * <p>It now behaves like Splatoon's: it bounces where it is thrown and only <em>then</em> counts
	 * down, {@link #SPECIAL_FUSE} ticks of it, so a bomb is a thing you can run away from rather than a
	 * contact grenade. The blast falls off from {@link #SPECIAL_DAMAGE} at the centre to
	 * {@link #SPECIAL_EDGE_DAMAGE} at {@link #SPECIAL_BLAST} blocks, linear in distance squared, which
	 * is the shape Splatcraft's own explosion uses. No line-of-sight test: a blast that has to see you
	 * costs a clip per victim and is wrong as often as it is right in a world of stairs.
	 */
	public static final int SPECIAL_INK = 70;
	public static final int SPECIAL_COOLDOWN = 80;
	/**
	 * The bomb's own wait before standing in your own ink starts refilling the tank — {@code
	 * splat_bomb.json}'s {@code ink_recovery_cooldown}, which is the bomb's rather than the weapon it was
	 * thrown from: seventy ink out of a hundred wants a beat of its own before it comes back.
	 */
	public static final int SPECIAL_REFILL_DELAY = 20;
	/** How far the splash reaches: 3 is 7×7 on the face it lands on. */
	public static final int SPECIAL_RADIUS = 3;
	/** Hearts at the centre of the blast, and at its edge {@link #SPECIAL_BLAST} blocks out. */
	public static final float SPECIAL_DAMAGE = 36.0f;
	public static final float SPECIAL_EDGE_DAMAGE = 6.0f;
	public static final double SPECIAL_BLAST = 3.25;
	/** Inside this, the blast is at full: the falloff starts at the edge of the bomb, not at its centre. */
	public static final double SPECIAL_CORE = 0.5;
	/** Ticks between landing and going off. */
	public static final int SPECIAL_FUSE = 20;
	/** A slow, heavy lob that gives everyone time to see it coming, and dies on its own after 2 s. */
	public static final float SPECIAL_VELOCITY = 0.75f;
	public static final double SPECIAL_GRAVITY = 0.06;
	public static final int SPECIAL_LIFETIME = 40;
	/** The bomb is a big blob: this is its display scale outright, not a multiple of a ball's. */
	public static final float SPECIAL_SCALE = 1.6f;
	/** It is a lob, so it leaves above the crosshair, in degrees of pitch; negative is up. */
	public static final float SPECIAL_PITCH = -30.0f;

	/** What a bounce droplet is worth — a graze, not a shot. */
	public static final float DROPLET_DAMAGE = 0.5f;

	/**
	 * Ticks after a shot before standing in your own ink starts topping the tank up again — Splatcraft's
	 * {@code ink_recovery_cooldown}, per weapon: a shooter is back on the tap almost at once, a roller
	 * has to stop rolling first. It is why a weapon cannot be fired and refilled in the same breath.
	 */
	public final int refillDelay;

	Weapon(String id, String displayName, int inkPerShot, int cooldownTicks, float velocity, float inaccuracy,
			float kickPitch, float damage, int refillDelay) {
		this.refillDelay = refillDelay;
		this.id = id;
		this.displayName = displayName;
		this.inkPerShot = inkPerShot;
		this.cooldownTicks = cooldownTicks;
		this.velocity = velocity;
		this.inaccuracy = inaccuracy;
		this.kickPitch = kickPitch;
		this.damage = damage;
	}

	/**
	 * What {@code /rivals gun} offers and what the help lists: the weapon's own name, lowercased. It is
	 * the registry id for three of the four, and for the shooter it is {@code shooter} rather than the
	 * v2 registry id {@code paint_gun} — nobody should have to type the latter to get the former.
	 */
	public String commandId() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Either name a weapon answers to: its {@link #commandId} or its registry {@link #id}. */
	public static Optional<Weapon> byId(String id) {
		for (Weapon weapon : values()) {
			if (weapon.id.equals(id) || weapon.commandId().equals(id)) return Optional.of(weapon);
		}
		return Optional.empty();
	}

	/** The names players type, comma-separated, for command help and failure messages. */
	public static String idList() {
		return Stream.of(values()).map(Weapon::commandId).collect(Collectors.joining(", "));
	}
}
