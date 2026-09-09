package metacraft.ovvar.content;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The patch catalogue: the single source for the server (items, sewing), the datagen (every
 * combination's equipment definition) and the look key. Adding a patch is one line here plus a
 * 4×4 PNG at {@code art/ovvar/patches/<id>.png}; then {@code runDatagen}.
 *
 * Order matters: a garment's look key is a bitmask over this list filtered to its piece, so
 * reordering or removing an entry changes what every sewn garment in the world shows. Append.
 */
public final class Patches {
    private Patches() {}

    /** Combinations are pre-generated (2^n per garment per chapter); past this the pack stops being small. */
    public static final int MAX_PER_PIECE = 12;

    /** @param id also the art file name and the item id suffix ({@code ovvar:patch_<id>}) */
    public record Patch(String id, String name, Spot spot) {
        public Piece piece() {
            return spot.piece;
        }
    }

    // Placeholder set for testing the system; real patch art replaces these one for one.
    private static final List<Patch> ALL = List.of(
            new Patch("metacraft", "METAcraft", Spot.FRONT_TOP_LEFT),
            new Patch("kth", "KTH", Spot.FRONT_TOP_RIGHT),
            new Patch("heart", "Heart", Spot.FRONT_MID_LEFT),
            new Patch("star", "Star", Spot.BACK_TOP_LEFT),
            new Patch("beer", "Beer", Spot.BACK_MID_RIGHT),
            new Patch("nolle", "Nolle", Spot.SLEEVE_OUT_TOP_L),
            new Patch("sittning", "Sittning", Spot.LEG_OUT_TOP_R),
            new Patch("gasque", "Gasque", Spot.LEG_OUT_TOP_L)
    );

    private static final Map<String, Patch> BY_ID = ALL.stream()
            .collect(Collectors.toMap(Patch::id, p -> p, (a, b) -> { throw new IllegalStateException("duplicate patch id " + a.id()); }, LinkedHashMap::new));
    private static final Map<Piece, List<Patch>> BY_PIECE = new EnumMap<>(Piece.class);

    static {
        Map<Spot, Patch> spots = new EnumMap<>(Spot.class);
        for (Patch p : ALL) {
            Patch other = spots.put(p.spot(), p);
            if (other != null) throw new IllegalStateException("patches " + other.id() + " and " + p.id() + " share spot " + p.spot());
        }
        for (Piece piece : Piece.values()) {
            List<Patch> list = ALL.stream().filter(p -> p.piece() == piece).toList();
            if (list.size() > MAX_PER_PIECE) {
                throw new IllegalStateException(piece + " has " + list.size() + " patches; the cap is " + MAX_PER_PIECE);
            }
            BY_PIECE.put(piece, list);
        }
    }

    public static List<Patch> all() {
        return ALL;
    }

    public static List<Patch> forPiece(Piece piece) {
        return Collections.unmodifiableList(BY_PIECE.get(piece));
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
