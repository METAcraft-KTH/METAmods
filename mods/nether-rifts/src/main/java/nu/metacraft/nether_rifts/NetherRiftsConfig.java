package nu.metacraft.nether_rifts;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import nu.metacraft.nether_rifts.mob.BombingRunGoal;
import nu.metacraft.nether_rifts.mob.BurningPatches;
import nu.metacraft.nether_rifts.mob.CrewHitbox;
import nu.metacraft.nether_rifts.mob.GhastBombardier;
import nu.metacraft.nether_rifts.mob.MagmaBomb;
import nu.metacraft.nether_rifts.mob.WarDrummer;
import nu.metacraft.nether_rifts.mob.WarGhast;
import nu.metacraft.nether_rifts.raid.Roster;
import nu.metacraft.nether_rifts.raid.Slot;
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
	 * The war ghast's bombing runs, and its Ghast Bombardier's magma bombs: dropped from the deck, and thrown by hand once
	 * the ghast has fallen. Ticks (20 a second) and blocks.
	 */
	public record BombardierTuning(
			double diveHeight, double climbHeight, double runLength, double bombRange, int bombEvery, int cooldown,
			float bombPower, int warnTicks, int burnTicks, int playerFireTicks, double patchRadius,
			int throwEvery, double throwMinRange, double throwRange, double maxFloatHeight, CrewTuning crew
	) {
		public static final BombardierTuning DEFAULT = new BombardierTuning(
				BombingRunGoal.DIVE_HEIGHT, BombingRunGoal.CLIMB_HEIGHT, BombingRunGoal.RUN_LENGTH, BombingRunGoal.BOMB_RANGE,
				BombingRunGoal.BOMB_EVERY, BombingRunGoal.COOLDOWN, MagmaBomb.POWER,
				BurningPatches.WARN_TICKS, BurningPatches.BURN_TICKS, BurningPatches.PLAYER_FIRE_TICKS, BurningPatches.RADIUS,
				GhastBombardier.THROW_EVERY, GhastBombardier.THROW_MIN_RANGE, GhastBombardier.THROW_RANGE, WarGhast.MAX_FLOAT_HEIGHT, CrewTuning.DEFAULT
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
						"Ticks between runs."
				).forGetter(BombardierTuning::cooldown),
				CommentCodec.comment(
						Sections.section("bomb_power", Codec.floatRange(0, 10), DEFAULT.bombPower()),
						"Explosion power of a bomb, which sets its damage and knockback (a creeper is 3). Bombs never change blocks."
				).forGetter(BombardierTuning::bombPower),
				CommentCodec.comment(
						Sections.section("warn_ticks", Codec.intRange(0, 200), DEFAULT.warnTicks()),
						"The ground warning shows at least this many ticks before a bomb lands."
				).forGetter(BombardierTuning::warnTicks),
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
						"The radius of a warning ring and a burning patch, in blocks."
				).forGetter(BombardierTuning::patchRadius),
				CommentCodec.comment(
						Sections.section("throw_every", Codec.intRange(1, 72_000), DEFAULT.throwEvery()),
						"On foot (its chained ghast fallen), the Grenadier throws a bomb at most this often, in ticks."
				).forGetter(BombardierTuning::throwEvery),
				CommentCodec.comment(
						Sections.section("throw_min_range", Codec.doubleRange(0, 64), DEFAULT.throwMinRange()),
						"It throws at players at least this many blocks off; closer, it fights with its sword."
				).forGetter(BombardierTuning::throwMinRange),
				CommentCodec.comment(
						Sections.section("throw_range", Codec.doubleRange(0, 64), DEFAULT.throwRange()),
						"It throws at players at most this many blocks off. A thrown bomb flies at least warn_ticks."
				).forGetter(BombardierTuning::throwRange),
				CommentCodec.comment(
						Sections.section("max_float_height", Codec.doubleRange(1, 256), DEFAULT.maxFloatHeight()),
						"Floating about, the ghast keeps at most this many blocks above the ground under it (a bombing run's climb may go higher)."
				).forGetter(BombardierTuning::maxFloatHeight),
				CommentCodec.comment(
						Sections.section("crew", CrewTuning.CODEC, CrewTuning.DEFAULT),
						"The crew on the deck."
				).forGetter(BombardierTuning::crew)
		).apply(i, BombardierTuning::new));
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
