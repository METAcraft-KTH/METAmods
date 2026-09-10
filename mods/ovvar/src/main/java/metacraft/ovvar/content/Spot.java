package metacraft.ovvar.content;

/**
 * A 4×4 texel cell on the garment, in the 64×32 armour layout — the faces you see, keeping off
 * the collar and the belt (the body's top and bottom texel rows), the hands (the bottom row of
 * the arms), the cuffs (the bottom row of the legs), and the inner faces.
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
    // top: chest and back, 2 columns × 2 rows each. The rows sit at v 21 and 26: the body's top
    // texel row is the collar and its bottom row the belt. No cells on the body's sides, the inner
    // arms or the inner legs: hidden most of the time, and their bits buy more instant designs.
    FRONT_TOP_LEFT(Piece.TOP, 20, 21), FRONT_TOP_RIGHT(Piece.TOP, 24, 21),
    FRONT_LOW_LEFT(Piece.TOP, 20, 26), FRONT_LOW_RIGHT(Piece.TOP, 24, 26),
    BACK_TOP_LEFT(Piece.TOP, 32, 21), BACK_TOP_RIGHT(Piece.TOP, 36, 21),
    BACK_LOW_LEFT(Piece.TOP, 32, 26), BACK_LOW_RIGHT(Piece.TOP, 36, 26),
    // top: sleeves, outer, front and back faces, top and middle rows per arm (the bottom row is the hand)
    SLEEVE_OUT_TOP_R(Piece.TOP, 40, 20, Side.RIGHT), SLEEVE_OUT_MID_R(Piece.TOP, 40, 24, Side.RIGHT),
    SLEEVE_OUT_TOP_L(Piece.TOP, 40, 20, Side.LEFT), SLEEVE_OUT_MID_L(Piece.TOP, 40, 24, Side.LEFT),
    SLEEVE_FRONT_TOP_R(Piece.TOP, 44, 20, Side.RIGHT), SLEEVE_FRONT_MID_R(Piece.TOP, 44, 24, Side.RIGHT),
    SLEEVE_FRONT_TOP_L(Piece.TOP, 44, 20, Side.LEFT), SLEEVE_FRONT_MID_L(Piece.TOP, 44, 24, Side.LEFT),
    SLEEVE_BACK_TOP_R(Piece.TOP, 52, 20, Side.RIGHT), SLEEVE_BACK_MID_R(Piece.TOP, 52, 24, Side.RIGHT),
    SLEEVE_BACK_TOP_L(Piece.TOP, 52, 20, Side.LEFT), SLEEVE_BACK_MID_L(Piece.TOP, 52, 24, Side.LEFT),
    // bottom: legs, outer, front and back faces, top and middle rows per leg (the bottom row is the cuff, under boots)
    LEG_OUT_TOP_R(Piece.BOTTOM, 0, 20, Side.RIGHT), LEG_OUT_MID_R(Piece.BOTTOM, 0, 24, Side.RIGHT),
    LEG_OUT_TOP_L(Piece.BOTTOM, 0, 20, Side.LEFT), LEG_OUT_MID_L(Piece.BOTTOM, 0, 24, Side.LEFT),
    LEG_FRONT_TOP_R(Piece.BOTTOM, 4, 20, Side.RIGHT), LEG_FRONT_MID_R(Piece.BOTTOM, 4, 24, Side.RIGHT),
    LEG_FRONT_TOP_L(Piece.BOTTOM, 4, 20, Side.LEFT), LEG_FRONT_MID_L(Piece.BOTTOM, 4, 24, Side.LEFT),
    LEG_BACK_TOP_R(Piece.BOTTOM, 12, 20, Side.RIGHT), LEG_BACK_MID_R(Piece.BOTTOM, 12, 24, Side.RIGHT),
    LEG_BACK_TOP_L(Piece.BOTTOM, 12, 20, Side.LEFT), LEG_BACK_MID_L(Piece.BOTTOM, 12, 24, Side.LEFT),
    /** The seat: one 8×4 patch across the back of both legs (LEG_BACK_TOP_R + LEG_BACK_TOP_L). Only seat patches go here. */
    SEAT(Piece.BOTTOM, 12, 20, Side.SEAT);

    /** BODY = an unmirrored face; RIGHT/LEFT = the limb the cell is drawn on; SEAT = both legs. Ordinal is what the shader reads. */
    public enum Side { BODY, RIGHT, LEFT, SEAT }

    /** A cell's side in skin texels (the coordinates here). */
    public static final int SIZE = 4;
    /** Texels per skin texel in the garment and patch textures (128×64): patch art is {@link #PX} square. */
    public static final int DETAIL = 2;
    public static final int PX = SIZE * DETAIL;

    /** The armour model's inflation for a piece's layer: 1 for the chest layer, 0.5 for the leggings layer. */
    public static double inflate(Piece piece) {
        return piece == Piece.TOP ? 1.0 : 0.5;
    }

    /** Start (skin texel) of the box face a cell is on: legs and arms four 4-wide faces, body right 4 | front 8 | left 4 | back 8. */
    public static int faceStart(Spot spot) {
        int u = spot.u;
        if (u < 16 || u >= 40) return u / 4 * 4;
        if (u < 20) return 16;
        if (u < 28) return 20;
        if (u < 32) return 28;
        return 32;
    }

    public static int faceWidth(Spot spot) {
        int start = faceStart(spot);
        return start == 20 || start == 32 ? 8 : 4;   // the body's front and back
    }

    /**
     * The armour model draws a texel wider than tall: the box is inflated, its texture is not,
     * so a face n texels wide covers n + 2·inflate units and 12 rows cover 12 + 2·inflate. Art
     * is squeezed in x by this about its face's centre to come out with square pixels — mirrored
     * in ovvar.glsl, which does it per fragment for the garment and the patch layers; datagen
     * bakes it into the trim textures vanilla draws.
     */
    public static double squeeze(Spot spot) {
        double i = inflate(spot.piece), n = faceWidth(spot);
        return ((12 + 2 * i) / 12) / ((n + 2 * i) / n);
    }
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

    /** The cells of one half in a fixed order; a cell's index here is what the instant channel carries. */
    public static java.util.List<Spot> cells(Piece piece) {
        return java.util.Arrays.stream(values()).filter(s -> s.piece == piece).toList();
    }

    /** The cell nearest a strip position in its column (u, side), or null if the column has none. */
    public static Spot nearest(Piece piece, int u, double v, Side side) {
        Spot best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Spot s : values()) {
            if (s.piece != piece || s.u != u || s.side != side) continue;
            double d = v < s.v ? s.v - v : v >= s.v + SIZE ? v - (s.v + SIZE) + 1 : 0;
            if (d < bestDistance) { bestDistance = d; best = s; }
        }
        return best;
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
        if (rest.startsWith("side_")) return (n.endsWith("_r") ? "right" : "left") + " side, " + rest.substring(5);
        for (String face : new String[]{"out", "front", "in", "back"}) {
            String word = face.equals("out") ? "outer" : face.equals("in") ? "inner" : face;
            if (rest.startsWith("sleeve_" + face + "_")) return limb + "sleeve, " + word + " " + rest.substring(8 + face.length());
            if (rest.startsWith("leg_" + face + "_")) return limb + "leg, " + word + " " + rest.substring(5 + face.length());
        }
        return n.replace('_', ' ');
    }
}
