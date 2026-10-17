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

	@Test
	public void defaultsAreTheSpecNumbers() {
		var drummer = NetherRiftsConfig.DrummerTuning.DEFAULT;
		assertEquals(new NetherRiftsConfig.DrummerTuning(10, 4, 12, 120, 4, 1.2, 4f, 0.03), drummer);
		assertEquals(new NetherRiftsConfig.BombardierTuning(4, 14, 20, 8, 6, 100, 1.5f, 20, 40, 60, 1.5, 80, 4, 12, 20,
				new NetherRiftsConfig.CrewTuning(60, 0.6, 1.0, 2.3)),
				NetherRiftsConfig.BombardierTuning.DEFAULT);
	}
}
