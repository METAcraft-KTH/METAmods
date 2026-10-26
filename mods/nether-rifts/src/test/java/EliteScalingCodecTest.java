import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.metacraft.nether_rifts.NetherRiftsConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The top-level {@code elite_scaling_*} keys: optional, each with its default. */
public class EliteScalingCodecTest {

	/** The config's defaults name blocks. */
	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	static com.mojang.serialization.DataResult<NetherRiftsConfig> parse(String json) {
		return NetherRiftsConfig.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json));
	}

	@Test
	public void leftOutKeepsTheDefaults() {
		var config = parse("{}").getOrThrow();
		assertEquals(4, config.eliteScalingFrom());
		assertEquals(0.25, config.eliteScalingPerPlayer());
		assertEquals(2.0, config.eliteScalingCap());
	}

	@Test
	public void roundTrips() {
		var config = parse("{\"elite_scaling_from\": 2, \"elite_scaling_per_player\": 0.5, \"elite_scaling_cap\": 3.0}").getOrThrow();
		assertEquals(2, config.eliteScalingFrom());
		assertEquals(0.5, config.eliteScalingPerPlayer());
		assertEquals(3.0, config.eliteScalingCap());
		var json = (JsonObject) NetherRiftsConfig.CODEC.codec().encodeStart(JsonOps.INSTANCE, config).getOrThrow();
		assertEquals(2, json.get("elite_scaling_from").getAsInt());
		assertEquals(0.5, json.get("elite_scaling_per_player").getAsDouble());
		assertEquals(3.0, json.get("elite_scaling_cap").getAsDouble());
		var again = parse(json.toString()).getOrThrow();
		assertEquals(2, again.eliteScalingFrom());
		assertEquals(0.5, again.eliteScalingPerPlayer());
		assertEquals(3.0, again.eliteScalingCap());
	}

	@Test
	public void outOfRangeIsRejected() {
		assertTrue(parse("{\"elite_scaling_cap\": 0.5}").isError());
		assertTrue(parse("{\"elite_scaling_from\": -1}").isError());
	}
}
