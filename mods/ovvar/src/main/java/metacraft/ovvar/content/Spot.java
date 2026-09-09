package metacraft.ovvar.content;

/**
 * A 4×4 texel cell on the garment, in the 64×32 armour layout — anywhere a patch can go. The
 * sleeves stop at the middle row: the bottom row of the arm boxes is drawn over the hands.
 * Items store cells by name, so entries may be added or removed; the ordinal only travels in the
 * (transient) preview bits. Left/right in sleeve and leg
 * names are the wearer's own; in chest and back names they are as seen by someone facing that
 * side. Sleeve and leg cells sit on the right limb's strips: the armour model draws the left limb
 * as a mirror image off the same strips, and the shader tells the two apart by the handedness
 * of the texture mapping, so a LEFT cell is the same rectangle drawn only on mirrored fragments
 * (with its art flipped back).
 *
 * Box strips (rows 20–32): body 16 right | 20 front | 28 left | 32 back; arm 40 outer | 44 front
 * | 48 inner | 52 back; leg 0 outer | 4 front | 8 inner | 12 back.
 */
public enum Spot {
    // top: chest, 2 columns × 3 rows
    FRONT_TOP_LEFT(Piece.TOP, 20, 20), FRONT_TOP_RIGHT(Piece.TOP, 24, 20),
    FRONT_MID_LEFT(Piece.TOP, 20, 24), FRONT_MID_RIGHT(Piece.TOP, 24, 24),
    FRONT_LOW_LEFT(Piece.TOP, 20, 28), FRONT_LOW_RIGHT(Piece.TOP, 24, 28),
    // top: back, 2 × 3
    BACK_TOP_LEFT(Piece.TOP, 32, 20), BACK_TOP_RIGHT(Piece.TOP, 36, 20),
    BACK_MID_LEFT(Piece.TOP, 32, 24), BACK_MID_RIGHT(Piece.TOP, 36, 24),
    BACK_LOW_LEFT(Piece.TOP, 32, 28), BACK_LOW_RIGHT(Piece.TOP, 36, 28),
    // top: sleeves, outer face and front face, top and middle rows per arm (the bottom row is the hand)
    SLEEVE_OUT_TOP_R(Piece.TOP, 40, 20, Side.RIGHT), SLEEVE_OUT_MID_R(Piece.TOP, 40, 24, Side.RIGHT),
    SLEEVE_OUT_TOP_L(Piece.TOP, 40, 20, Side.LEFT), SLEEVE_OUT_MID_L(Piece.TOP, 40, 24, Side.LEFT),
    SLEEVE_FRONT_TOP_R(Piece.TOP, 44, 20, Side.RIGHT), SLEEVE_FRONT_MID_R(Piece.TOP, 44, 24, Side.RIGHT),
    SLEEVE_FRONT_TOP_L(Piece.TOP, 44, 20, Side.LEFT), SLEEVE_FRONT_MID_L(Piece.TOP, 44, 24, Side.LEFT),
    // bottom: legs, outer, front and back faces, 3 each per leg
    LEG_OUT_TOP_R(Piece.BOTTOM, 0, 20, Side.RIGHT), LEG_OUT_MID_R(Piece.BOTTOM, 0, 24, Side.RIGHT), LEG_OUT_LOW_R(Piece.BOTTOM, 0, 28, Side.RIGHT),
    LEG_OUT_TOP_L(Piece.BOTTOM, 0, 20, Side.LEFT), LEG_OUT_MID_L(Piece.BOTTOM, 0, 24, Side.LEFT), LEG_OUT_LOW_L(Piece.BOTTOM, 0, 28, Side.LEFT),
    LEG_FRONT_TOP_R(Piece.BOTTOM, 4, 20, Side.RIGHT), LEG_FRONT_MID_R(Piece.BOTTOM, 4, 24, Side.RIGHT), LEG_FRONT_LOW_R(Piece.BOTTOM, 4, 28, Side.RIGHT),
    LEG_FRONT_TOP_L(Piece.BOTTOM, 4, 20, Side.LEFT), LEG_FRONT_MID_L(Piece.BOTTOM, 4, 24, Side.LEFT), LEG_FRONT_LOW_L(Piece.BOTTOM, 4, 28, Side.LEFT),
    LEG_BACK_TOP_R(Piece.BOTTOM, 12, 20, Side.RIGHT), LEG_BACK_MID_R(Piece.BOTTOM, 12, 24, Side.RIGHT), LEG_BACK_LOW_R(Piece.BOTTOM, 12, 28, Side.RIGHT),
    LEG_BACK_TOP_L(Piece.BOTTOM, 12, 20, Side.LEFT), LEG_BACK_MID_L(Piece.BOTTOM, 12, 24, Side.LEFT), LEG_BACK_LOW_L(Piece.BOTTOM, 12, 28, Side.LEFT),
    /** The seat: one 8×4 patch across the back of both legs (LEG_BACK_TOP_R + LEG_BACK_TOP_L). Only seat patches go here. */
    SEAT(Piece.BOTTOM, 12, 20, Side.SEAT);

    /** BODY = an unmirrored face; RIGHT/LEFT = the limb the cell is drawn on; SEAT = both legs. Ordinal is what the shader reads. */
    public enum Side { BODY, RIGHT, LEFT, SEAT }

    public static final int SIZE = 4;
    /** How far up the mirror strip sits from the limb boxes. */
    public static final int MIRROR_SHIFT = 16;

    public final Piece piece;
    public final Side side;
    /** Cell origin in the standard layout (the right limb's strip for limb cells). */
    public final int u, v;

    Spot(Piece piece, int u, int v) {
        this(piece, u, v, Side.BODY);
    }

    Spot(Piece piece, int u, int v, Side side) {
        this.piece = piece;
        this.u = u;
        this.v = v;
        this.side = side;
    }

    /** The cells a seat patch covers, which a seat patch and a plain patch fight over. */
    public static final java.util.List<Spot> SEAT_CELLS = java.util.List.of(LEG_BACK_TOP_R, LEG_BACK_TOP_L);

    private static final java.util.Map<String, Spot> BY_KEY = new java.util.HashMap<>();

    static {
        for (Spot s : values()) BY_KEY.put(s.piece + "/" + s.u + "/" + s.v + "/" + s.side, s);
    }

    /** The cell at a strip position, or null if no patch goes there. */
    public static Spot at(Piece piece, int u, int v, Side side) {
        return BY_KEY.get(piece + "/" + u + "/" + v + "/" + side);
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Never null: an unknown spot id is a bug (a renamed cell, a typo in a command). */
    public static Spot get(String id) {
        for (Spot s : values()) if (s.id().equals(id)) return s;
        throw new IllegalArgumentException("unknown spot '" + id + "'");
    }

    public static boolean exists(String id) {
        for (Spot s : values()) if (s.id().equals(id)) return true;
        return false;
    }

    /** Cells that overlap this one (a seat patch covers two leg cells). */
    public java.util.List<Spot> overlapping() {
        if (this == SEAT) return SEAT_CELLS;
        return SEAT_CELLS.contains(this) ? java.util.List.of(SEAT) : java.util.List.of();
    }

    /** "chest, top left" / "left sleeve, outer top" — for tooltips. */
    public String label() {
        if (this == SEAT) return "seat";
        String n = name().toLowerCase(java.util.Locale.ROOT);
        String limb = side == Side.LEFT ? "left " : side == Side.RIGHT ? "right " : "";
        if (n.startsWith("front_")) return "chest, " + n.substring(6).replace('_', ' ');
        if (n.startsWith("back_")) return "back, " + n.substring(5).replace('_', ' ');
        String rest = n.replaceAll("_[lr]$", "");
        if (rest.startsWith("sleeve_out_")) return limb + "sleeve, outer " + rest.substring(11);
        if (rest.startsWith("sleeve_front_")) return limb + "sleeve, front " + rest.substring(13);
        if (rest.startsWith("leg_out_")) return limb + "leg, outer " + rest.substring(8);
        if (rest.startsWith("leg_front_")) return limb + "leg, front " + rest.substring(10);
        if (rest.startsWith("leg_back_")) return limb + "leg, back " + rest.substring(9);
        return n.replace('_', ' ');
    }
}
