package se.metacraft.nether_rifts;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import se.metacraft.nether_rifts.mob.BombingRunGoal;
import se.metacraft.nether_rifts.mob.BurningPatches;
import se.metacraft.nether_rifts.mob.CrewHitbox;
import se.metacraft.nether_rifts.mob.GhastBombardier;
import se.metacraft.nether_rifts.mob.MagmaBomb;
import se.metacraft.nether_rifts.mob.WarDrummer;
import se.metacraft.nether_rifts.mob.WarGhast;
import se.metacraft.nether_rifts.raid.Roster;
import se.metacraft.nether_rifts.raid.Slot;
import se.metacraft.config.container.ConfigContainer;
import se.metacraft.config.util.CommentCodec;
import se.metacraft.portalopening.raid.MobEntry;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** {@code config/nether-rifts.json5}. */
public record NetherRiftsConfig(
		Tiers tiers, Timers timers, Ranges ranges, int activeRaidCap,
		double trooperScalingPerPlayer, double trooperScalingCap,
		Map<Block, Block> frameCollapse, List<Identifier> scfNetherPortalTypes,
		Map<Slot, MobEntry> roster, Mobs mobs
) {

	public record Tiers(int smallMax, int mediumMax) {
		public static final Tiers DEFAULT = new Tiers(20, 60);
		public static final Codec<Tiers> CODEC = RecordCodecBuilder.<Tiers>create(i -> i.group(
				Codec.intRange(6, 441).fieldOf("small_max").forGetter(Tiers::smallMax),
				Codec.intRange(6, 441).fieldOf("medium_max").forGetter(Tiers::mediumMax)
		).apply(i, Tiers::new)).validate(Tiers::check);

		/** Medium rifts must be reachable: no small rift may be bigger than the largest medium one. */
		private static DataResult<Tiers> check(Tiers tiers) {
			return tiers.smallMax() <= tiers.mediumMax()
					? DataResult.success(tiers)
					: DataResult.error(() -> "tiers: small_max (" + tiers.smallMax() + ") must not be above medium_max (" + tiers.mediumMax() + ")");
		}
	}

	/** All in ticks. */
	public record Timers(int unstableFallback, int countdown, int glowAfter, int abandonAfter) {
		public static final Timers DEFAULT = new Timers(6000, 200, 1800, 3600);
		public static final Codec<Timers> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(1, 1_000_000).fieldOf("unstable_fallback").forGetter(Timers::unstableFallback),
				Codec.intRange(1, 1_000_000).fieldOf("countdown").forGetter(Timers::countdown),
				Codec.intRange(1, 1_000_000).fieldOf("glow_after").forGetter(Timers::glowAfter),
				Codec.intRange(1, 1_000_000).fieldOf("abandon_after").forGetter(Timers::abandonAfter)
		).apply(i, Timers::new));
	}

	/** All in blocks from the rift's centre. */
	public record Ranges(int warning, int participants, int tether) {
		public static final Ranges DEFAULT = new Ranges(32, 48, 64);
		public static final Codec<Ranges> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(1, 256).fieldOf("warning").forGetter(Ranges::warning),
				Codec.intRange(1, 256).fieldOf("participants").forGetter(Ranges::participants),
				Codec.intRange(1, 256).fieldOf("tether").forGetter(Ranges::tether)
		).apply(i, Ranges::new));
	}

	/** The War Drummer. Ticks (20 a second) and blocks. */
	public record DrummerTuning(int beatTicks, int bigBeatEvery, double buffRange, int buffTicks, double shockRange, double shockStrength,
			float shockDamage, double naturalChance) {
		public static final DrummerTuning DEFAULT = new DrummerTuning(
				WarDrummer.BEAT_TICKS, WarDrummer.BIG_BEAT_EVERY, WarDrummer.BUFF_RANGE, WarDrummer.BUFF_TICKS,
				WarDrummer.SHOCK_RANGE, WarDrummer.SHOCK_STRENGTH, WarDrummer.SHOCK_DAMAGE, WarDrummer.NATURAL_CHANCE
		);
		public static final Codec<DrummerTuning> CODEC = RecordCodecBuilder.create(i -> i.group(
				CommentCodec.comment(
						Sections.section("beat_ticks", Codec.intRange(1, 1200), DEFAULT.beatTicks()),
						"Ticks between beats."
				).forGetter(DrummerTuning::beatTicks),
				CommentCodec.comment(
						Sections.section("big_beat_every", Codec.intRange(1, 100), DEFAULT.bigBeatEvery()),
						"Every this many beats is the big beat (War Beat and Shock Beat)."
				).forGetter(DrummerTuning::bigBeatEvery),
				CommentCodec.comment(
						Sections.section("buff_range", Codec.doubleRange(0, 64), DEFAULT.buffRange()),
						"War Beat: piglins within this many blocks get Speed I and Strength I (a rift's drummer buffs rift piglins, a nether one the nether's own)."
				).forGetter(DrummerTuning::buffRange),
				CommentCodec.comment(
						Sections.section("buff_ticks", Codec.intRange(0, 72_000), DEFAULT.buffTicks()),
						"War Beat: how long the buff lasts, in ticks."
				).forGetter(DrummerTuning::buffTicks),
				CommentCodec.comment(
						Sections.section("shock_range", Codec.doubleRange(0, 32), DEFAULT.shockRange()),
						"Shock Beat: players within this many blocks are knocked back."
				).forGetter(DrummerTuning::shockRange),
				CommentCodec.comment(
						Sections.section("shock_strength", Codec.doubleRange(0, 10), DEFAULT.shockStrength()),
						"Shock Beat: knockback strength (a sword hit with Knockback I is about 0.9)."
				).forGetter(DrummerTuning::shockStrength),
				CommentCodec.comment(
						Sections.section("shock_damage", Codec.floatRange(0, 100), DEFAULT.shockDamage()),
						"Shock Beat: damage to each player it reaches (2 = one heart)."
				).forGetter(DrummerTuning::shockDamage),
				CommentCodec.comment(
						Sections.section("natural_chance", Codec.doubleRange(0, 1), DEFAULT.naturalChance()),
						"The chance that a piglin spawning naturally in the nether brings a drummer along (0 = never)."
				).forGetter(DrummerTuning::naturalChance)
		).apply(i, DrummerTuning::new));
	}

	/** The Chained Ghast's crew on its deck: how hard they are to hit and hurt, and the crossbow piglin's bolts. */
	public record CrewTuning(int crossbowEvery, double damageTaken, double hitboxWidth, double hitboxHeight) {
		public static final CrewTuning DEFAULT = new CrewTuning(WarGhast.CROSSBOW_EVERY, CrewHitbox.DAMAGE_TAKEN, CrewHitbox.WIDTH, CrewHitbox.HEIGHT);
		public static final Codec<CrewTuning> CODEC = RecordCodecBuilder.create(i -> i.group(
				CommentCodec.comment(
						Sections.section("crossbow_every", Codec.intRange(0, 72_000), DEFAULT.crossbowEvery()),
						"The crossbow piglin shoots a bolt this often, in ticks, at a player it can see within 24 blocks (0: never)."
				).forGetter(CrewTuning::crossbowEvery),
				CommentCodec.comment(
						Sections.section("damage_taken", Codec.doubleRange(0, 1), DEFAULT.damageTaken()),
						"The share of the damage the crew take on the deck, after armour: the deck gives them cover (1: all of it)."
				).forGetter(CrewTuning::damageTaken),
				CommentCodec.comment(
						Sections.section("hitbox_width", Codec.doubleRange(0.1, 4), DEFAULT.hitboxWidth()),
						"A piglin's hitbox on the deck, across, in blocks: roomier than a piglin's 0.6, as it doesn't turn with its figure."
				).forGetter(CrewTuning::hitboxWidth),
				CommentCodec.comment(
						Sections.section("hitbox_height", Codec.doubleRange(0.1, 6), DEFAULT.hitboxHeight()),
						"A piglin's hitbox on the deck, up from its feet, in blocks (a piglin is 1.95)."
				).forGetter(CrewTuning::hitboxHeight)
		).apply(i, CrewTuning::new));
	}

	/**
	 * A bomb's ground warning: how long ahead it shows, whether it casts a shadow, and how wide it is (0: as wide as the
	 * blast reaches, twice {@code bomb_power}). Its keys sit in the bombardier section with the others ({@code warn_...}).
	 */
	public record Warning(int ticks, boolean shadow, double radius) {
		public static final Warning DEFAULT = new Warning(BurningPatches.WARN_TICKS, true, 0);
		public static final MapCodec<Warning> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				CommentCodec.comment(
						Sections.section("warn_ticks", Codec.intRange(0, 200), DEFAULT.ticks()),
						"The ground warning shows at least this many ticks before a bomb lands."
				).forGetter(Warning::ticks),
				CommentCodec.comment(
						Sections.section("warn_shadow", Codec.BOOL, DEFAULT.shadow()),
						"The warning shows a red ring on the ground and the bomb's shadow, darkening as it falls (else only embers)."
				).forGetter(Warning::shadow),
				CommentCodec.comment(
						Sections.section("warn_radius", Codec.doubleRange(0, 16), DEFAULT.radius()),
						"The warning's radius, in blocks; 0: as far as the blast reaches (twice bomb_power)."
				).forGetter(Warning::radius)
		).apply(i, Warning::new));
	}

	/**
	 * The Grenadier's throws on foot. Its keys sit in the bombardier section with the others ({@code throw_...}): a record
	 * of its own only because a codec takes at most 16 fields.
	 */
	public record Throwing(int every, double minRange, double range) {
		public static final Throwing DEFAULT = new Throwing(GhastBombardier.THROW_EVERY, GhastBombardier.THROW_MIN_RANGE, GhastBombardier.THROW_RANGE);
		public static final MapCodec<Throwing> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				CommentCodec.comment(
						Sections.section("throw_every", Codec.intRange(1, 72_000), DEFAULT.every()),
						"On foot (its chained ghast fallen), the Grenadier throws a bomb at most this often, in ticks."
				).forGetter(Throwing::every),
				CommentCodec.comment(
						Sections.section("throw_min_range", Codec.doubleRange(0, 64), DEFAULT.minRange()),
						"It throws at players at least this many blocks off; closer, it fights with its sword."
				).forGetter(Throwing::minRange),
				CommentCodec.comment(
						Sections.section("throw_range", Codec.doubleRange(0, 64), DEFAULT.range()),
						"It throws at players at most this many blocks off. A thrown bomb flies at least warn_ticks."
				).forGetter(Throwing::range)
		).apply(i, Throwing::new));
	}

	/**
	 * How a Chained Ghast picks its attacks, one at a time ({@code GhastTactics}): it cruises (its crossbow piglin's bolts
	 * only), telegraphs a bombing run, makes it, climbs, shoots a volley of fireballs, regroups, and cruises again.
	 * Ticks (20 a second).
	 */
	public record TacticsTuning(int telegraphTicks, int maxBombsPerRun, int volleySize, int volleySpacing, int regroupTicks,
			int globalCooldown, int volleyCooldown, boolean boltsInCruiseOnly, int phaseTimeout) {
		public static final TacticsTuning DEFAULT = new TacticsTuning(20, 6, 2, 20, 60, 40, 200, true, 120);
		public static final Codec<TacticsTuning> CODEC = RecordCodecBuilder.create(i -> i.group(
				CommentCodec.comment(
						Sections.section("telegraph_ticks", Codec.intRange(0, 200), DEFAULT.telegraphTicks()),
						"Before a bombing run the ghast wails, its drummer war-cries and it pitches into the dive for this long."
				).forGetter(TacticsTuning::telegraphTicks),
				CommentCodec.comment(
						Sections.section("max_bombs_per_run", Codec.intRange(0, 100), DEFAULT.maxBombsPerRun()),
						"The Grenadier drops at most this many bombs on a run."
				).forGetter(TacticsTuning::maxBombsPerRun),
				CommentCodec.comment(
						Sections.section("volley_size", Codec.intRange(1, 10), DEFAULT.volleySize()),
						"Fireballs in a volley, each charged (its shooting face, the spear piglin stabbing) and heard before it flies."
				).forGetter(TacticsTuning::volleySize),
				CommentCodec.comment(
						Sections.section("volley_spacing", Codec.intRange(11, 200), DEFAULT.volleySpacing()),
						"Ticks from one fireball's charge to the next's (a charge takes 10)."
				).forGetter(TacticsTuning::volleySpacing),
				CommentCodec.comment(
						Sections.section("regroup_ticks", Codec.intRange(0, 1200), DEFAULT.regroupTicks()),
						"After a run or a volley the ghast holds off this long, no attacks at all."
				).forGetter(TacticsTuning::regroupTicks),
				CommentCodec.comment(
						Sections.section("global_cooldown", Codec.intRange(0, 1200), DEFAULT.globalCooldown()),
						"And this long again, cruising, before its next run or volley."
				).forGetter(TacticsTuning::globalCooldown),
				CommentCodec.comment(
						Sections.section("volley_cooldown", Codec.intRange(0, 72_000), DEFAULT.volleyCooldown()),
						"Ticks from the end of a volley till the next can start (cooldown is the bombing run's)."
				).forGetter(TacticsTuning::volleyCooldown),
				CommentCodec.comment(
						Sections.section("bolts_in_cruise_only", Codec.BOOL, DEFAULT.boltsInCruiseOnly()),
						"The crossbow piglin shoots only while the ghast cruises or regroups (else at any time but a run or a volley)."
				).forGetter(TacticsTuning::boltsInCruiseOnly),
				CommentCodec.comment(
						Sections.section("phase_timeout", Codec.intRange(20, 1200), DEFAULT.phaseTimeout()),
						"A run's dive, its pass and its climb, and a volley, each give up after this many ticks."
				).forGetter(TacticsTuning::phaseTimeout)
		).apply(i, TacticsTuning::new));
	}

	/**
	 * The war ghast's bombing runs, and its Ghast Bombardier's magma bombs: dropped from the deck, and thrown by hand once
	 * the ghast has fallen. Ticks (20 a second) and blocks.
	 */
	public record BombardierTuning(
			double diveHeight, double climbHeight, double runLength, double bombRange, int bombEvery, int cooldown,
			float bombPower, Warning warning, int burnTicks, int playerFireTicks, double patchRadius,
			Throwing throwing, double maxFloatHeight, CrewTuning crew, TacticsTuning tactics
	) {
		public static final BombardierTuning DEFAULT = new BombardierTuning(
				BombingRunGoal.DIVE_HEIGHT, BombingRunGoal.CLIMB_HEIGHT, BombingRunGoal.RUN_LENGTH, BombingRunGoal.BOMB_RANGE,
				BombingRunGoal.BOMB_EVERY, BombingRunGoal.COOLDOWN, MagmaBomb.POWER,
				Warning.DEFAULT, BurningPatches.BURN_TICKS, BurningPatches.PLAYER_FIRE_TICKS, BurningPatches.RADIUS,
				Throwing.DEFAULT, WarGhast.MAX_FLOAT_HEIGHT, CrewTuning.DEFAULT, TacticsTuning.DEFAULT
		);
		public static final Codec<BombardierTuning> CODEC = RecordCodecBuilder.create(i -> i.group(
				CommentCodec.comment(
						Sections.section("dive_height", Codec.doubleRange(1, 64), DEFAULT.diveHeight()),
						"On a run the chained ghast's deck (hung about 6 blocks under it) flies this many blocks above the target."
				).forGetter(BombardierTuning::diveHeight),
				CommentCodec.comment(
						Sections.section("climb_height", Codec.doubleRange(1, 128), DEFAULT.climbHeight()),
						"After a run the ghast climbs till its deck is this many blocks above the target."
				).forGetter(BombardierTuning::climbHeight),
				CommentCodec.comment(
						Sections.section("run_length", Codec.doubleRange(1, 128), DEFAULT.runLength()),
						"The run's straight line across the target, in blocks."
				).forGetter(BombardierTuning::runLength),
				CommentCodec.comment(
						Sections.section("bomb_range", Codec.doubleRange(0, 64), DEFAULT.bombRange()),
						"Bombs drop while the ghast is within this many blocks of the target, horizontally."
				).forGetter(BombardierTuning::bombRange),
				CommentCodec.comment(
						Sections.section("bomb_every", Codec.intRange(1, 200), DEFAULT.bombEvery()),
						"Ticks between bombs on a run."
				).forGetter(BombardierTuning::bombEvery),
				CommentCodec.comment(
						Sections.section("cooldown", Codec.intRange(0, 72_000), DEFAULT.cooldown()),
						"Ticks from the end of a bombing run till the next can start."
				).forGetter(BombardierTuning::cooldown),
				CommentCodec.comment(
						Sections.section("bomb_power", Codec.floatRange(0, 10), DEFAULT.bombPower()),
						"Explosion power of a bomb, which sets its damage and knockback (a creeper is 3). Bombs never change blocks."
				).forGetter(BombardierTuning::bombPower),
				Warning.MAP_CODEC.forGetter(BombardierTuning::warning),
				CommentCodec.comment(
						Sections.section("burn_ticks", Codec.intRange(0, 72_000), DEFAULT.burnTicks()),
						"How long a bomb's burning patch lasts, in ticks."
				).forGetter(BombardierTuning::burnTicks),
				CommentCodec.comment(
						Sections.section("player_fire_ticks", Codec.intRange(0, 72_000), DEFAULT.playerFireTicks()),
						"Players in a burning patch burn for this many ticks."
				).forGetter(BombardierTuning::playerFireTicks),
				CommentCodec.comment(
						Sections.section("patch_radius", Codec.doubleRange(0, 16), DEFAULT.patchRadius()),
						"The radius of a burning patch, in blocks."
				).forGetter(BombardierTuning::patchRadius),
				Throwing.MAP_CODEC.forGetter(BombardierTuning::throwing),
				CommentCodec.comment(
						Sections.section("max_float_height", Codec.doubleRange(1, 256), DEFAULT.maxFloatHeight()),
						"Floating about, the ghast keeps at most this many blocks above the ground under it (a bombing run's climb may go higher)."
				).forGetter(BombardierTuning::maxFloatHeight),
				CommentCodec.comment(
						Sections.section("crew", CrewTuning.CODEC, CrewTuning.DEFAULT),
						"The crew on the deck."
				).forGetter(BombardierTuning::crew),
				CommentCodec.comment(
						Sections.section("tactics", TacticsTuning.CODEC, TacticsTuning.DEFAULT),
						"How the chained ghast takes its attacks in turn: cruise (bolts only), telegraph, bombing run, climb, fireball volley, regroup."
				).forGetter(BombardierTuning::tactics)
		).apply(i, BombardierTuning::new));

		public BombardierTuning withCrew(CrewTuning crew) {
			return new BombardierTuning(diveHeight, climbHeight, runLength, bombRange, bombEvery, cooldown, bombPower, warning, burnTicks,
					playerFireTicks, patchRadius, throwing, maxFloatHeight, crew, tactics);
		}

		public BombardierTuning withTactics(int cooldown, TacticsTuning tactics) {
			return new BombardierTuning(diveHeight, climbHeight, runLength, bombRange, bombEvery, cooldown, bombPower, warning, burnTicks,
					playerFireTicks, patchRadius, throwing, maxFloatHeight, crew, tactics);
		}

		public int throwEvery() {
			return throwing.every();
		}

		public double throwMinRange() {
			return throwing.minRange();
		}

		public double throwRange() {
			return throwing.range();
		}

		/** The warning's lead, in ticks. */
		public int warnTicks() {
			return warning.ticks();
		}

		/** How wide the warning is: the configured radius, else the blast's reach (an explosion's is twice its power). */
		public double warnRadius() {
			return warning.radius() > 0 ? warning.radius() : 2 * bombPower;
		}
	}

	public record Mobs(DrummerTuning drummer, BombardierTuning bombardier) {
		public static final Mobs DEFAULT = new Mobs(DrummerTuning.DEFAULT, BombardierTuning.DEFAULT);
		public static final Codec<Mobs> CODEC = RecordCodecBuilder.create(i -> i.group(
				CommentCodec.comment(
						Sections.section("drummer", DrummerTuning.CODEC, DrummerTuning.DEFAULT),
						"The Piglin Drummer, which leads waves from wave 2, and now and then walks with the nether's own piglins."
				).forGetter(Mobs::drummer),
				CommentCodec.comment(
						Sections.section("bombardier", BombardierTuning.CODEC, BombardierTuning.DEFAULT),
						"Chained ghasts, crewed by a Piglin Drummer, a Piglin Grenadier and two piglins, in Large waves 5 and later; the Grenadier's bombs, from the deck and by hand."
				).forGetter(Mobs::bombardier)
		).apply(i, Mobs::new));
	}

	public static final NetherRiftsConfig DEFAULT = new NetherRiftsConfig(
			Tiers.DEFAULT, Timers.DEFAULT, Ranges.DEFAULT, 4, 0.25, 2.0,
			Map.of(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN), List.of(), Roster.DEFAULT, Mobs.DEFAULT
	);

	private static final Codec<Map<Block, Block>> BLOCK_MAP = Codec.unboundedMap(
			BuiltInRegistries.BLOCK.byNameCodec(), BuiltInRegistries.BLOCK.byNameCodec()
	);

	public static final MapCodec<NetherRiftsConfig> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			CommentCodec.comment(
					Sections.section("tiers", Tiers.CODEC, Tiers.DEFAULT),
					"Rift blocks up to small_max make a small raid (3 waves), up to medium_max a medium one (5), more a large one (7)."
			).forGetter(NetherRiftsConfig::tiers),
			CommentCodec.comment(
					Sections.section("timers", Timers.CODEC, Timers.DEFAULT),
					"Ticks (20 a second): unstable rifts settle back, the countdown before wave 1, when stragglers glow, when an abandoned raid collapses."
			).forGetter(NetherRiftsConfig::timers),
			CommentCodec.comment(
					Sections.section("ranges", Ranges.CODEC, Ranges.DEFAULT),
					"Blocks from a rift's centre: who is warned, who takes part, how far rift mobs may stray."
			).forGetter(NetherRiftsConfig::ranges),
			CommentCodec.comment(
					Codec.intRange(0, 1000).optionalFieldOf("active_raid_cap", 4),
					"How many rifts may be torn open at once, server-wide."
			).forGetter(NetherRiftsConfig::activeRaidCap),
			CommentCodec.comment(
					Codec.doubleRange(0, 10).optionalFieldOf("trooper_scaling_per_player", 0.25),
					"Each player past the first adds this share of troopers to a wave."
			).forGetter(NetherRiftsConfig::trooperScalingPerPlayer),
			CommentCodec.comment(
					Codec.doubleRange(1, 10).optionalFieldOf("trooper_scaling_cap", 2.0),
					"Troopers never grow past this many times the vanilla count."
			).forGetter(NetherRiftsConfig::trooperScalingCap),
			CommentCodec.comment(
					BLOCK_MAP.optionalFieldOf("frame_collapse", Map.of(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN)),
					"What each frame block becomes when its rift closes. Unlisted blocks become air; crying-obsidian rifts always leave air."
			).forGetter(NetherRiftsConfig::frameCollapse),
			CommentCodec.comment(
					Identifier.CODEC.listOf().optionalFieldOf("scf_nether_portal_types", List.of()),
					"simple-custom-features dynamic portal blocks that count as nether portals."
			).forGetter(NetherRiftsConfig::scfNetherPortalTypes),
			CommentCodec.comment(
					Roster.CODEC.optionalFieldOf("roster", Roster.DEFAULT),
					"What each wave slot spawns: portal-opening mob entries (weighted entity NBT)."
			).forGetter(NetherRiftsConfig::roster),
			CommentCodec.comment(
					Sections.section("mobs", Mobs.CODEC, Mobs.DEFAULT),
					"Tuning for the Piglin Drummer and the Piglin Grenadier. Any value left out keeps its default."
			).forGetter(NetherRiftsConfig::mobs)
	).apply(i, NetherRiftsConfig::new));

	/** The configured entry for a slot, or the default when the config file predates the slot. */
	public MobEntry rosterEntry(Slot slot) {
		var entry = roster.get(slot);
		return entry != null ? entry : Roster.DEFAULT.get(slot);
	}

	public static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(NetherRifts.MODID + ".json5");

	private static final ConfigContainer<NetherRiftsConfig> CONTAINER = ConfigContainer.Builder.create(
			CODEC, () -> DEFAULT
	).withPath(PATH).build(NetherRifts.MODID);

	/** An optional section that falls back to its defaults. Apart from the config class, so the nested records' codecs don't start its static init (DEFAULT needs them). */
	private static final class Sections {
		static <T> MapCodec<T> section(String name, Codec<T> codec, T defaults) {
			return codec.optionalFieldOf(name).xmap(present -> present.orElse(defaults), Optional::of);
		}
	}

	public static NetherRiftsConfig getInstance() {
		return CONTAINER.get();
	}

	public static void reload() {
		CONTAINER.reload();
	}
}
