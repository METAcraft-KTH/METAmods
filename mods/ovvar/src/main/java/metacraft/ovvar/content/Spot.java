package metacraft.ovvar.content;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/**
 * A cell on the garment, in the 64×32 armour layout — {@value #SIZE}×{@value #SIZE} texels unless
 * it says otherwise ({@link #width}, {@link #height}) — the faces you see, keeping off
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
 *
 * Nearly every cell is on those side rows. The exception is a cell on a box's <em>top</em> face,
 * rows {@value #TOP_ROW}–{@value #FACE_ROW} of the strip's second block of four columns (the
 * shoulders): {@link #top} tells the two kinds apart and {@link #face} answers {@value #TOP_FACE}
 * for the top one. A top face has no neighbour to bend onto in the layout, so art bigger than a
 * top cell is <b>clipped</b> to the face rather than wrapped round it.
 */
public enum Spot implements StringRepresentable {
	// top: chest and back, 2 columns × 2 rows each. The rows sit at v 21 and 26: the body's top
	// texel row is the collar and its bottom row the belt. No cells on the body's sides, the inner
	// arms or the inner legs: hidden most of the time, and their bits buy more instant designs.
	FRONT_TOP_LEFT(Piece.TOP, 20, 21), FRONT_TOP_RIGHT(Piece.TOP, 24, 21),
	FRONT_LOW_LEFT(Piece.TOP, 20, 26), FRONT_LOW_RIGHT(Piece.TOP, 24, 26),
	BACK_TOP_LEFT(Piece.TOP, 32, 21), BACK_TOP_RIGHT(Piece.TOP, 36, 21),
	/**
	 * The big back cell: the whole back face below the collar, {@value #BIG}×{@value #BIG} texels
	 * (rows 22–29, the belt row 31 and the collar row 20 clear), which takes a patch at
	 * {@link Patches#MAX_ART} square without any of it hanging over onto another face. It covers the
	 * two {@code BACK_TOP} cells and all three may be sewn at once — overlapping patches are the
	 * point of an ovve — with the big one underneath and the two small ones over it ({@link #layer}).
	 */
	BACK_BIG(Piece.TOP, 32, 22, Side.BODY, Spot.BIG, Spot.BIG),
	// top: sleeves, outer, front and back faces, top and middle rows per arm. The rows sit at v 21
	// and 25, a texel below the shoulder (the playtest wanted them lower on the arm); the bottom
	// row of the arm, v 31, is the hand.
	SLEEVE_OUT_TOP_R(Piece.TOP, 40, 21, Side.RIGHT), SLEEVE_OUT_MID_R(Piece.TOP, 40, 25, Side.RIGHT),
	SLEEVE_OUT_TOP_L(Piece.TOP, 40, 21, Side.LEFT), SLEEVE_OUT_MID_L(Piece.TOP, 40, 25, Side.LEFT),
	SLEEVE_FRONT_TOP_R(Piece.TOP, 44, 21, Side.RIGHT), SLEEVE_FRONT_MID_R(Piece.TOP, 44, 25, Side.RIGHT),
	SLEEVE_FRONT_TOP_L(Piece.TOP, 44, 21, Side.LEFT), SLEEVE_FRONT_MID_L(Piece.TOP, 44, 25, Side.LEFT),
	SLEEVE_BACK_TOP_R(Piece.TOP, 52, 21, Side.RIGHT), SLEEVE_BACK_MID_R(Piece.TOP, 52, 25, Side.RIGHT),
	SLEEVE_BACK_TOP_L(Piece.TOP, 52, 21, Side.LEFT), SLEEVE_BACK_MID_L(Piece.TOP, 52, 25, Side.LEFT),
	/**
	 * The shoulders: the arm boxes' TOP faces, the whole of them — the arm strip's u 44..48, rows
	 * {@value #TOP_ROW}–{@value #FACE_ROW}. The only cells that are not on the box sides, and the
	 * only ones a patch cannot hang over: a top face's four edges have no neighbouring face in the
	 * layout to continue onto, so art bigger than the cell is clipped to the face (see {@link #top}).
	 * The left arm's top face is mirrored in u like every other left-limb face.
	 */
	SHOULDER_R(Piece.TOP, 44, Spot.TOP_ROW, Side.RIGHT), SHOULDER_L(Piece.TOP, 44, Spot.TOP_ROW, Side.LEFT),
	// bottom: legs, outer, front and back faces, top and middle rows per leg. The rows sit at v 22
	// and 26, two texels below the waist (the playtest wanted them lower still, a patch on the thigh
	// rather than on the hip); the cuff row under a boot stays clear, 26 + 4 = 30 < 31.
	LEG_OUT_TOP_R(Piece.BOTTOM, 0, 22, Side.RIGHT), LEG_OUT_MID_R(Piece.BOTTOM, 0, 26, Side.RIGHT),
	LEG_OUT_TOP_L(Piece.BOTTOM, 0, 22, Side.LEFT), LEG_OUT_MID_L(Piece.BOTTOM, 0, 26, Side.LEFT),
	LEG_FRONT_TOP_R(Piece.BOTTOM, 4, 22, Side.RIGHT), LEG_FRONT_MID_R(Piece.BOTTOM, 4, 26, Side.RIGHT),
	LEG_FRONT_TOP_L(Piece.BOTTOM, 4, 22, Side.LEFT), LEG_FRONT_MID_L(Piece.BOTTOM, 4, 26, Side.LEFT),
	LEG_BACK_TOP_R(Piece.BOTTOM, 12, 22, Side.RIGHT), LEG_BACK_MID_R(Piece.BOTTOM, 12, 26, Side.RIGHT),
	LEG_BACK_TOP_L(Piece.BOTTOM, 12, 22, Side.LEFT), LEG_BACK_MID_L(Piece.BOTTOM, 12, 26, Side.LEFT),
	/**
	 * The seat: one patch two cells wide across the back of both legs (LEG_BACK_TOP_R +
	 * LEG_BACK_TOP_L), so it follows their row. Seat art may be taller than the row
	 * ({@link Patches#SEAT_HEIGHT_MAX}), centred on it and hanging onto the cloth below and above.
	 */
	SEAT(Piece.BOTTOM, 12, 22, Side.SEAT, 2 * Spot.SIZE, Spot.SIZE);

	public static final Codec<Spot> CODEC = StringRepresentable.fromEnum(Spot::values);

	@Override
	public @NonNull String getSerializedName() {
		return id();
	}

	/** BODY = an unmirrored face; RIGHT/LEFT = the limb the cell is drawn on; SEAT = both legs. Ordinal is what the shader reads. */
	public enum Side { BODY, RIGHT, LEFT, SEAT }

	/** A cell's side in skin texels (the coordinates here), unless the entry names its own. */
	public static final int SIZE = 4;
	/** The side of a big cell: two cells each way, so its art can be {@link Patches#MAX_ART} square. */
	public static final int BIG = 2 * SIZE;
	/**
	 * The box <em>side</em> rows of the armour layout, which is where every cell is: skin rows
	 * {@value #FACE_ROW} to {@value #FACE_ROW} + {@value #FACE_ROWS}. Above them are the boxes' top
	 * and bottom faces. Anything that measures a cell down a face — datagen's clip, the stand
	 * sprites, the aim, the paper doll — reads these rather than writing 20 and 32 again, so moving
	 * a row of cells is one edit in the enum above.
	 */
	public static final int FACE_ROW = 20, FACE_ROWS = 12;
	/**
	 * The box <em>top</em> faces' rows, above the side rows: skin rows {@value #TOP_ROW} to
	 * {@value #FACE_ROW}, {@value #TOP_ROWS} of them, on the strip's second block of four columns
	 * (the bottom face is on the third). The shoulders are there; {@link #top} is the test.
	 */
	public static final int TOP_ROW = FACE_ROW - SIZE, TOP_ROWS = SIZE;
	/** {@link #face}'s answer for a cell on its box's top face, which is none of the four side faces. */
	public static final int TOP_FACE = 4;
	/** Texels per skin texel in the garment and patch textures (128×64): patch art is {@link #PX} square. */
	public static final int DETAIL = 2;
	public static final int PX = SIZE * DETAIL;

	/** The armour model's inflation for a piece's layer: 1 for the chest layer, 0.5 for the leggings layer. */
	public static double inflate(Piece piece) {
		return piece == Piece.TOP ? 1.0 : 0.5;
	}

	/** The pixel every layer on a part is drawn at, in model units (ovvar_pixel in ovvar.glsl). */
	public static double pixel(int skinX, double inflate) {
		return skinX < 16 ? 13.0 / 12 : (12 + 2 * inflate) / 12;
	}

	/**
	 * The face of its strip a cell is on, 0..3: a limb's outer, front, inner, back; the body's
	 * right side, front, left side, back — or {@value #TOP_FACE} for the box's top face, which is
	 * not on the strip at all (the shoulders). Datagen puts this in a placement texture's kind
	 * texel and ovvar.glsl reads it back, so the two share the numbering.
	 */
	public static int face(Spot spot) {
		if (spot.top()) return TOP_FACE;
		int local = spot.u - stripStart(spot), n1 = stripWidth(spot) == 24 ? 8 : 4;
		return local < 4 ? 0 : local < 4 + n1 ? 1 : local < 8 + n1 ? 2 : 3;
	}

	/**
	 *      * ovvar_anchored in ovvar.glsl, the same arithmetic: a placement is drawn continuous round
	 * the box from the face it is on — that face's texels centred on it, the neighbours'
	 * continuing past its edges at {@link #pixel} units each — with the slack of the inflated
	 * box in the middle of the opposite face. For a side-row texel {@code skinX} (fractional,
	 * skin texels): the strip-local texel column shown there, or -1 in the slack. Datagen bakes
	 * the trim textures with it, since vanilla draws those.
	 */
	public static double anchored(double skinX, double inflate, int anchor) {
		double p = pixel((int) Math.floor(skinX), inflate), e = 2 * inflate;
		boolean body = skinX >= 16 && skinX < 40;
		int stripStart = skinX < 16 ? 0 : body ? 16 : 40;
		double local = skinX - stripStart;
		int n1 = body ? 8 : 4, total = 8 + 2 * n1;
		double perimeter = total + 4 * e;
		int k = local < 4 ? 0 : local < 4 + n1 ? 1 : local < 8 + n1 ? 2 : 3;
		double[] s = {0, 4, 4 + n1, 8 + n1}, n = {4, n1, 4, n1}, U = {0, 4 + e, 4 + n1 + 2 * e, 8 + n1 + 3 * e};
		double u = U[k] + (local - s[k]) * ((n[k] + e) / n[k]);
		double c = U[anchor] + (n[anchor] + e) / 2, t = s[anchor] + n[anchor] / 2.0;
		double du = u - c;
		if (du >= perimeter / 2) du -= perimeter; else if (du < -perimeter / 2) du += perimeter;
		double dt = du / p;
		if (Math.abs(dt) > total / 2.0) return -1;
		double w = t + dt;
		return w - Math.floor(w / total) * total;
	}


	/** Start of the strip (skin texels) a cell's part draws: legs 0, body 16, arms 40. */
	public static int stripStart(Spot spot) {
		return spot.u < 16 ? 0 : spot.u < 40 ? 16 : 40;
	}

	public static int stripWidth(Spot spot) {
		return spot.u >= 16 && spot.u < 40 ? 24 : 16;
	}
	/** How far up the mirror strip sits from the limb boxes. */
	public static final int MIRROR_SHIFT = 16;

	public final Piece piece;
	public final Side side;
	/** Cell origin in the standard layout (the right limb's strip for limb cells). */
	public final int u, v;
	/**
	 * The cell's own size in skin texels: {@value #SIZE} square for nearly all of them, {@link #BIG}
	 * square for {@link #BACK_BIG}, and two cells by one for the {@link #SEAT}, which is a cell on
	 * each leg. A patch is centred in this, so it is what {@link Patches.Patch#offsetX} and
	 * {@code offsetY} measure against, and {@link #px}/{@link #pxHeight} is it in texture pixels.
	 */
	public final int width, height;

	Spot(Piece piece, int u, int v) {
		this(piece, u, v, Side.BODY);
	}

	Spot(Piece piece, int u, int v, Side side) {
		this(piece, u, v, side, SIZE, SIZE);
	}

	Spot(Piece piece, int u, int v, Side side, int width, int height) {
		this.piece = piece;
		this.u = u;
		this.v = v;
		this.side = side;
		this.width = width;
		this.height = height;
	}

	/** The cell's width in texture pixels ({@link #DETAIL} per skin texel): {@value #PX} for most. */
	public int px() {
		return width * DETAIL;
	}

	public int pxHeight() {
		return height * DETAIL;
	}

	/**
	 * Is this cell on its box's <em>top</em> face rather than on the side rows? Read off the row,
	 * which is the whole of the difference: rows {@value #TOP_ROW}–{@value #FACE_ROW} are the top
	 * face, {@value #FACE_ROW} and below are the sides. It decides three things everywhere the cell
	 * is drawn — no squeeze round the box (the top face is not on the strip's perimeter), no wrap
	 * for art that hangs over (it is clipped to the face), and the rows it is measured down from
	 * ({@link #TOP_ROW}, not {@link #FACE_ROW}).
	 */
	public boolean top() {
		return v < FACE_ROW;
	}

	/**
	 * Which half of a seat patch's art a leg wears, as a column across the art: 0 the art's left
	 * half, 1 its right half.
	 *
	 * <p><b>Which half.</b> Seat art is drawn as seen from behind — the only way anybody sees a
	 * seat. Standing behind the wearer you face the way they face, so their left leg is the one on
	 * <em>your</em> left: the art's left half belongs on the wearer's LEFT leg and its right half on
	 * their right.
	 *
	 * <p><b>Which leg is drawn mirrored.</b> The armour model draws the wearer's LEFT limbs as
	 * mirror images off the right limb's strips, so each of the three paths has to say two things —
	 * which half of the art a leg takes, and whether that half must be flipped in x to come out the
	 * way it was drawn:
	 * <ul>
	 *   <li><b>The pack path</b> (datagen, {@code patch/seat/<id>_r.png} and {@code _l.png}, what
	 *	   shows once {@link metacraft.ovvar.pack.Combos} has the combination): {@code _r} carries
	 *	   the art's right half as drawn, because the right leg is not mirrored; {@code _l} carries
	 *	   the art's left half <em>flipped in x</em>, because the model flips the left leg's texture
	 *	   back when it draws it.
	 *   <li><b>The instant path</b> (the dye bits and the shader's library, {@code ovvar.glsl}): the
	 *	   shader tells a left-leg fragment by the handedness of its texture mapping
	 *	   ({@code mirrored}). A mirrored fragment reads column {@code seatColumn(LEFT)} of the
	 *	   library art and mirrors {@code local.x} back; an unmirrored one reads column
	 *	   {@code seatColumn(RIGHT)} as it is. {@code OVVAR_SEAT_COLUMN_RIGHT} in the shader is this
	 *	   method's RIGHT value, and a game test holds the two to each other.
	 *   <li><b>The wardrobe preview</b> ({@code WardrobePreview.shownArt}): the doll lays the two
	 *	   legs out in the order the back view puts them and gives each the slice of the art at its
	 *	   own place across the figure — the same statement again, arrived at without reading this.
	 * </ul>
	 */
	public static int seatColumn(Side side) {
		return side == Side.LEFT ? 0 : 1;
	}

	/** Where a seat patch's art is cut for one leg: the x its half starts at, in art pixels. */
	public static int seatHalf(Side side) {
		return seatColumn(side) * PX;
	}

	/** The cells a seat patch covers, which a seat patch and a plain patch fight over. */
	public static final java.util.List<Spot> SEAT_CELLS = java.util.List.of(LEG_BACK_TOP_R, LEG_BACK_TOP_L);

	/** The cells of one half in a fixed order; a cell's index here is what the instant channel carries. */
	public static java.util.List<Spot> cells(Piece piece) {
		return java.util.Arrays.stream(values()).filter(s -> s.piece == piece).toList();
	}

	/**
	 * The cell nearest a strip position, or null if nothing of this side is in that column: the
	 * cells whose own columns hold {@code u} (a big cell spans two of the aim's 4-texel columns),
	 * the nearest of them down the face. Ties go to the earlier entry, which is how a small cell
	 * wins the rows it shares with the big one it lies inside.
	 *
	 * <p>{@code v} also says which kind of face was aimed at: a row above {@link #FACE_ROW} is the
	 * box's top face and only top cells are looked at, a row on the side rows only side cells. The
	 * shoulders share the arm's u 44..48 with the front sleeve cells, and the two faces are
	 * different faces of the box, so nearness down the face must not carry an aim from one to the
	 * other.
	 */
	public static Spot nearest(Piece piece, int u, double v, Side side) {
		Spot best = null;
		double bestDistance = Double.MAX_VALUE;
		boolean top = v < FACE_ROW;
		for (Spot s : values()) {
			if (s.piece != piece || s.side != side || s.top() != top || u < s.u || u >= s.u + s.width) continue;
			double d = v < s.v ? s.v - v : v >= s.v + s.height ? v - (s.v + s.height) + 1 : 0;
			if (d < bestDistance) { bestDistance = d; best = s; }
		}
		return best;
	}

	public String id() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	/**
	 * Cells this one's own rectangle lands on: {@link #BACK_BIG} over the two back-top cells, the
	 * seat over the two leg-back ones. Worked out from the rectangles rather than listed, so a cell
	 * added or resized above says it for itself. The seat's two legs count as the same side as each
	 * leg's own.
	 */
	public java.util.List<Spot> overlaps() {
		java.util.List<Spot> out = new java.util.ArrayList<>();
		for (Spot s : values()) {
			if (s == this || s.piece != piece || !sameSide(s.side, side)) continue;
			boolean acrossU = s.u < u + width && u < s.u + s.width;
			boolean acrossV = s.v < v + height && v < s.v + s.height;
			if (acrossU && acrossV) out.add(s);
		}
		return java.util.List.copyOf(out);
	}

	/**
	 * Cells that cannot be sewn while this one is: <b>only the seat's</b>. Overlapping patches are
	 * the point of an ovve — the big back cell and the two back-top cells are meant to be worn all
	 * three at once, drawn in {@link #layer} order, the way the back of a real ovve is built up.
	 *
	 * <p>The seat is the exception because it is not one patch on one cell: it is one patch cut in
	 * half across two cells of two different boxes, drawn as a single sprite on the seam between
	 * them ({@code StandDisplays}), so "one of them on top" has no answer there that all three
	 * paths could give. Sew the seat or the legs' back cells, not both.
	 */
	public java.util.List<Spot> overlapping() {
		java.util.List<Spot> out = new java.util.ArrayList<>();
		for (Spot s : overlaps()) if (s.side == Side.SEAT || side == Side.SEAT) out.add(s);
		return java.util.List.copyOf(out);
	}

	/**
	 * Where this cell's patch sits in the stack where cells overlap: the number of overlapping cells
	 * bigger than it, so a small patch is drawn <b>over</b> the big cell it lies inside and the big
	 * one is the background it was sewn to be. 0 for every cell that overlaps nothing.
	 *
	 * <p>Every path that draws a patch reads it and none of them may disagree: the pack stacks a
	 * half's layers in this order ({@code EquipmentJson.layerTextures}, which is also what the paper
	 * doll composites), the sprites on a stand are laid on in it, and the instant channel carries it
	 * in the cell table's own row (the ranked set the dye colour holds has no order of its own, so
	 * the shader takes the highest layer of the cells a fragment falls in).
	 */
	public int layer() {
		int under = 0;
		for (Spot s : overlaps()) if (s.width * s.height > width * height) under++;
		return under;
	}

	/** Placements bottom first: {@link #layer} order, and the order they came in within a layer. */
	public static java.util.List<Placement> stacked(java.util.List<Placement> placements) {
		java.util.List<Placement> out = new java.util.ArrayList<>(placements);
		out.sort(java.util.Comparator.comparingInt(p -> p.spot().layer()));
		return out;
	}

	/** Do two cells draw on the same part of the model? (The seat is on both legs, so on either side.) */
	private static boolean sameSide(Side a, Side b) {
		if (a == b) return true;
		return (a == Side.SEAT || b == Side.SEAT) && a != Side.BODY && b != Side.BODY;
	}

	/** "chest, top left" / "left sleeve, outer top" / "right shoulder" — for tooltips. */
	public String label() {
		if (this == SEAT) return "seat";
		if (this == BACK_BIG) return "back, all of it";
		String n = name().toLowerCase(java.util.Locale.ROOT);
		String limb = side == Side.LEFT ? "left " : side == Side.RIGHT ? "right " : "";
		if (n.startsWith("shoulder")) return limb + "shoulder";
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
