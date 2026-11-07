package se.metacraft.fake_players;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A recorded stretch of player movement, read from a CSV file with the columns
 * {@code session,t,x,y,z[,dim]}: one row per position sample (about every 5 seconds), {@code t} in seconds from the
 * start of the scenario, {@code dim} an optional dimension id (default {@code minecraft:overworld}).
 * Each distinct {@code session} value becomes one fake player that joins at its first sample and leaves after its
 * last. Lines starting with {@code #} and blank lines are ignored.
 */
public final class Scenario {
	private static final Pattern DIMENSION = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
	public static final String DEFAULT_DIMENSION = "minecraft:overworld";

	public final String name;
	public final List<Session> sessions;
	/** Length of the scenario in seconds. */
	public final int length;

	private Scenario(String name, List<Session> sessions) {
		this.name = name;
		this.sessions = sessions;
		this.length = sessions.stream().mapToInt(Session::endTime).max().orElse(0);
	}

	public static Scenario parse(String name, BufferedReader reader) throws IOException {
		int session = -1;
		int t = -1;
		int x = -1;
		int y = -1;
		int z = -1;
		int dim = -1;
		var sessions = new LinkedHashMap<Integer, List<Point>>();
		String line;
		int lineNumber = 0;
		while ((line = reader.readLine()) != null) {
			lineNumber++;
			line = line.trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			var cells = line.split(",", -1);
			if (session < 0) {
				var header = new ArrayList<String>();
				for (var cell : cells) {
					header.add(cell.trim().toLowerCase(java.util.Locale.ROOT));
				}
				session = header.indexOf("session");
				t = header.indexOf("t");
				x = header.indexOf("x");
				y = header.indexOf("y");
				z = header.indexOf("z");
				dim = header.indexOf("dim");
				if (session < 0 || t < 0 || x < 0 || y < 0 || z < 0) {
					throw new IOException(name + ".csv: the header must contain session,t,x,y,z (and optionally dim)");
				}
				continue;
			}
			try {
				var dimension = dim >= 0 && dim < cells.length && !cells[dim].isBlank()
						? cells[dim].trim() : DEFAULT_DIMENSION;
				if (!DIMENSION.matcher(dimension).matches()) {
					throw new IllegalArgumentException("bad dimension '" + dimension + "'");
				}
				sessions.computeIfAbsent(Integer.parseInt(cells[session].trim()), k -> new ArrayList<>()).add(
						new Point(
								Integer.parseInt(cells[t].trim()), Integer.parseInt(cells[x].trim()),
								Integer.parseInt(cells[y].trim()), Integer.parseInt(cells[z].trim()), dimension
						)
				);
			} catch (RuntimeException e) {
				throw new IOException(name + ".csv line " + lineNumber + ": " + e.getMessage(), e);
			}
		}
		if (sessions.isEmpty()) {
			throw new IOException(name + ".csv has no samples");
		}
		var result = new ArrayList<Session>();
		sessions.forEach((id, points) -> {
			points.sort(Comparator.comparingInt(Point::t));
			result.add(new Session(id, List.copyOf(points)));
		});
		return new Scenario(name, List.copyOf(result));
	}

	public record Point(int t, int x, int y, int z, String dimension) {
	}

	/** One continuous online session of one fake player. */
	public record Session(int id, List<Point> points) {
		public int startTime() {
			return points.getFirst().t();
		}

		public int endTime() {
			return points.getLast().t();
		}

		/** Index of the latest sample at or before {@code time}, or -1 if the session has not started. */
		public int indexAt(int time) {
			int i = -1;
			while (i + 1 < points.size() && points.get(i + 1).t() <= time) {
				i++;
			}
			return i;
		}

		/** Yaw (degrees) facing from sample {@code index - 1} to {@code index}, or 0 for the first/standing still. */
		public float yaw(int index) {
			if (index <= 0) {
				return 0;
			}
			var from = points.get(index - 1);
			var to = points.get(index);
			double dx = to.x() - from.x();
			double dz = to.z() - from.z();
			if (!from.dimension().equals(to.dimension()) || dx * dx + dz * dz < 1) {
				return 0;
			}
			return (float) Math.toDegrees(Math.atan2(-dx, dz));
		}
	}
}
