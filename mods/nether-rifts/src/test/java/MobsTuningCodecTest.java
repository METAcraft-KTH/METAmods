import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import se.metacraft.nether_rifts.NetherRiftsConfig;
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
		assertEquals(new NetherRiftsConfig.BombardierTuning(4, 14, 20, 8, 10, 300, 1.5f, new NetherRiftsConfig.Warning(30, true, 0), 40, 60, 1.5,
				new NetherRiftsConfig.Throwing(80, 4, 12), 20,
				new NetherRiftsConfig.CrewTuning(60, 0.6, 1.0, 2.3), new NetherRiftsConfig.TacticsTuning(20, 6, 2, 20, 60, 40, 200, true, 120)),
				NetherRiftsConfig.BombardierTuning.DEFAULT);
	}

	/** The General's keys sit flat in his section, his swing's, spin's and roar's among them; one set keeps the rest at their defaults. */
	@Test
	public void generalKeys() {
		var general = parse("{\"general\": {\"swing_damage\": 12}}").general();
		var d = NetherRiftsConfig.GeneralTuning.DEFAULT;
		assertEquals(new NetherRiftsConfig.GeneralTuning(d.health(), d.armour(), d.toughness(), d.speed(),
				new NetherRiftsConfig.GeneralTuning.Swing(12, d.swing().reach(), d.swing().arc(), d.swing().startRange(), d.swing().cooldown()),
				d.spin(), d.dizzyTicks(), d.dizzyDamageTaken(), d.roar()), general);
		var json = NetherRiftsConfig.Mobs.CODEC.encodeStart(JsonOps.INSTANCE, NetherRiftsConfig.Mobs.DEFAULT).getOrThrow()
				.getAsJsonObject().getAsJsonObject("general");
		for (var key : java.util.List.of("health", "swing_damage", "spin_radius", "dizzy_ticks", "roar_at", "guard_drummers")) {
			org.junit.jupiter.api.Assertions.assertTrue(json.has(key), "no " + key + " in " + json);
		}
	}

	@Test
	public void generalDefaultsAreTheSpecNumbers() {
		assertEquals(new NetherRiftsConfig.GeneralTuning(300, 16, 6, 0.3,
				new NetherRiftsConfig.GeneralTuning.Swing(10, 4.5, 120, 4, 20),
				new NetherRiftsConfig.GeneralTuning.Spin(6, 3.5, 60, 100, 240, 120, 60, 10, 0.33),
				60, 1.5,
				new NetherRiftsConfig.GeneralTuning.Roar(java.util.List.of(0.6667, 0.3333), 0.5, 1, 1)),
				NetherRiftsConfig.GeneralTuning.DEFAULT);
	}

	/** A spin can't be set shorter at most than at least. */
	@Test
	public void spinTicksInOrder() {
		org.junit.jupiter.api.Assertions.assertTrue(NetherRiftsConfig.Mobs.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString("{\"general\": {\"spin_min_ticks\": 80, \"spin_max_ticks\": 40}}")).isError());
	}

	/** The throw keys still sit flat in the bombardier section; the tactics are a section of their own. */
	@Test
	public void throwAndTacticsKeys() {
		var bombardier = parse("{\"bombardier\": {\"throw_every\": 40, \"tactics\": {\"volley_size\": 3}}}").bombardier();
		assertEquals(40, bombardier.throwEvery());
		assertEquals(NetherRiftsConfig.Throwing.DEFAULT.range(), bombardier.throwRange());
		assertEquals(3, bombardier.tactics().volleySize());
		assertEquals(NetherRiftsConfig.TacticsTuning.DEFAULT.globalCooldown(), bombardier.tactics().globalCooldown());
	}
}
