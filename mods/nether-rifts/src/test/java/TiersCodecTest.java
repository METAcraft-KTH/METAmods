import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import se.metacraft.nether_rifts.NetherRiftsConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TiersCodecTest {

	static com.mojang.serialization.DataResult<NetherRiftsConfig.Tiers> parse(String json) {
		return NetherRiftsConfig.Tiers.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
	}

	@Test
	public void defaultsRoundTrip() {
		var json = NetherRiftsConfig.Tiers.CODEC.encodeStart(JsonOps.INSTANCE, NetherRiftsConfig.Tiers.DEFAULT).getOrThrow();
		assertEquals(NetherRiftsConfig.Tiers.DEFAULT, NetherRiftsConfig.Tiers.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
	}

	@Test
	public void equalMaximaAreAccepted() {
		assertEquals(new NetherRiftsConfig.Tiers(30, 30), parse("{\"small_max\": 30, \"medium_max\": 30}").getOrThrow());
	}

	@Test
	public void smallAboveMediumIsRejected() {
		// medium could never be reached: every rift past small_max would already be past medium_max
		var result = parse("{\"small_max\": 60, \"medium_max\": 20}");
		assertTrue(result.isError(), "small_max 60 > medium_max 20 was accepted: " + result);
		assertTrue(result.error().orElseThrow().message().contains("small_max"), result.error().orElseThrow().message());
	}
}
