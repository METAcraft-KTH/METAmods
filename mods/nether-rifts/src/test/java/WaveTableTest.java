import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import se.metacraft.nether_rifts.NetherRiftsConfig;
import se.metacraft.nether_rifts.raid.Slot;
import se.metacraft.nether_rifts.raid.WaveTable;
import se.metacraft.nether_rifts.rift.Tier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WaveTableTest {

	/** The config's defaults name blocks. */
	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	static final IntUnaryOperator LOW = bound -> 0;
	static final IntUnaryOperator HIGH = bound -> bound - 1;

	static int raiders(Tier tier, IntUnaryOperator rolls, int players) {
		int total = 0;
		for (int wave = 1; wave <= NetherRiftsConfig.DEFAULT.waveCount(tier); wave++) {
			EnumMap<Slot, Integer> counts = WaveTable.counts(NetherRiftsConfig.DEFAULT, tier, wave, players, rolls, 0.25, 2.0);
			for (var slot : new Slot[] {Slot.RANGED, Slot.MELEE, Slot.HEAVY, Slot.SUPPORT, Slot.ELITE}) {
				total += counts.get(slot);
			}
		}
		return total;
	}

	@Test
	public void baseTotalsMatchVanilla() {
		assertEquals(13, raiders(Tier.SMALL, LOW, 1));
		assertEquals(31, raiders(Tier.MEDIUM, LOW, 1));
		assertEquals(50, raiders(Tier.LARGE, LOW, 1));
	}

	@Test
	public void waveFiveOfAHardRaid() {
		var counts = WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.LARGE, 5, 1, LOW, 0.25, 2.0);
		assertEquals(4, counts.get(Slot.RANGED));
		assertEquals(4, counts.get(Slot.MELEE));
		assertEquals(1, counts.get(Slot.HEAVY));
		assertEquals(0, counts.get(Slot.SUPPORT));
		assertEquals(1, counts.get(Slot.ELITE));
	}

	@Test
	public void bonusRollsFollowDifficulty() {
		// hard: troopers get nextInt(3) more, so up to +2 each
		assertEquals(4 + 2, WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.LARGE, 1, 1, HIGH, 0.25, 2.0).get(Slot.RANGED));
		// easy: k = nextInt(2) (1 when rolling high), then nextInt(k + 1) (1): +1
		assertEquals(4 + 1, WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.SMALL, 1, 1, HIGH, 0.25, 2.0).get(Slot.RANGED));
		// support bonus never in waves 1, 2 or 4, never on easy
		assertEquals(3, WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.MEDIUM, 4, 1, HIGH, 0.25, 2.0).get(Slot.SUPPORT));
		assertEquals(1, WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.MEDIUM, 5, 1, HIGH, 0.25, 2.0).get(Slot.SUPPORT));
		assertEquals(0, WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.SMALL, 3, 1, HIGH, 0.25, 2.0).get(Slot.SUPPORT));
		// heavy and elite never get a bonus (no bonus wave)
		assertEquals(2, WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.LARGE, 7, 1, HIGH, 0.25, 2.0).get(Slot.HEAVY));
		assertEquals(2, WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.LARGE, 7, 1, HIGH, 0.25, 2.0).get(Slot.ELITE));
	}

	@Test
	public void troopersScaleWithPlayersUpToTheCap() {
		// 3 players: × 1.5; 4 ranged → 6
		assertEquals(6, WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.SMALL, 1, 3, LOW, 0.25, 2.0).get(Slot.RANGED));
		// 2 players: × 1.25; 3 ranged → 3.75 → 4
		assertEquals(4, WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.SMALL, 2, 2, LOW, 0.25, 2.0).get(Slot.RANGED));
		// 9 players would be × 3, capped at × 2
		assertEquals(8, WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.SMALL, 1, 9, LOW, 0.25, 2.0).get(Slot.RANGED));
		// up to elite_scaling_from (4) players nothing else scales
		assertEquals(2, WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.LARGE, 7, 4, LOW, 0.25, 2.0).get(Slot.ELITE));
	}

	@Test
	public void troopersScaleAsBeforePastTheThreshold() {
		// × min(2, 1 + 0.25 × (players - 1)), whatever elite_scaling_* says
		int[] ranged = {0, 4, 5, 6, 7, 8, 8, 8, 8, 8, 8, 8, 8};
		for (int players = 1; players <= 12; players++) {
			var counts = WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.SMALL, 1, players, LOW, 0.25, 2.0);
			assertEquals(ranged[players], counts.get(Slot.RANGED), players + " players");
		}
		// large wave 7: 2 ranged, 5 melee; 5 players × 2
		var seven = WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.LARGE, 7, 5, LOW, 0.25, 2.0);
		assertEquals(4, seven.get(Slot.RANGED));
		assertEquals(10, seven.get(Slot.MELEE));
	}

	@Test
	public void heavySupportAndEliteWaitForTheFifthPlayer() {
		for (int players = 1; players <= 4; players++) {
			var counts = WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.LARGE, 7, players, LOW, 0.25, 2.0);
			assertEquals(2, counts.get(Slot.HEAVY), players + " players");
			assertEquals(1, counts.get(Slot.SUPPORT), players + " players");
			assertEquals(2, counts.get(Slot.ELITE), players + " players");
		}
	}

	@Test
	public void heavySupportAndEliteScalePastTheThreshold() {
		// 5 players: × 1.25; 2 → 2.5 → 3, and a 1 stays 1
		var five = WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.LARGE, 7, 5, LOW, 0.25, 2.0);
		assertEquals(3, five.get(Slot.HEAVY));
		assertEquals(3, five.get(Slot.ELITE));
		assertEquals(1, five.get(Slot.SUPPORT));
		assertEquals(1, WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.LARGE, 5, 5, LOW, 0.25, 2.0).get(Slot.HEAVY));
		// 6 players: × 1.5; a 1 becomes 2
		var six = WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.LARGE, 7, 6, LOW, 0.25, 2.0);
		assertEquals(2, six.get(Slot.SUPPORT));
		assertEquals(2, WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.LARGE, 5, 6, LOW, 0.25, 2.0).get(Slot.HEAVY));
		assertEquals(2, WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.LARGE, 5, 6, LOW, 0.25, 2.0).get(Slot.ELITE));
		// 12 players would be × 3, capped at × 2
		var twelve = WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.LARGE, 7, 12, LOW, 0.25, 2.0);
		assertEquals(4, twelve.get(Slot.HEAVY));
		assertEquals(4, twelve.get(Slot.ELITE));
		assertEquals(2, twelve.get(Slot.SUPPORT));
	}

	@Test
	public void supportScalesWithItsBonusRoll() {
		// medium wave 5: no support listed, a bonus roll of 1; 6 players × 1.5 → 2
		assertEquals(1, WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.MEDIUM, 5, 4, HIGH, 0.25, 2.0).get(Slot.SUPPORT));
		assertEquals(2, WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.MEDIUM, 5, 6, HIGH, 0.25, 2.0).get(Slot.SUPPORT));
	}

	@Test
	public void leaderAndCaptainNeverScale() {
		assertEquals(1, WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.LARGE, 7, 12, LOW, 0.25, 2.0).get(Slot.LEADER));
		var config = withCaptain(NetherRiftsConfig.DEFAULT);
		assertEquals(1, WaveTable.counts(config, Tier.LARGE, 7, 12, LOW, 0.25, 2.0).get(Slot.CAPTAIN));
	}

	@Test
	public void eliteScalingFollowsTheConfig() {
		// from 1, × 0.5 a player, capped at × 3: 3 players × 2; 2 heavy → 4
		var c = NetherRiftsConfig.DEFAULT;
		var config = new NetherRiftsConfig(c.tiers(), c.timers(), c.ranges(), c.activeRaidCap(), c.trooperScalingPerPlayer(), c.trooperScalingCap(),
				1, 0.5, 3.0, c.frameCollapse(), c.scfNetherPortalTypes(), c.roster(), c.waves(), c.mobs());
		assertEquals(4, WaveTable.counts(config, Tier.LARGE, 7, 3, LOW, 0.25, 2.0).get(Slot.HEAVY));
		assertEquals(6, WaveTable.counts(config, Tier.LARGE, 7, 20, LOW, 0.25, 2.0).get(Slot.HEAVY));
	}

	@Test
	public void leaderFromWaveTwo() {
		assertEquals(0, WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.SMALL, 1, 1, LOW, 0.25, 2.0).get(Slot.LEADER));
		assertEquals(1, WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.SMALL, 2, 1, LOW, 0.25, 2.0).get(Slot.LEADER));
	}

	@Test
	public void defaultsMatchVanillaTable() {
		int[] ranged = {0, 4, 3, 3, 4, 4, 4, 2};
		int[] melee = {0, 0, 2, 0, 1, 4, 2, 5};
		int[] heavy = {0, 0, 0, 1, 0, 1, 0, 2};
		int[] support = {0, 0, 0, 0, 3, 0, 0, 1};
		int[] elite = {0, 0, 0, 0, 0, 1, 1, 2};
		var waves = NetherRiftsConfig.Waves.DEFAULT;
		assertEquals(3, waves.of(Tier.SMALL).size());
		assertEquals(5, waves.of(Tier.MEDIUM).size());
		assertEquals(7, waves.of(Tier.LARGE).size());
		for (var tier : Tier.values()) {
			for (int wave = 1; wave <= waves.of(tier).size(); wave++) {
				var counts = WaveTable.counts(NetherRiftsConfig.DEFAULT, tier, wave, 1, LOW, 0.25, 2.0);
				var where = tier + " wave " + wave;
				assertEquals(ranged[wave], counts.get(Slot.RANGED), where);
				assertEquals(melee[wave], counts.get(Slot.MELEE), where);
				assertEquals(heavy[wave], counts.get(Slot.HEAVY), where);
				assertEquals(support[wave], counts.get(Slot.SUPPORT), where);
				assertEquals(elite[wave], counts.get(Slot.ELITE), where);
				assertEquals(wave >= 2 ? 1 : 0, counts.get(Slot.LEADER), where);
				assertEquals(0, counts.get(Slot.CAPTAIN), where);
				assertEquals(0, counts.get(Slot.BOMBARDIER), where);
			}
		}
	}

	@Test
	public void generalOffByDefault() {
		assertEquals(0, WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.LARGE, 7, 1, LOW, 0.25, 2.0).get(Slot.CAPTAIN));
	}

	@Test
	public void captainWhenListed() {
		var config = withCaptain(NetherRiftsConfig.DEFAULT);
		assertEquals(1, WaveTable.counts(config, Tier.LARGE, 7, 1, LOW, 0.25, 2.0).get(Slot.CAPTAIN));
		assertEquals(0, WaveTable.counts(config, Tier.LARGE, 6, 1, LOW, 0.25, 2.0).get(Slot.CAPTAIN));
	}

	@Test
	public void pastTheEndIsEmpty() {
		var counts = WaveTable.counts(NetherRiftsConfig.DEFAULT, Tier.SMALL, 4, 1, HIGH, 0.25, 2.0);
		for (var slot : Slot.values()) {
			assertEquals(0, counts.get(slot), slot.toString());
		}
	}

	@Test
	public void shorterList() {
		var two = NetherRiftsConfig.Waves.DEFAULT.small().subList(0, 2);
		var config = withWaves(new NetherRiftsConfig.Waves(two, NetherRiftsConfig.Waves.DEFAULT.medium(), NetherRiftsConfig.Waves.DEFAULT.large()));
		assertEquals(2, config.waveCount(Tier.SMALL));
		assertEquals(0, WaveTable.counts(config, Tier.SMALL, 3, 1, HIGH, 0.25, 2.0).get(Slot.RANGED));
	}

	/** The config with the Piglin General in the last large wave. */
	static NetherRiftsConfig withCaptain(NetherRiftsConfig c) {
		var large = new java.util.ArrayList<>(c.waves().large());
		var last = new HashMap<>(large.getLast());
		last.put(Slot.CAPTAIN, 1);
		large.set(large.size() - 1, last);
		return withWaves(new NetherRiftsConfig.Waves(c.waves().small(), c.waves().medium(), large));
	}

	static NetherRiftsConfig withWaves(NetherRiftsConfig.Waves waves) {
		var c = NetherRiftsConfig.DEFAULT;
		return new NetherRiftsConfig(c.tiers(), c.timers(), c.ranges(), c.activeRaidCap(), c.trooperScalingPerPlayer(), c.trooperScalingCap(),
				c.eliteScalingFrom(), c.eliteScalingPerPlayer(), c.eliteScalingCap(),
				c.frameCollapse(), c.scfNetherPortalTypes(), c.roster(), waves, c.mobs());
	}
}
