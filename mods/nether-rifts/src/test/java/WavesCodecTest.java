import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.metacraft.nether_rifts.NetherRiftsConfig;
import se.metacraft.nether_rifts.raid.Slot;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The {@code waves} config section: each tier optional, a tier's list 1 to 20 waves of real slots. */
public class WavesCodecTest {

	/** The config's defaults name blocks. */
	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	static com.mojang.serialization.DataResult<NetherRiftsConfig.Waves> parse(String json) {
		return NetherRiftsConfig.Waves.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
	}

	@Test
	public void defaultsRoundTrip() {
		var json = NetherRiftsConfig.Waves.CODEC.encodeStart(JsonOps.INSTANCE, NetherRiftsConfig.Waves.DEFAULT).getOrThrow();
		assertEquals(NetherRiftsConfig.Waves.DEFAULT, NetherRiftsConfig.Waves.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
	}

	@Test
	public void noWaveAtAllIsRejected() {
		var result = parse("{\"large\": []}");
		assertTrue(result.isError(), "an empty large list was accepted: " + result);
		assertTrue(result.error().orElseThrow().message().contains("large"), result.error().orElseThrow().message());
	}

	@Test
	public void tooManyWavesAreRejected() {
		var twentyOne = String.join(",", Collections.nCopies(21, "{\"ranged\": 1}"));
		assertTrue(parse("{\"small\": [" + twentyOne + "]}").isError());
		assertTrue(parse("{\"small\": [" + twentyOne.substring(0, twentyOne.lastIndexOf(',')) + "]}").isSuccess());
	}

	@Test
	public void aWaveNamesSlots() {
		var waves = parse("{\"small\": [{\"ranged\": 2}]}").getOrThrow();
		assertEquals(List.of(Map.of(Slot.RANGED, 2)), waves.small());
	}

	@Test
	public void aWaveMayBringTheGeneralsGuard() {
		var waves = parse("{\"small\": [{\"ranged\": 2, \"grenadier\": 1, \"mounted_drummer\": 1}]}").getOrThrow();
		assertEquals(List.of(Map.of(Slot.RANGED, 2, Slot.GRENADIER, 1, Slot.MOUNTED_DRUMMER, 1)), waves.small());
	}

	@Test
	public void unknownSlotsAreRejected() {
		assertTrue(parse("{\"small\": [{\"dragon\": 1}]}").isError());
	}

	@Test
	public void negativeCountsAreRejected() {
		assertTrue(parse("{\"small\": [{\"ranged\": -1}]}").isError());
	}

	@Test
	public void aTierLeftOutKeepsItsDefault() {
		var waves = parse("{\"small\": [{\"ranged\": 2}]}").getOrThrow();
		assertEquals(NetherRiftsConfig.Waves.DEFAULT.medium(), waves.medium());
		assertEquals(NetherRiftsConfig.Waves.DEFAULT.large(), waves.large());
		assertEquals(NetherRiftsConfig.Waves.DEFAULT, parse("{}").getOrThrow());
	}
}
