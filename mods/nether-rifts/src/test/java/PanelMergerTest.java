import se.metacraft.nether_rifts.rift.PanelMerger;
import se.metacraft.nether_rifts.rift.PanelMerger.Cell;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PanelMergerTest {

	static Set<Cell> rect(int w, int h) {
		var cells = new HashSet<Cell>();
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < h; v++) {
				cells.add(new Cell(u, v));
			}
		}
		return cells;
	}

	@Test
	public void aRectangleIsOnePanelAndFourEdges() {
		assertEquals(1, PanelMerger.rectangles(rect(2, 3)).size());
		assertEquals(4, PanelMerger.edges(rect(2, 3)).size());
		assertEquals(1, PanelMerger.rectangles(rect(21, 21)).size());
	}

	@Test
	public void anLShapeHasSixEdges() {
		var cells = rect(4, 4);
		cells.removeIf(c -> c.u() >= 2 && c.v() >= 2);
		assertEquals(6, PanelMerger.edges(cells).size());
		assertCovers(cells);
	}

	@Test
	public void randomBlobsAreCoveredExactlyOnce() {
		var random = new Random(7);
		for (int run = 0; run < 200; run++) {
			var cells = new HashSet<Cell>();
			for (int i = 0; i < 60; i++) {
				cells.add(new Cell(random.nextInt(12), random.nextInt(12)));
			}
			assertCovers(cells);
		}
	}

	/** Every cell in exactly one rectangle, and no rectangle reaches outside the shape. */
	static void assertCovers(Set<Cell> cells) {
		var seen = new HashSet<Cell>();
		for (var r : PanelMerger.rectangles(cells)) {
			for (int u = r.u0(); u <= r.u1(); u++) {
				for (int v = r.v0(); v <= r.v1(); v++) {
					var c = new Cell(u, v);
					assertTrue(cells.contains(c), "rectangle " + r + " covers " + c + ", outside the shape");
					assertTrue(seen.add(c), "two rectangles cover " + c);
				}
			}
		}
		assertEquals(cells, seen);
	}
}
