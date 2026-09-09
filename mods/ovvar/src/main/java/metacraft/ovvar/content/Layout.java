package metacraft.ovvar.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Where patches can go, and how that is encoded. Each half of the ovve (top = chest slot,
 * bottom = legs slot) carries {@value #BITS_PER_HALF} bits in its dye colour; the shader reads
 * them (see {@link Looks#dye}). A half is split
 * into fields: a MENU field is a spot that holds one patch chosen from a short list (a 4-bit
 * field can hold one of 15), a PINNED field is one specific patch that is either on or off.
 * Every field is one dyeable layer of the equipment asset, so the pack has one asset per half
 * and no file per combination.
 *
 * Retune freely: offsets are assigned in order and validated against the 24-bit budget. A
 * field's cells are where it is drawn (one 4×4 cell, or several for a big patch); a menu entry's
 * art must be 4×(4 × cells) pixels. Library room per field: {@link #LIBRARY_CELLS} cells.
 */
public final class Layout {
    private Layout() {}

    /** The dye colour holds three base-255 digits (no byte may be 0), 255³ > 2²³. */
    public static final int BITS_PER_HALF = 23;

    public enum Kind { MENU, PINNED }

    public record Field(String id, String name, Piece piece, Kind kind, List<Spot> cells, int bits, List<String> patches, int offset) {
        /** Encoded value for a patch, or 0 for none. */
        public int valueOf(String patchId) {
            int i = patches.indexOf(patchId);
            if (i < 0) throw new IllegalArgumentException("patch '" + patchId + "' does not fit field " + id);
            return i + 1;
        }

        public boolean accepts(String patchId) {
            return patches.contains(patchId);
        }

        public String patchAt(int value) {
            return value == 0 ? null : patches.get(value - 1);
        }

        public int mask() {
            return ((1 << bits) - 1) << offset;
        }
    }

    // ------------------------------------------------------------ texture geometry

    /** Most cells a field may span (the marker row has room for this many). */
    public static final int MAX_CELLS = 4;

    /**
     * 4×4 cells in the top strip of the 64×32 layout that nothing else uses: not the mirrored limb
     * boxes (x 0–16 and 40–56), not the marker texels (row 15, x 58–63). Library entry e of a
     * field with n cells uses cells e*n .. e*n+n-1.
     */
    public static final List<int[]> LIBRARY_CELLS = libraryCells();

    private static List<int[]> libraryCells() {
        List<int[]> out = new ArrayList<>();
        for (int y = 0; y < 16; y += 4) {
            for (int x = 16; x < 40; x += 4) out.add(new int[]{x, y});
        }
        for (int y = 0; y < 12; y += 4) {
            out.add(new int[]{56, y});
            out.add(new int[]{60, y});
        }
        return List.copyOf(out);
    }

    /** Marker texels on row 15, read by the shader (see entity.fsh). */
    public static final int MARKER_ROW = 15;
    public static final int MARKER_MAGIC_X = 63;   // magenta, alpha 2: "an ovvar texture"
    public static final int MARKER_FIELD_X = 62;   // R = bit offset, G = bits (0 = base texture), B = cell count
    public static final int MARKER_CELLS_X = 61;   // then 60, 59, 58: R = u, G = v, B = side ordinal
    /** Always transparent in a patch layer: what the shader draws where the field shows nothing. */
    public static final int BLANK_X = 63, BLANK_Y = 14;

    private static final List<Field> FIELDS = new ArrayList<>();
    private static final Map<String, Field> BY_ID = new LinkedHashMap<>();
    private static final Map<Piece, List<Field>> BY_PIECE = new EnumMap<>(Piece.class);

    // Placeholder menus: every small patch everywhere. Curate per spot when the real art exists.
    private static final List<String> SMALL = List.of("metacraft", "kth", "heart", "star", "beer", "nolle", "sittning", "gasque");

    static {
        // Top: 5 menu spots × 4 bits + one of 3 bits = 23.
        menu("chest_l", "chest, left", 4, SMALL, Spot.FRONT_TOP_RIGHT);
        menu("chest_r", "chest, right", 4, SMALL, Spot.FRONT_TOP_LEFT);
        menu("back_l", "back, left", 4, SMALL, Spot.BACK_TOP_LEFT);
        menu("back_r", "back, right", 4, SMALL, Spot.BACK_TOP_RIGHT);
        menu("sleeve_l", "left sleeve", 4, SMALL, Spot.SLEEVE_OUT_TOP_L);
        menu("sleeve_r", "right sleeve", 3, SMALL.subList(0, 7), Spot.SLEEVE_OUT_TOP_R);
        // Bottom: 4 menu spots × 5 bits = 20, plus the chapter patch across the seat: 1 bit.
        menu("leg_l", "left leg, outer", 5, SMALL, Spot.LEG_OUT_TOP_L);
        menu("leg_r", "right leg, outer", 5, SMALL, Spot.LEG_OUT_TOP_R);
        menu("leg_l_front", "left leg, front", 5, SMALL, Spot.LEG_FRONT_TOP_L);
        menu("leg_r_front", "right leg, front", 5, SMALL, Spot.LEG_FRONT_TOP_R);
        pinned("seat", "seat", "chapter", Spot.LEG_BACK_TOP_R, Spot.LEG_BACK_TOP_L);
        validate();
    }

    private static void menu(String id, String name, int bits, List<String> patches, Spot... cells) {
        add(id, name, Kind.MENU, bits, patches, cells);
    }

    private static void pinned(String id, String name, String patch, Spot... cells) {
        add(id, name, Kind.PINNED, 1, List.of(patch), cells);
    }

    private static void add(String id, String name, Kind kind, int bits, List<String> patches, Spot... cells) {
        Piece piece = cells[0].piece;
        int offset = BY_PIECE.getOrDefault(piece, List.of()).stream().mapToInt(f -> f.bits).sum();
        Field f = new Field(id, name, piece, kind, List.of(cells), bits, List.copyOf(patches), offset);
        FIELDS.add(f);
        if (BY_ID.put(id, f) != null) throw new IllegalStateException("duplicate field " + id);
        BY_PIECE.computeIfAbsent(piece, p -> new ArrayList<>()).add(f);
    }

    private static void validate() {
        for (Piece piece : Piece.values()) {
            int total = fields(piece).stream().mapToInt(Field::bits).sum();
            if (total > BITS_PER_HALF) throw new IllegalStateException(piece + " layout needs " + total + " bits; the dye colour has " + BITS_PER_HALF);
        }
        Set<Spot> used = new TreeSet<>();
        for (Field f : FIELDS) {
            for (Spot cell : f.cells) {
                if (cell.piece != f.piece) throw new IllegalStateException("field " + f.id + " mixes pieces");
                if (!used.add(cell)) throw new IllegalStateException("cell " + cell + " is in two fields");
            }
            if (f.cells.size() > MAX_CELLS) throw new IllegalStateException("field " + f.id + " has more than " + MAX_CELLS + " cells");
            int room = LIBRARY_CELLS.size() / f.cells.size();
            if (f.patches.size() > (1 << f.bits) - 1 || f.patches.size() > room) {
                throw new IllegalStateException("field " + f.id + ": " + f.patches.size() + " patches; " + f.bits + " bits allow "
                        + ((1 << f.bits) - 1) + " and the library " + room);
            }
            for (String p : f.patches) Patches.get(p);
        }
    }

    public static List<Field> all() {
        return Collections.unmodifiableList(FIELDS);
    }

    public static List<Field> fields(Piece piece) {
        return Collections.unmodifiableList(BY_PIECE.getOrDefault(piece, List.of()));
    }

    /** Never null: an unknown field id is a bug (a renamed field, a typo in a command). */
    public static Field get(String id) {
        Field f = BY_ID.get(id);
        if (f == null) throw new IllegalArgumentException("unknown field '" + id + "'");
        return f;
    }

    public static boolean exists(String id) {
        return BY_ID.containsKey(id);
    }

    /** The field owning a cell, or null. */
    public static Field at(Spot cell) {
        for (Field f : FIELDS) if (f.cells.contains(cell)) return f;
        return null;
    }
}
