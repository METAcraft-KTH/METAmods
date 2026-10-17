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
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.util.helper.TestHelper;
import nu.metacraft.nether_rifts.NetherRifts;
import nu.metacraft.nether_rifts.NetherRiftsConfig;
import nu.metacraft.portal_blocker.PortalBlocker;
import nu.metacraft.simplecustomfeatures.Features;
import nu.metacraft.zones.METAcraftZones;
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

	/** FAST names blocks, so the registries must be bootstrapped before this class's other statics run. */
	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	/** Short timers and ranges, so raids finish inside a test and neighbouring tests' players don't take part. */
	public static final NetherRiftsConfig FAST = new NetherRiftsConfig(
			NetherRiftsConfig.Tiers.DEFAULT,
			new NetherRiftsConfig.Timers(40, 20, 100, 60),
			new NetherRiftsConfig.Ranges(10, 10, 16),
			100, 0.25, 2.0,
			NetherRiftsConfig.DEFAULT.frameCollapse(), NetherRiftsConfig.DEFAULT.scfNetherPortalTypes(),
			NetherRiftsConfig.DEFAULT.roster(), NetherRiftsConfig.DEFAULT.mobs()
	);

	private static boolean initialised = false;

	@BeforeAll
	public static synchronized void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		writeScfPortal();
		TestHelper.init(
				() -> {
					registerTestCommands();
					ModeTests.registerTests();
					EyeLockTests.registerTests();
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
					ShowcaseTests.registerTests();
					CaptainTests.registerTests();
					HeldItemsTests.registerTests();
					ArmourTests.registerTests();
				},
				METAcraftLib::new, METAcraftCore::new, METAcraftZones::new, PortalBlocker::new,
				PortalOpening::new, Features::new, de.tomalbrc.bil.BIL::new, NetherRifts::new
		);
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
		var json = NetherRiftsConfig.CODEC.codec().encodeStart(JsonOps.INSTANCE, config).getOrThrow();
		try {
			Files.createDirectories(NetherRiftsConfig.PATH.getParent());
			Files.writeString(NetherRiftsConfig.PATH, json.toString());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		NetherRiftsConfig.reload();
	}

	/** {@code riftstest <preset>}: rewrites the real config file and reloads it. Test environments call it. */
	static void registerTestCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("riftstest")
						.then(preset("fast", () -> FAST))
						.then(preset("default", () -> NetherRiftsConfig.DEFAULT))
						.then(preset("cap_one", () -> new NetherRiftsConfig(
								FAST.tiers(), FAST.timers(), FAST.ranges(), 1, FAST.trooperScalingPerPlayer(),
								FAST.trooperScalingCap(), FAST.frameCollapse(), FAST.scfNetherPortalTypes(), FAST.roster(), FAST.mobs()
						)))
						.then(preset("scf", () -> new NetherRiftsConfig(
								FAST.tiers(), FAST.timers(), FAST.ranges(), FAST.activeRaidCap(), FAST.trooperScalingPerPlayer(),
								FAST.trooperScalingCap(), FAST.frameCollapse(),
								java.util.List.of(NetherRifts.getID("test_blob_portal")), FAST.roster(), FAST.mobs()
						)))
						.then(preset("roster_partial", () -> {
							var roster = new java.util.HashMap<>(nu.metacraft.nether_rifts.raid.Roster.DEFAULT);
							roster.remove(nu.metacraft.nether_rifts.raid.Slot.HEAVY);
							return new NetherRiftsConfig(FAST.tiers(), FAST.timers(), FAST.ranges(), FAST.activeRaidCap(),
									FAST.trooperScalingPerPlayer(), FAST.trooperScalingCap(), FAST.frameCollapse(),
									FAST.scfNetherPortalTypes(), roster, FAST.mobs());
						}))
						.then(preset("drummer_short", () -> {
							var d = NetherRiftsConfig.DrummerTuning.DEFAULT;
							var drummer = new NetherRiftsConfig.DrummerTuning(d.beatTicks(), d.bigBeatEvery(), 6, d.buffTicks(), d.shockRange(), d.shockStrength(),
									d.shockDamage(), d.naturalChance());
							return new NetherRiftsConfig(FAST.tiers(), FAST.timers(), FAST.ranges(), FAST.activeRaidCap(),
									FAST.trooperScalingPerPlayer(), FAST.trooperScalingCap(), FAST.frameCollapse(),
									FAST.scfNetherPortalTypes(), FAST.roster(), new NetherRiftsConfig.Mobs(drummer, FAST.mobs().bombardier()));
						}))
						.then(preset("drummer_natural", () -> naturalChance(1)))
						.then(preset("drummer_never", () -> naturalChance(0)))
						.then(preset("crossbow_never", () -> crossbowEvery(0)))
		));
	}

	/** The defaults, with every natural piglin spawn in the nether bringing a drummer at this chance. */
	private static NetherRiftsConfig naturalChance(double chance) {
		var d = NetherRiftsConfig.DrummerTuning.DEFAULT;
		var c = NetherRiftsConfig.DEFAULT;
		var drummer = new NetherRiftsConfig.DrummerTuning(d.beatTicks(), d.bigBeatEvery(), d.buffRange(), d.buffTicks(), d.shockRange(),
				d.shockStrength(), d.shockDamage(), chance);
		return new NetherRiftsConfig(c.tiers(), c.timers(), c.ranges(), c.activeRaidCap(), c.trooperScalingPerPlayer(), c.trooperScalingCap(),
				c.frameCollapse(), c.scfNetherPortalTypes(), c.roster(), new NetherRiftsConfig.Mobs(drummer, c.mobs().bombardier()));
	}

	/** The defaults, with the Chained Ghast's crossbow piglin shooting this often. */
	private static NetherRiftsConfig crossbowEvery(int every) {
		var b = NetherRiftsConfig.BombardierTuning.DEFAULT;
		var c = NetherRiftsConfig.DEFAULT;
		var bombardier = new NetherRiftsConfig.BombardierTuning(b.diveHeight(), b.climbHeight(), b.runLength(), b.bombRange(), b.bombEvery(),
				b.cooldown(), b.bombPower(), b.warnTicks(), b.burnTicks(), b.playerFireTicks(), b.patchRadius(), b.throwEvery(), b.throwMinRange(),
				b.throwRange(), b.maxFloatHeight(), new NetherRiftsConfig.CrewTuning(every, b.crew().damageTaken(), b.crew().hitboxWidth(),
				b.crew().hitboxHeight()));
		return new NetherRiftsConfig(c.tiers(), c.timers(), c.ranges(), c.activeRaidCap(), c.trooperScalingPerPlayer(), c.trooperScalingCap(),
				c.frameCollapse(), c.scfNetherPortalTypes(), c.roster(), new NetherRiftsConfig.Mobs(c.mobs().drummer(), bombardier));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> preset(String name, Supplier<NetherRiftsConfig> config) {
		return Commands.literal(name).executes(ctx -> {
			writeConfig(config.get());
			return 1;
		});
	}

	@Test
	public void gameTests() throws Exception {
		TestHelper.runTestServer("nether_rifts", "*");
	}
}
