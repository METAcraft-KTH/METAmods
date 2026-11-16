import org.junit.jupiter.api.Test;
import se.metacraft.lib.util.helper.TestHelper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What {@link TestHelper#runTestServer} says when game tests fail: which ones, and why. */
public class TestGameTestFailureMessage {

	@Test
	public void namesEachFailedTestWithItsMessage() {
		var message = TestHelper.describeFailure(true, 174, List.of(
				new TestHelper.FailedTest("nether_rifts:ghast_flight/deck_drummer_war_beat", "a ghast without its drummer gave War Beat on tick 110"),
				new TestHelper.FailedTest("nether_rifts:raid/starts", "Didn't succeed or fail within 100 ticks")
		));
		assertEquals(
				"2 of 174 required game tests failed:"
						+ "\n  - nether_rifts:ghast_flight/deck_drummer_war_beat: a ghast without its drummer gave War Beat on tick 110"
						+ "\n  - nether_rifts:raid/starts: Didn't succeed or fail within 100 ticks",
				message
		);
	}

	@Test
	public void saysWhenTheServerStoppedBeforeTheTestsFinished() {
		var message = TestHelper.describeFailure(false, 0, List.of());
		assertTrue(message.contains("stopped before its tests finished"), message);
	}

	@Test
	public void aFailedTestWithoutAMessageIsStillNamed() {
		var message = TestHelper.describeFailure(true, 3, List.of(new TestHelper.FailedTest("metacraft:lib/x", null)));
		assertEquals("1 of 3 required game tests failed:\n  - metacraft:lib/x: (no message)", message);
	}
}
