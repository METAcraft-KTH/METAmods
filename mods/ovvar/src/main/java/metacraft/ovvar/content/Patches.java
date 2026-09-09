package metacraft.ovvar.content;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The patch catalogue: the art that can be sewn on. Where a patch may go is decided by
 * {@link Layout} — a patch appears in the menus of the fields that accept it, or is the fixed
 * art of a pinned field. Adding a patch is one line here plus its PNG at
 * {@code art/ovvar/patches/<id>.png} (4×4 per cell it covers), then {@code runDatagen}.
 */
public final class Patches {
    private Patches() {}

    /** @param id also the art file name and the item id suffix ({@code ovvar:patch_<id>}) */
    public record Patch(String id, String name) {}

    // Placeholder set for testing the system; real patch art replaces these one for one.
    private static final List<Patch> ALL = List.of(
            new Patch("metacraft", "METAcraft"),
            new Patch("kth", "KTH"),
            new Patch("heart", "Heart"),
            new Patch("star", "Star"),
            new Patch("beer", "Beer"),
            new Patch("nolle", "Nolle"),
            new Patch("sittning", "Sittning"),
            new Patch("gasque", "Gasque"),
            new Patch("chapter", "Chapter")
    );

    private static final Map<String, Patch> BY_ID = ALL.stream()
            .collect(Collectors.toMap(Patch::id, p -> p, (a, b) -> { throw new IllegalStateException("duplicate patch id " + a.id()); }, LinkedHashMap::new));

    public static List<Patch> all() {
        return ALL;
    }

    /** Never null: an id that is not in the catalogue is a bug (a removed entry, a typo in a command), not a state. */
    public static Patch get(String id) {
        Patch p = BY_ID.get(id);
        if (p == null) throw new IllegalArgumentException("unknown patch '" + id + "'");
        return p;
    }

    public static boolean exists(String id) {
        return BY_ID.containsKey(id);
    }
}
