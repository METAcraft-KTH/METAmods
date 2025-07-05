import it.unimi.dsi.fastutil.doubles.DoubleList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.core.util.InterpolationSet;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.util.helper.TestHelper;

public class YawFixTest {

	@BeforeAll
	public static void init() {
		TestHelper.init(
				METAcraftLib::new,
				METAcraftCore::new
		);
	}

	private static void check(DoubleList list, DoubleList expected) {
		var result = InterpolationSet.fixYawRotations(list);
		if (!result.equals(expected)) {
			throw new AssertionError("Expected " + expected + ", got " + result);
		}
	}

	@Test
	public void test() {
		check(
				DoubleList.of(50, -50, 50, 170, -170, 170, 180, -50),
				DoubleList.of(50, -50, 50, 170, 190, 170, 180, 310)
		);



		check(
				DoubleList.of(179, -179, -150, -50, 50, 150, -150, -50, 50, -150),
				DoubleList.of(179, 181, 210, 310, 410, 510, 570, 670, 770, 930)
		);

		check(
				DoubleList.of(179, -179, -150, -50, 50, -50, -150, 150, 50),
				DoubleList.of(179, 181, 210, 310, 410, 310, 210, 150, 50)
		);

	}


}
