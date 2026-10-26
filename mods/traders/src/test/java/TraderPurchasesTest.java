import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import se.metacraft.traders.TraderKind;
import se.metacraft.traders.TraderPurchases;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The saved purchase counts behind {@code max_uses}: per player, per trader, per trade. */
public class TraderPurchasesTest {

	private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
	private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000b0b");

	@Test
	public void countsEachPlayerTraderAndTradeApart() {
		var purchases = new TraderPurchases();
		purchases.record(ALICE, TraderKind.DATA, "ovvar:data_ovve");
		purchases.record(ALICE, TraderKind.DATA, "ovvar:data_ovve");
		purchases.record(BOB, TraderKind.DATA, "ovvar:data_ovve");
		assertEquals(2, purchases.count(ALICE, TraderKind.DATA, "ovvar:data_ovve"));
		assertEquals(1, purchases.count(BOB, TraderKind.DATA, "ovvar:data_ovve"));
		assertEquals(0, purchases.count(ALICE, TraderKind.IT, "ovvar:data_ovve"));
		assertEquals(0, purchases.count(ALICE, TraderKind.DATA, "ovvar:it_ovve"));
	}

	@Test
	public void recordingMarksTheDataForSaving() {
		var purchases = new TraderPurchases();
		assertEquals(false, purchases.isDirty());
		purchases.record(ALICE, TraderKind.MEDIA, "ovvar:media_frack");
		assertEquals(true, purchases.isDirty());
	}

	/** What a restart reads back is what was saved, and it can still be added to. */
	@Test
	public void countsSurviveSavingAndLoading() {
		var purchases = new TraderPurchases();
		purchases.record(ALICE, TraderKind.IT, "ovvar:it_ovve");
		purchases.record(BOB, TraderKind.MEDIA, "summer");
		var json = TraderPurchases.CODEC.encodeStart(JsonOps.INSTANCE, purchases).getOrThrow();
		var loaded = TraderPurchases.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(1, loaded.count(ALICE, TraderKind.IT, "ovvar:it_ovve"));
		assertEquals(1, loaded.count(BOB, TraderKind.MEDIA, "summer"));
		loaded.record(ALICE, TraderKind.IT, "ovvar:it_ovve");
		assertEquals(2, loaded.count(ALICE, TraderKind.IT, "ovvar:it_ovve"));
	}
}
