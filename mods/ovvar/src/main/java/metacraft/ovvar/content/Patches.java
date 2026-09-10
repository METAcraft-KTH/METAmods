package metacraft.ovvar.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JavaOps;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The patch catalogue: the art that can be sewn on. A plain patch goes on any cell ({@link Spot})
 * and is normally the cell's size ({@link Spot#PX} square); a bigger one is centred on its cell
 * and hangs over the neighbours — later-sewn on top, the way real ovvar are patched. A seat patch
 * is two cells wide and goes across the seat only. Adding a patch is one line here plus its PNG at
 * {@code art/ovvar/patches/<id>.png}, then {@code runDatagen}. The first
 * {@value Looks#INSTANT_DESIGNS} cell-sized ones can ride in the dye colour (sewn ones show at once);
 * bigger ones and later ones always go through the pack. Items store patches by id, so the order
 * is otherwise free.
 */
public final class Patches {
    private Patches() {}

    /** The most a patch's art may hang over: two cells each way. Even sizes only — the art is centred on a cell. */
    public static final int MAX_ART = 2 * Spot.PX;

    /**
     * @param id     also the art file name and the item id suffix ({@code ovvar:patch_<id>})
     * @param width  art width in pixels ({@link Spot#PX} for a cell-sized patch; a seat patch is 2 cells wide)
     * @param height art height in pixels
     */
    public record Patch(String id, String name, boolean seat, int width, int height) {
        public Patch {
            if (seat && (width != 2 * Spot.PX || height != Spot.PX)) throw new IllegalArgumentException(id + ": a seat patch is " + 2 * Spot.PX + "×" + Spot.PX);
            if (width < 2 || height < 2 || width > MAX_ART || height > MAX_ART || width % 2 != 0 || height % 2 != 0) {
                throw new IllegalArgumentException(id + ": patch art must be an even size up to " + MAX_ART + "×" + MAX_ART + ", not " + width + "×" + height);
            }
        }

        /** A cell-sized patch. */
        public Patch(String id, String name) {
            this(id, name, false, Spot.PX, Spot.PX);
        }

        /** A patch bigger (or smaller) than its cell, centred on it. */
        public Patch(String id, String name, int width, int height) {
            this(id, name, false, width, height);
        }

        public static Patch seat(String id, String name) {
            return new Patch(id, name, true, 2 * Spot.PX, Spot.PX);
        }

        public boolean fits(Spot spot) {
            return seat == (spot == Spot.SEAT);
        }

        /** Art width in cells (the seat's two; a big patch is still one cell's). */
        public int cells() {
            return seat ? 2 : 1;
        }

        /** Does the art hang over its cell? Such a patch never rides in the dye colour (the preview library holds cell-sized art). */
        public boolean oversize() {
            return !seat && (width > Spot.PX || height > Spot.PX);
        }

        /** Where the art's top-left lands relative to the cell's, in texels (centred). */
        public int offsetX() {
            return (Spot.PX * cells() - width) / 2;
        }

        public int offsetY() {
            return (Spot.PX - height) / 2;
        }
    }


    // Placeholder set for testing the system; real patch art replaces these one for one.
    private static final List<Patch> ALL = List.of(
            new Patch("metacraft", "METAcraft"),
            new Patch("kth", "KTH", 12, 12),   // hangs over its cell: the overlap showcase
            new Patch("heart", "Heart"),
            new Patch("star", "Star"),
            new Patch("beer", "Beer"),
            new Patch("nolle", "Nolle"),
            new Patch("sittning", "Sittning"),
            new Patch("gasque", "Gasque"),
            Patch.seat("chapter", "Chapter")
    );

    private static final Map<String, Patch> BY_ID = ALL.stream()
            .collect(Collectors.toMap(Patch::id, p -> p, (a, b) -> { throw new IllegalStateException("duplicate patch id " + a.id()); }, LinkedHashMap::new));

    public static final Codec<Patch> ID_CODEC = Codec.STRING.comapFlatMap(
            id -> {
                var patch = BY_ID.get(id);
                if (patch != null) {
                    return DataResult.success(patch);
                } else {
                    return DataResult.error(() -> "unknown patch '" + id + "'");
                }
            },
            Patch::id
    );

    public static List<Patch> all() {
        return ALL;
    }

    /** index + 1, what the preview bits carry. */
    public static int code(Patch patch) {
        return ALL.indexOf(patch) + 1;
    }

    /** Never null: an id that is not in the catalogue is a bug (a removed entry, a typo in a command), not a state. */
    public static Patch get(String id) {
        return ID_CODEC.parse(JavaOps.INSTANCE, id).getOrThrow(IllegalArgumentException::new);
    }

    public static boolean exists(String id) {
        return BY_ID.containsKey(id);
    }
}
