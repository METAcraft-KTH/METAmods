import se.metacraft.nether_rifts.raid.Slot;
import se.metacraft.nether_rifts.raid.WaveTable;
import se.metacraft.nether_rifts.rift.Tier;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WaveTableTest {

	static final IntUnaryOperator LOW = bound -> 0;
	static final IntUnaryOperator HIGH = bound -> bound - 1;

	static int raiders(Tier tier, IntUnaryOperator rolls, int players) {
		int total = 0;
		for (int wave = 1; wave <= tier.waves(); wave++) {
			EnumMap<Slot, Integer> counts = WaveTable.counts(tier, wave, players, rolls, 0.25, 2.0);
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
		var counts = WaveTable.counts(Tier.LARGE, 5, 1, LOW, 0.25, 2.0);
		assertEquals(4, counts.get(Slot.RANGED));
		assertEquals(4, counts.get(Slot.MELEE));
		assertEquals(1, counts.get(Slot.HEAVY));
		assertEquals(0, counts.get(Slot.SUPPORT));
		assertEquals(1, counts.get(Slot.ELITE));
	}

	@Test
	public void bonusRollsFollowDifficulty() {
		// hard: troopers get nextInt(3) more, so up to +2 each
		assertEquals(4 + 2, WaveTable.counts(Tier.LARGE, 1, 1, HIGH, 0.25, 2.0).get(Slot.RANGED));
		// easy: k = nextInt(2) (1 when rolling high), then nextInt(k + 1) (1): +1
		assertEquals(4 + 1, WaveTable.counts(Tier.SMALL, 1, 1, HIGH, 0.25, 2.0).get(Slot.RANGED));
		// support bonus never in waves 1, 2 or 4, never on easy
		assertEquals(3, WaveTable.counts(Tier.MEDIUM, 4, 1, HIGH, 0.25, 2.0).get(Slot.SUPPORT));
		assertEquals(1, WaveTable.counts(Tier.MEDIUM, 5, 1, HIGH, 0.25, 2.0).get(Slot.SUPPORT));
		assertEquals(0, WaveTable.counts(Tier.SMALL, 3, 1, HIGH, 0.25, 2.0).get(Slot.SUPPORT));
		// heavy and elite never get a bonus (no bonus wave)
		assertEquals(2, WaveTable.counts(Tier.LARGE, 7, 1, HIGH, 0.25, 2.0).get(Slot.HEAVY));
		assertEquals(2, WaveTable.counts(Tier.LARGE, 7, 1, HIGH, 0.25, 2.0).get(Slot.ELITE));
	}

	@Test
	public void troopersScaleWithPlayersUpToTheCap() {
		// 3 players: × 1.5; 4 ranged → 6
		assertEquals(6, WaveTable.counts(Tier.SMALL, 1, 3, LOW, 0.25, 2.0).get(Slot.RANGED));
		// 2 players: × 1.25; 3 ranged → 3.75 → 4
		assertEquals(4, WaveTable.counts(Tier.SMALL, 2, 2, LOW, 0.25, 2.0).get(Slot.RANGED));
		// 9 players would be × 3, capped at × 2
		assertEquals(8, WaveTable.counts(Tier.SMALL, 1, 9, LOW, 0.25, 2.0).get(Slot.RANGED));
		// nothing else scales
		assertEquals(2, WaveTable.counts(Tier.LARGE, 7, 9, LOW, 0.25, 2.0).get(Slot.ELITE));
	}

	@Test
	public void leaderFromWaveTwoCaptainOnlyLastLargeWave() {
		assertEquals(0, WaveTable.counts(Tier.SMALL, 1, 1, LOW, 0.25, 2.0).get(Slot.LEADER));
		assertEquals(1, WaveTable.counts(Tier.SMALL, 2, 1, LOW, 0.25, 2.0).get(Slot.LEADER));
		assertEquals(0, WaveTable.counts(Tier.MEDIUM, 5, 1, LOW, 0.25, 2.0).get(Slot.CAPTAIN));
		assertEquals(0, WaveTable.counts(Tier.LARGE, 6, 1, LOW, 0.25, 2.0).get(Slot.CAPTAIN));
		assertEquals(1, WaveTable.counts(Tier.LARGE, 7, 1, LOW, 0.25, 2.0).get(Slot.CAPTAIN));
	}
}
