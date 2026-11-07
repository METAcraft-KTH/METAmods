package se.metacraft.fake_players;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A recorded snippet of real player movement, embedded in the jar as {@code data/metacraft-fake-players/track.txt}.
 * Each {@link Segment} is one continuous online session of one (anonymised) player, sampled every ~5 seconds.
 */
public final class Track {
	public static final String RESOURCE = "/data/metacraft-fake-players/track.txt";

	private static Track instance;

	public final List<Segment> segments;
	/** Length of the snippet in seconds. */
	public final int length;

	private Track(List<Segment> segments) {
		this.segments = segments;
		int length = 0;
		for (var s : segments) {
			length = Math.max(length, s.endTime());
		}
		this.length = length;
	}

	public static synchronized Track get() {
		if (instance == null) {
			try {
				instance = load();
			} catch (IOException e) {
				throw new IllegalStateException("Could not read " + RESOURCE, e);
			}
		}
		return instance;
	}

	private static Track load() throws IOException {
		var in = Track.class.getResourceAsStream(RESOURCE);
		if (in == null) {
			throw new IOException("Missing resource");
		}
		var segments = new ArrayList<Segment>();
		try (var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			Segment current = null;
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank() || line.startsWith("#")) {
					continue;
				}
				var parts = line.trim().split(" ");
				if (parts[0].equals("S")) {
					current = new Segment(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
					segments.add(current);
				} else if (current != null) {
					current.add(
							Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
							Integer.parseInt(parts[2]), Integer.parseInt(parts[3])
					);
				}
			}
		}
		return new Track(List.copyOf(segments));
	}

	/** One online session: the sample points {t, x, y, z}, with t in seconds into the snippet. */
	public static final class Segment {
		public final int id;
		private final int start;
		private final List<int[]> points = new ArrayList<>();

		private Segment(int id, int start) {
			this.id = id;
			this.start = start;
		}

		/** The first line is absolute (dt 0), every later line is a delta from the previous point. */
		private void add(int x, int y, int z, int dt) {
			if (points.isEmpty()) {
				points.add(new int[]{start, x, y, z});
			} else {
				var p = points.getLast();
				points.add(new int[]{p[0] + dt, p[1] + x, p[2] + y, p[3] + z});
			}
		}

		public int endTime() {
			return points.getLast()[0];
		}

		public int x(int index) {
			return points.get(index)[1];
		}

		public int y(int index) {
			return points.get(index)[2];
		}

		public int z(int index) {
			return points.get(index)[3];
		}

		/** Index of the latest sample at or before {@code time}, or -1 if the session has not started. */
		public int indexAt(int time) {
			int i = -1;
			while (i + 1 < points.size() && points.get(i + 1)[0] <= time) {
				i++;
			}
			return i;
		}

		/** Yaw (degrees) facing from sample {@code index - 1} to {@code index}, or 0 for the first/standing still. */
		public float yaw(int index) {
			if (index <= 0) {
				return 0;
			}
			double dx = x(index) - x(index - 1);
			double dz = z(index) - z(index - 1);
			if (dx * dx + dz * dz < 1) {
				return 0;
			}
			return (float) Math.toDegrees(Math.atan2(-dx, dz));
		}
	}
}
