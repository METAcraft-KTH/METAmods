package metacraft.ovvar.content;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The patch catalogue: the art that can be sewn on. A plain patch is 4×4 and goes on any cell
 * ({@link Spot}); a seat patch is 8×4 and goes across the seat only. Adding a patch is one line
 * here plus its PNG at {@code art/ovvar/patches/<id>.png}, then {@code runDatagen}. The first
 * {@value Looks#INSTANT_DESIGNS} can ride in the dye colour (sewn ones show at once, and they can
 * be previewed); later ones always go through the pack. Items store patches by id, so the order
 * is otherwise free.
 */
public final class Patches {
    private Patches() {}

    /** @param id also the art file name and the item id suffix ({@code ovvar:patch_<id>}) */
    public record Patch(String id, String name, boolean seat) {
        public Patch(String id, String name) {
            this(id, name, false);
        }

        public boolean fits(Spot spot) {
            return seat == (spot == Spot.SEAT);
        }

        /** Art width in cells. */
        public int cells() {
            return seat ? 2 : 1;
        }
    }


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
            new Patch("chapter", "Chapter", true)
    );

    private static final Map<String, Patch> BY_ID = ALL.stream()
            .collect(Collectors.toMap(Patch::id, p -> p, (a, b) -> { throw new IllegalStateException("duplicate patch id " + a.id()); }, LinkedHashMap::new));

    public static List<Patch> all() {
        return ALL;
    }

    /** index + 1, what the preview bits carry. */
    public static int code(String id) {
        return ALL.indexOf(get(id)) + 1;
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
