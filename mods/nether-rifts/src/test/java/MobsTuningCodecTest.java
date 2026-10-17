import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import nu.metacraft.nether_rifts.NetherRiftsConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The {@code mobs} config section: optional throughout, every value left out keeps its default. */
public class MobsTuningCodecTest {

	/** The config's defaults name blocks. */
	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	static NetherRiftsConfig.Mobs parse(String json) {
		return NetherRiftsConfig.Mobs.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
	}

	@Test
	public void defaultsRoundTrip() {
		var json = NetherRiftsConfig.Mobs.CODEC.encodeStart(JsonOps.INSTANCE, NetherRiftsConfig.Mobs.DEFAULT).getOrThrow();
		assertEquals(NetherRiftsConfig.Mobs.DEFAULT, NetherRiftsConfig.Mobs.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
	}

	@Test
	public void emptySectionIsTheDefaults() {
		assertEquals(NetherRiftsConfig.Mobs.DEFAULT, parse("{}"));
	}

	@Test
	public void oneValueChangesOnlyItself() {
		var mobs = parse("{\"drummer\": {\"buff_range\": 6}, \"bombardier\": {\"bomb_power\": 2.5}}");
		var drummer = NetherRiftsConfig.DrummerTuning.DEFAULT;
		assertEquals(new NetherRiftsConfig.DrummerTuning(drummer.beatTicks(), drummer.bigBeatEvery(), 6, drummer.buffTicks(),
				drummer.shockRange(), drummer.shockStrength(), drummer.shockDamage(), drummer.naturalChance()), mobs.drummer());
		assertEquals(2.5f, mobs.bombardier().bombPower());
		assertEquals(NetherRiftsConfig.BombardierTuning.DEFAULT.diveHeight(), mobs.bombardier().diveHeight());
		assertEquals(NetherRiftsConfig.BombardierTuning.DEFAULT.throwRange(), mobs.bombardier().throwRange());
	}

	/** The warning's keys sit flat in the bombardier section; its radius is the blast's reach unless set. */
	@Test
	public void warningKeys() {
		var bombardier = parse("{\"bombardier\": {\"warn_ticks\": 25, \"warn_shadow\": false, \"bomb_power\": 2}}").bombardier();
		assertEquals(new NetherRiftsConfig.Warning(25, false, 0), bombardier.warning());
		assertEquals(25, bombardier.warnTicks());
		assertEquals(4.0, bombardier.warnRadius());
		assertEquals(3.0, NetherRiftsConfig.BombardierTuning.DEFAULT.warnRadius());
		assertEquals(5.0, parse("{\"bombardier\": {\"warn_radius\": 5}}").bombardier().warnRadius());
		var json = NetherRiftsConfig.Mobs.CODEC.encodeStart(JsonOps.INSTANCE, NetherRiftsConfig.Mobs.DEFAULT).getOrThrow()
				.getAsJsonObject().getAsJsonObject("bombardier");
		for (var key : java.util.List.of("warn_ticks", "warn_shadow", "warn_radius")) {
			org.junit.jupiter.api.Assertions.assertTrue(json.has(key), "no " + key + " in " + json);
		}
	}

	@Test
	public void defaultsAreTheSpecNumbers() {
		var drummer = NetherRiftsConfig.DrummerTuning.DEFAULT;
		assertEquals(new NetherRiftsConfig.DrummerTuning(10, 4, 12, 120, 4, 1.2, 4f, 0.03), drummer);
		assertEquals(new NetherRiftsConfig.BombardierTuning(4, 14, 20, 8, 6, 100, 1.5f, new NetherRiftsConfig.Warning(30, true, 0), 40, 60, 1.5, 80, 4, 12, 20,
				new NetherRiftsConfig.CrewTuning(60, 0.6, 1.0, 2.3)),
				NetherRiftsConfig.BombardierTuning.DEFAULT);
	}
}
