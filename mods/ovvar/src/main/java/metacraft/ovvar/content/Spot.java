package metacraft.ovvar.content;

/**
 * Where a patch sits: a 4×4 texel cell in the garment's 64×32 layer texture. Cells never overlap,
 * so patch layers can be drawn in any order. Left/right in chest and back names are as seen by
 * someone facing that side of the wearer; in sleeve and leg names they are the wearer's own
 * left and right limb.
 *
 * The armour model draws the left arm and leg as mirror images of the right ones, off the same
 * texture strips. Our core shader (assets/minecraft/shaders/core/entity.fsh) makes mirrored
 * fragments sample the same box one strip up (v − 16), so {@link Side#LEFT} cells are the right
 * cells' coordinates shifted into the free top strip; datagen also flips their art horizontally,
 * since the model's mirroring flips it back.
 *
 * Box strips (64×32 armour layout, rows 20–32): body 16 right | 20 front | 28 left | 32 back;
 * arm 40 outer | 44 front | 48 inner | 52 back; leg 0 outer | 4 front | 8 inner | 12 back.
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
    // top: sleeves, outer face and front face, 3 each per arm
    SLEEVE_OUT_TOP_R(Piece.TOP, 40, 20, Side.RIGHT), SLEEVE_OUT_MID_R(Piece.TOP, 40, 24, Side.RIGHT), SLEEVE_OUT_LOW_R(Piece.TOP, 40, 28, Side.RIGHT),
    SLEEVE_OUT_TOP_L(Piece.TOP, 40, 20, Side.LEFT), SLEEVE_OUT_MID_L(Piece.TOP, 40, 24, Side.LEFT), SLEEVE_OUT_LOW_L(Piece.TOP, 40, 28, Side.LEFT),
    SLEEVE_FRONT_TOP_R(Piece.TOP, 44, 20, Side.RIGHT), SLEEVE_FRONT_MID_R(Piece.TOP, 44, 24, Side.RIGHT), SLEEVE_FRONT_LOW_R(Piece.TOP, 44, 28, Side.RIGHT),
    SLEEVE_FRONT_TOP_L(Piece.TOP, 44, 20, Side.LEFT), SLEEVE_FRONT_MID_L(Piece.TOP, 44, 24, Side.LEFT), SLEEVE_FRONT_LOW_L(Piece.TOP, 44, 28, Side.LEFT),
    // bottom: legs, outer, front and back faces, 3 each per leg
    LEG_OUT_TOP_R(Piece.BOTTOM, 0, 20, Side.RIGHT), LEG_OUT_MID_R(Piece.BOTTOM, 0, 24, Side.RIGHT), LEG_OUT_LOW_R(Piece.BOTTOM, 0, 28, Side.RIGHT),
    LEG_OUT_TOP_L(Piece.BOTTOM, 0, 20, Side.LEFT), LEG_OUT_MID_L(Piece.BOTTOM, 0, 24, Side.LEFT), LEG_OUT_LOW_L(Piece.BOTTOM, 0, 28, Side.LEFT),
    LEG_FRONT_TOP_R(Piece.BOTTOM, 4, 20, Side.RIGHT), LEG_FRONT_MID_R(Piece.BOTTOM, 4, 24, Side.RIGHT), LEG_FRONT_LOW_R(Piece.BOTTOM, 4, 28, Side.RIGHT),
    LEG_FRONT_TOP_L(Piece.BOTTOM, 4, 20, Side.LEFT), LEG_FRONT_MID_L(Piece.BOTTOM, 4, 24, Side.LEFT), LEG_FRONT_LOW_L(Piece.BOTTOM, 4, 28, Side.LEFT),
    LEG_BACK_TOP_R(Piece.BOTTOM, 12, 20, Side.RIGHT), LEG_BACK_MID_R(Piece.BOTTOM, 12, 24, Side.RIGHT), LEG_BACK_LOW_R(Piece.BOTTOM, 12, 28, Side.RIGHT),
    LEG_BACK_TOP_L(Piece.BOTTOM, 12, 20, Side.LEFT), LEG_BACK_MID_L(Piece.BOTTOM, 12, 24, Side.LEFT), LEG_BACK_LOW_L(Piece.BOTTOM, 12, 28, Side.LEFT);

    public enum Side { BODY, RIGHT, LEFT }

    public static final int SIZE = 4;
    /** How far up the mirror strip sits from the limb boxes. */
    public static final int MIRROR_SHIFT = 16;

    public final Piece piece;
    public final Side side;
    /** Cell origin in the standard layout (the right limb's strip for limb spots). */
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

    /** Where datagen actually draws the cell: left-limb cells go to the mirror strip. */
    public int drawV() {
        return side == Side.LEFT ? v - MIRROR_SHIFT : v;
    }
}
