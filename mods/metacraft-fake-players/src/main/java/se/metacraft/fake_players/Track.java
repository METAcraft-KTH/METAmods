package se.metacraft.fake_players;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A recorded snippet of real player movement, embedded in the jar as {@code data/metacraft-fake-players/track.txt}.
 * Each {@link Segment} is one continuous online session of one (anonymised) player.
 * Positions are sampled every ~5 seconds and interpolated by {@link Segment#sample}.
 */
public final class Track {
	public static final String RESOURCE = "/data/metacraft-fake-players/track.txt";
	/** Jumps larger than this between two samples are teleports and are not interpolated. */
	private static final double TELEPORT_DISTANCE = 64;

	private static Track instance;

	public final List<Segment> segments;
	/** Length of the snippet in seconds. */
	public final double length;

	private Track(List<Segment> segments) {
		this.segments = segments;
		double length = 0;
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
			Segment.Builder current = null;
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank() || line.startsWith("#")) {
					continue;
				}
				var parts = line.trim().split(" ");
				if (parts[0].equals("S")) {
					if (current != null) {
						segments.add(current.build());
					}
					current = new Segment.Builder(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
				} else if (current != null) {
					current.add(
							Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
							Integer.parseInt(parts[2]), Integer.parseInt(parts[3])
					);
				}
			}
			if (current != null) {
				segments.add(current.build());
			}
		}
		return new Track(List.copyOf(segments));
	}

	public static final class Segment {
		public final int id;
		private final double[] t;
		private final double[] x;
		private final double[] y;
		private final double[] z;

		private Segment(int id, double[] t, double[] x, double[] y, double[] z) {
			this.id = id;
			this.t = t;
			this.x = x;
			this.y = y;
			this.z = z;
		}

		public double startTime() {
			return t[0];
		}

		public double endTime() {
			return t[t.length - 1];
		}

		/** Writes {x, y, z, yawDegrees} for time {@code time} (seconds since the snippet start) into {@code out}. */
		public void sample(double time, double[] out) {
			int i = 0;
			// Segments are tiny (<= 180 samples) so a linear scan per tick is fine.
			while (i < t.length - 2 && t[i + 1] <= time) {
				i++;
			}
			int j = Math.min(i + 1, t.length - 1);
			double dx = x[j] - x[i];
			double dz = z[j] - z[i];
			double f = 0;
			if (j != i && Math.sqrt(dx * dx + dz * dz) <= TELEPORT_DISTANCE) {
				f = Math.clamp((time - t[i]) / (t[j] - t[i]), 0.0, 1.0);
			} else if (j != i && time >= t[j]) {
				f = 1;
			}
			out[0] = x[i] + dx * f + 0.5;
			out[1] = y[i] + (y[j] - y[i]) * f;
			out[2] = z[i] + dz * f + 0.5;
			if (dx * dx + dz * dz > 0.25 && Math.sqrt(dx * dx + dz * dz) <= TELEPORT_DISTANCE) {
				out[3] = Math.toDegrees(Math.atan2(-dx, dz));
			}
		}

		private static final class Builder {
			private final int id;
			private final int start;
			private final List<double[]> points = new ArrayList<>();

			private Builder(int id, int start) {
				this.id = id;
				this.start = start;
			}

			/** Every line but the first is a delta from the previous point; the first is absolute with dt 0. */
			private void add(int x, int y, int z, int dt) {
				if (points.isEmpty()) {
					points.add(new double[]{start, x, y, z});
				} else {
					var p = points.getLast();
					points.add(new double[]{p[0] + dt, p[1] + x, p[2] + y, p[3] + z});
				}
			}

			private Segment build() {
				int n = points.size();
				var t = new double[n];
				var x = new double[n];
				var y = new double[n];
				var z = new double[n];
				for (int i = 0; i < n; i++) {
					var p = points.get(i);
					t[i] = p[0];
					x[i] = p[1];
					y[i] = p[2];
					z[i] = p[3];
				}
				return new Segment(id, t, x, y, z);
			}
		}
	}
}
