import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.Bootstrap;
import se.metacraft.core.METAcraftCore;
import se.metacraft.lib.METAcraftLib;
import se.metacraft.lib.util.helper.TestHelper;
import se.metacraft.nether_rifts.NetherRifts;
import se.metacraft.nether_rifts.NetherRiftsConfig;
import se.metacraft.portable_jukebox.PortableJukebox;
import se.metacraft.portal_blocker.PortalBlocker;
import se.metacraft.simplecustomfeatures.Features;
import se.metacraft.nether_rifts.raid.Roster;
import se.metacraft.nether_rifts.raid.Slot;
import se.metacraft.zones.METAcraftZones;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.metacraft.portalopening.PortalOpening;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Every nether-rifts game test, on one test server (registries freeze after the first init). */
public class NetherRiftsTests {

	/** The registries must be bootstrapped before {@link #init} writes the roster file with the roster's codec. */
	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	/**
	 * Short timers and ranges, so raids finish inside a test and neighbouring tests' players don't take part. Set once
	 * the mods are initialised, before any test registers: building the config's codec checks the default roster's
	 * entity ids (metacraft-config), so the config class mustn't load before the mod's entities are registered.
	 */
	public static NetherRiftsConfig FAST;

	private static NetherRiftsConfig fast() {
		return new NetherRiftsConfig(
				NetherRiftsConfig.Tiers.DEFAULT,
				new NetherRiftsConfig.Timers(40, 20, 100, 60),
				new NetherRiftsConfig.Ranges(10, 10, 16),
				100, 0.25, 2.0, 4, 0.25, 2.0,
				NetherRiftsConfig.DEFAULT.frameCollapse(), NetherRiftsConfig.DEFAULT.scfNetherPortalTypes(),
				NetherRiftsConfig.DEFAULT.roster(), NetherRiftsConfig.Waves.DEFAULT, NetherRiftsConfig.DEFAULT.mobs()
		);
	}

	private static boolean initialised = false;

	/** The config as the mod loaded it in its own init, from {@link #writeRosterAtInit}'s file. */
	private static NetherRiftsConfig loadedAtInit;

	/**
	 * A config file of only a roster, with the Piglin General in the elite slot: one of the mod's own mobs where the
	 * default is a vanilla one, so it's seen whether the entry survives the config's load during the mod's init.
	 * Written with the roster's codec alone, which leaves the config class unloaded.
	 */
	private static void writeRosterAtInit() {
		var roster = Roster.CODEC.encodeStart(JsonOps.INSTANCE, java.util.Map.of(Slot.ELITE, Roster.DEFAULT.get(Slot.CAPTAIN))).getOrThrow();
		var json = new com.google.gson.JsonObject();
		json.add("roster", roster);
		writeConfigFile(json.toString());
	}

	@BeforeAll
	public static synchronized void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		writeScfPortal();
		// written, not loaded: the mod's init loads it
		writeRosterAtInit();
		TestHelper.init(
				() -> {
					FAST = fast();
					registerTestCommands();
					ModeTests.registerTests();
					ShapeTests.registerTests();
					FormingTests.registerTests();
					StageTests.registerTests();
					MobTests.registerTests();
					LootTests.registerTests();
					RaidTests.registerTests();
					StabiliseTests.registerTests();
					ScfTests.registerTests();
					DrummerTests.registerTests();
					BombTests.registerTests();
					BombardierTests.registerTests();
					GhastCrewTests.registerTests();
					GhastFlightTests.registerTests();
					GhastTacticsTests.registerTests();
					ShowcaseTests.registerTests();
					HeldItemsTests.registerTests();
					ArmourTests.registerTests();
					GeneralTests.registerTests();
				},
				METAcraftLib::new, METAcraftCore::new, METAcraftZones::new, PortalBlocker::new,
				PortalOpening::new, Features::new, de.tomalbrc.bil.BIL::new, NetherRifts::new, PortableJukebox::new
		);
		loadedAtInit = NetherRiftsConfig.getInstance();
		// the tests start from the defaults; each one's environment sets its own
		writeConfig(NetherRiftsConfig.DEFAULT);
	}

	public static void register(String path, Consumer<GameTestHelper> test) {
		Registry.register(BuiltInRegistries.TEST_FUNCTION, NetherRifts.getID(path), test);
	}

	/** A dynamic portal, any shape, blackstone frame, lit with a fire charge; nether-rifts counts it as a nether portal in the scf environment. */
	static void writeScfPortal() {
		var dir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("simple-custom-features");
		try {
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("nether_rifts_test.json"), """
					{"objects": [{"id": "nether_rifts:test_blob_portal", "object": {
						"type": "minecraft:dynamic_portal",
						"dimensions": {"minecraft:overworld": "minecraft:the_nether", "minecraft:the_nether": "minecraft:overworld"},
						"valid_frame_block": {"blocks": "minecraft:blackstone"},
						"item_activator": {"items": "minecraft:fire_charge"},
						"min_area": 6
					}}]}
					""");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static void writeConfig(NetherRiftsConfig config) {
		writeConfigFile(NetherRiftsConfig.CODEC.codec().encodeStart(JsonOps.INSTANCE, config).getOrThrow().toString());
		NetherRiftsConfig.reload();
	}

	/** Rewrites the real config file, without reloading it. */
	private static void writeConfigFile(String json) {
		// the config class's path, without loading the class (see FAST)
		var path = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve(NetherRifts.MODID + ".json5");
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, json);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** {@code riftstest <preset>}: rewrites the real config file and reloads it. Test environments call it. */
	static void registerTestCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("riftstest")
						.then(preset("fast", () -> FAST))
						.then(preset("default", () -> NetherRiftsConfig.DEFAULT))
						.then(preset("cap_one", () -> new NetherRiftsConfig(
								FAST.tiers(), FAST.timers(), FAST.ranges(), 1, FAST.trooperScalingPerPlayer(),
								FAST.trooperScalingCap(), FAST.eliteScalingFrom(), FAST.eliteScalingPerPlayer(), FAST.eliteScalingCap(),
								FAST.frameCollapse(), FAST.scfNetherPortalTypes(), FAST.roster(), FAST.waves(), FAST.mobs()
						)))
						.then(preset("scf", () -> new NetherRiftsConfig(
								FAST.tiers(), FAST.timers(), FAST.ranges(), FAST.activeRaidCap(), FAST.trooperScalingPerPlayer(),
								FAST.trooperScalingCap(), FAST.eliteScalingFrom(), FAST.eliteScalingPerPlayer(), FAST.eliteScalingCap(),
								FAST.frameCollapse(), java.util.List.of(NetherRifts.getID("test_blob_portal")), FAST.roster(), FAST.waves(), FAST.mobs()
						)))
						.then(preset("roster_partial", () -> {
							var roster = new java.util.HashMap<>(Roster.DEFAULT);
							roster.remove(Slot.HEAVY);
							return new NetherRiftsConfig(FAST.tiers(), FAST.timers(), FAST.ranges(), FAST.activeRaidCap(),
									FAST.trooperScalingPerPlayer(), FAST.trooperScalingCap(), FAST.eliteScalingFrom(), FAST.eliteScalingPerPlayer(), FAST.eliteScalingCap(),
									FAST.frameCollapse(), FAST.scfNetherPortalTypes(), roster, FAST.waves(), FAST.mobs());
						}))
						.then(preset("drummer_short", () -> {
							var d = NetherRiftsConfig.DrummerTuning.DEFAULT;
							var drummer = new NetherRiftsConfig.DrummerTuning(d.beatTicks(), d.bigBeatEvery(), 6, d.buffTicks(), d.shockRange(), d.shockStrength(),
									d.shockDamage(), d.naturalChance());
							return new NetherRiftsConfig(FAST.tiers(), FAST.timers(), FAST.ranges(), FAST.activeRaidCap(),
									FAST.trooperScalingPerPlayer(), FAST.trooperScalingCap(), FAST.eliteScalingFrom(), FAST.eliteScalingPerPlayer(), FAST.eliteScalingCap(),
									FAST.frameCollapse(), FAST.scfNetherPortalTypes(), FAST.roster(), FAST.waves(), new NetherRiftsConfig.Mobs(drummer, FAST.mobs().bombardier(), FAST.mobs().general()));
						}))
						.then(preset("drummer_natural", () -> naturalChance(1)))
						.then(preset("drummer_never", () -> naturalChance(0)))
						.then(preset("crossbow_never", () -> crossbowEvery(0)))
						.then(preset("tactics_fast", NetherRiftsTests::tacticsFast))
						.then(preset("captain_on", NetherRiftsTests::captainOn))
		));
	}

	/** The defaults, with every natural piglin spawn in the nether bringing a drummer at this chance. */
	private static NetherRiftsConfig naturalChance(double chance) {
		var d = NetherRiftsConfig.DrummerTuning.DEFAULT;
		var c = NetherRiftsConfig.DEFAULT;
		var drummer = new NetherRiftsConfig.DrummerTuning(d.beatTicks(), d.bigBeatEvery(), d.buffRange(), d.buffTicks(), d.shockRange(),
				d.shockStrength(), d.shockDamage(), chance);
		return new NetherRiftsConfig(c.tiers(), c.timers(), c.ranges(), c.activeRaidCap(), c.trooperScalingPerPlayer(), c.trooperScalingCap(),
				c.eliteScalingFrom(), c.eliteScalingPerPlayer(), c.eliteScalingCap(),
				c.frameCollapse(), c.scfNetherPortalTypes(), c.roster(), c.waves(), new NetherRiftsConfig.Mobs(drummer, c.mobs().bombardier(), c.mobs().general()));
	}

	/** FAST, with the Piglin General in the last large wave. */
	static NetherRiftsConfig captainOn() {
		var large = new java.util.ArrayList<>(FAST.waves().large());
		var last = new java.util.HashMap<>(large.getLast());
		last.put(Slot.CAPTAIN, 1);
		large.set(large.size() - 1, last);
		return withWaves(FAST, new NetherRiftsConfig.Waves(FAST.waves().small(), FAST.waves().medium(), large));
	}

	/** The config with these waves instead. */
	static NetherRiftsConfig withWaves(NetherRiftsConfig c, NetherRiftsConfig.Waves waves) {
		return new NetherRiftsConfig(c.tiers(), c.timers(), c.ranges(), c.activeRaidCap(), c.trooperScalingPerPlayer(), c.trooperScalingCap(),
				c.eliteScalingFrom(), c.eliteScalingPerPlayer(), c.eliteScalingCap(),
				c.frameCollapse(), c.scfNetherPortalTypes(), c.roster(), waves, c.mobs());
	}

	/** The Chained Ghast's tactics turned quicker (shorter cooldowns and rests, 3 bombs a run), so a test sees a few rounds. */
	public static final NetherRiftsConfig.TacticsTuning TACTICS_FAST = new NetherRiftsConfig.TacticsTuning(20, 3, 2, 20, 30, 20, 100, true, 120);
	public static final int RUN_COOLDOWN_FAST = 120;

	static NetherRiftsConfig tacticsFast() {
		var c = NetherRiftsConfig.DEFAULT;
		var bombardier = NetherRiftsConfig.BombardierTuning.DEFAULT.withTactics(RUN_COOLDOWN_FAST, TACTICS_FAST);
		return new NetherRiftsConfig(c.tiers(), c.timers(), c.ranges(), c.activeRaidCap(), c.trooperScalingPerPlayer(), c.trooperScalingCap(),
				c.eliteScalingFrom(), c.eliteScalingPerPlayer(), c.eliteScalingCap(),
				c.frameCollapse(), c.scfNetherPortalTypes(), c.roster(), c.waves(), new NetherRiftsConfig.Mobs(c.mobs().drummer(), bombardier, c.mobs().general()));
	}

	/** The defaults, with the Chained Ghast's crossbow piglin shooting this often. */
	private static NetherRiftsConfig crossbowEvery(int every) {
		var b = NetherRiftsConfig.BombardierTuning.DEFAULT;
		var c = NetherRiftsConfig.DEFAULT;
		var bombardier = b.withCrew(new NetherRiftsConfig.CrewTuning(every, b.crew().damageTaken(), b.crew().hitboxWidth(), b.crew().hitboxHeight()));
		return new NetherRiftsConfig(c.tiers(), c.timers(), c.ranges(), c.activeRaidCap(), c.trooperScalingPerPlayer(), c.trooperScalingCap(),
				c.eliteScalingFrom(), c.eliteScalingPerPlayer(), c.eliteScalingCap(),
				c.frameCollapse(), c.scfNetherPortalTypes(), c.roster(), c.waves(), new NetherRiftsConfig.Mobs(c.mobs().drummer(), bombardier, c.mobs().general()));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> preset(String name, Supplier<NetherRiftsConfig> config) {
		return Commands.literal(name).executes(ctx -> {
			writeConfig(config.get());
			return 1;
		});
	}

	/** A roster naming the mod's own mobs loads with them, though the config loads in the mod's init. */
	@Test
	public void rosterOfOwnMobsLoadsAtInit() {
		var ids = loadedAtInit.rosterEntry(Slot.ELITE).mobs().unwrap().stream()
				.map(weighted -> weighted.value().data().getStringOr("id", ""))
				.toList();
		org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("nether_rifts:piglin_general"), ids,
				"the elite slot as the mod's init loaded it");
	}

	@Test
	public void gameTests() throws Exception {
		TestHelper.runTestServer("nether_rifts", "*");
	}
}
