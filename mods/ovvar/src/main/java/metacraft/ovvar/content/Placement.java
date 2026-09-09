package metacraft.ovvar.content;

import java.util.Comparator;
import java.util.List;

/** One patch sewn on one cell. Stored as {@code spot.patch}; a half's set of them is a combo. */
public record Placement(Spot spot, String patch) implements Comparable<Placement> {
    private static final Comparator<Placement> ORDER = Comparator.comparing(Placement::spot).thenComparing(Placement::patch);

    public Placement {
        if (!Patches.get(patch).fits(spot)) throw new IllegalArgumentException("patch '" + patch + "' cannot go on " + spot.id());
    }

    public String key() {
        return spot.id() + "." + patch;
    }

    /** Never null: a bad key is a bug or foreign data, not a state. */
    public static Placement parse(String key) {
        int dot = key.indexOf('.');
        if (dot < 0) throw new IllegalArgumentException("bad placement '" + key + "' (want spot.patch)");
        return new Placement(Spot.get(key.substring(0, dot)), key.substring(dot + 1));
    }

    public static boolean isKey(String s) {
        int dot = s.indexOf('.');
        return dot > 0 && Spot.exists(s.substring(0, dot)) && Patches.exists(s.substring(dot + 1));
    }

    public Piece piece() {
        return spot.piece;
    }

    /** The combo id of a set of placements on one half: sorted keys joined by '-', "" for none. */
    public static String combo(List<Placement> placements) {
        return String.join("-", placements.stream().sorted().map(Placement::key).toList());
    }

    @Override
    public int compareTo(Placement o) {
        return ORDER.compare(this, o);
    }
}
