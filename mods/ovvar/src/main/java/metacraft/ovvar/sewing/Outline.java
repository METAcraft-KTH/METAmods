package metacraft.ovvar.sewing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import metacraft.ovvar.Ovvar;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The edge of a patch's art, as datagen traced it: the boundary between its opaque texels and
 * everything else, one segment per texel side, chained clockwise from the top-left corner, each
 * with its outward normal. In texel units of the art. Datagen writes every patch's to
 * {@value #RESOURCE} ({@code [x0, y0, x1, y1, nx, ny]} per segment) and this reads it back.
 */
public record Outline(List<Segment> segments) {
    public static final String RESOURCE = "/" + Ovvar.MOD_ID + "/outlines.json";

    /** A unit-length edge from (x0, y0) to (x1, y1) with the outside on the (nx, ny) side. */
    public record Segment(int x0, int y0, int x1, int y1, int nx, int ny) {}

    /** A point on the outline with the outward normal there. */
    public record Point(double x, double y, int nx, int ny) {}

    /** The outline's length: one per segment. */
    public double length() {
        return segments.size();
    }

    /** The point {@code distance} along the outline from its start. */
    public Point at(double distance) {
        int index = Math.floorMod((int) Math.floor(distance), segments.size());
        double f = distance - Math.floor(distance);
        Segment s = segments.get(index);
        return new Point(s.x0 + (s.x1 - s.x0) * f, s.y0 + (s.y1 - s.y0) * f, s.nx, s.ny);
    }

    private static Map<String, Outline> all;

    public static Outline of(String patchId) {
        if (all == null) all = load();
        Outline outline = all.get(patchId);
        if (outline == null) throw new IllegalStateException("[" + Ovvar.MOD_ID + "] no outline for patch '" + patchId + "' in " + RESOURCE + "; run datagen");
        return outline;
    }

    private static Map<String, Outline> load() {
        InputStream in = Outline.class.getResourceAsStream(RESOURCE);
        if (in == null) throw new IllegalStateException("[" + Ovvar.MOD_ID + "] " + RESOURCE + " missing: run './gradlew runDatagen'");
        Map<String, Outline> out = new HashMap<>();
        try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            for (var entry : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
                List<Segment> segments = new ArrayList<>();
                for (JsonElement e : entry.getValue().getAsJsonArray()) {
                    JsonArray a = e.getAsJsonArray();
                    segments.add(new Segment(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(), a.get(3).getAsInt(), a.get(4).getAsInt(), a.get(5).getAsInt()));
                }
                if (segments.isEmpty()) throw new IllegalStateException("[" + Ovvar.MOD_ID + "] empty outline for " + entry.getKey());
                out.put(entry.getKey(), new Outline(List.copyOf(segments)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }
}
