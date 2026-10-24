package se.metacraft.nether_rifts.rift;

import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * A rift is drawn with as few displays as possible: its blocks merged into rectangles (one panel each), and
 * its outline merged into straight runs (one glowing strip each), so seams between panels never glow.
 */
public final class PanelMerger {

	/** A block of the rift on its plane: u along the rift's axis, v up. */
	public record Cell(int u, int v) {}

	/** Cells u0..u1 × v0..v1, inclusive. */
	public record Rect(int u0, int v0, int u1, int v1) {
		public int width() {
			return u1 - u0 + 1;
		}

		public int height() {
			return v1 - v0 + 1;
		}
	}

	/**
	 * A straight run of outline. Along u it lies on the horizontal grid line v = {@code line}, from u = {@code from}
	 * to u = {@code to}; otherwise on the vertical grid line u = {@code line}, from v = {@code from} to {@code to}.
	 */
	public record Edge(boolean alongU, int line, int from, int to) {}

	private PanelMerger() {}

	public static Set<Cell> cells(RiftShape shape) {
		return shape.blocks().stream()
				.map(p -> new Cell(shape.axis() == Direction.Axis.X ? p.getX() : p.getZ(), p.getY()))
				.collect(Collectors.toSet());
	}

	public static List<Rect> rectangles(Set<Cell> cells) {
		var left = new HashSet<>(cells);
		var out = new ArrayList<Rect>();
		var ordered = cells.stream().sorted(Comparator.comparingInt(Cell::v).thenComparingInt(Cell::u)).toList();
		for (var start : ordered) {
			if (!left.contains(start)) {
				continue;
			}
			int u1 = start.u();
			while (left.contains(new Cell(u1 + 1, start.v()))) {
				u1++;
			}
			int v1 = start.v();
			while (rowFree(left, start.u(), u1, v1 + 1)) {
				v1++;
			}
			for (int u = start.u(); u <= u1; u++) {
				for (int v = start.v(); v <= v1; v++) {
					left.remove(new Cell(u, v));
				}
			}
			out.add(new Rect(start.u(), start.v(), u1, v1));
		}
		return out;
	}

	private static boolean rowFree(Set<Cell> left, int u0, int u1, int v) {
		for (int u = u0; u <= u1; u++) {
			if (!left.contains(new Cell(u, v))) {
				return false;
			}
		}
		return true;
	}

	public static List<Edge> edges(Set<Cell> cells) {
		Map<Integer, TreeSet<Integer>> alongU = new HashMap<>();
		Map<Integer, TreeSet<Integer>> alongV = new HashMap<>();
		for (var c : cells) {
			if (!cells.contains(new Cell(c.u(), c.v() - 1))) {
				alongU.computeIfAbsent(c.v(), k -> new TreeSet<>()).add(c.u());
			}
			if (!cells.contains(new Cell(c.u(), c.v() + 1))) {
				alongU.computeIfAbsent(c.v() + 1, k -> new TreeSet<>()).add(c.u());
			}
			if (!cells.contains(new Cell(c.u() - 1, c.v()))) {
				alongV.computeIfAbsent(c.u(), k -> new TreeSet<>()).add(c.v());
			}
			if (!cells.contains(new Cell(c.u() + 1, c.v()))) {
				alongV.computeIfAbsent(c.u() + 1, k -> new TreeSet<>()).add(c.v());
			}
		}
		var out = new ArrayList<Edge>();
		alongU.forEach((line, starts) -> runs(true, line, starts, out));
		alongV.forEach((line, starts) -> runs(false, line, starts, out));
		return out;
	}

	private static void runs(boolean alongU, int line, TreeSet<Integer> starts, List<Edge> out) {
		Integer from = null;
		int last = 0;
		for (int s : starts) {
			if (from == null) {
				from = s;
			} else if (s != last + 1) {
				out.add(new Edge(alongU, line, from, last + 1));
				from = s;
			}
			last = s;
		}
		if (from != null) {
			out.add(new Edge(alongU, line, from, last + 1));
		}
	}
}
