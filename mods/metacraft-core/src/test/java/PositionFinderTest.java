import org.junit.jupiter.api.Test;
import nu.metacraft.lib.util.PositionFinder;

import java.util.HashSet;
import java.util.Set;

public class PositionFinderTest {

	@Test
	public void makeSureEveryPositionIsUnique() {
		Set<PositionFinder.XZ> checker = new HashSet<>();
		for (int i = 0; i < Short.MAX_VALUE * 100; i++) {
			var pos = PositionFinder.findPosAroundOrigin(i, 9);
			assert !checker.contains(pos);
			checker.add(pos);
		}
		assert new PositionFinder.XZ(5, 5).hashCode() == new PositionFinder.XZ(5, 5).hashCode();
		assert checker.contains(PositionFinder.findPosAroundOrigin(7, 9));
	}

}
