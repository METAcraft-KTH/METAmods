package metacraft.ovvar.content;

import java.util.ArrayList;
import java.util.List;

/**
 * How a patch's art is cut up when it is shown as flat display entities on an armour stand
 * ({@link metacraft.ovvar.sewing.StandDisplays}): the columns that fall past the corner of the
 * cell's face go on the neighbouring face, the rows above the top of the part go on its top face
 * (a patch lapping over the shoulder), each as its own sprite. The cut is where the box's corner
 * is, with the art at the fabric's square pixel ({@link Spot#pixel}) centred on the face's texels
 * the way the shader draws it — a column whose centre is past the corner goes round it.
 *
 * All in the art as seen (a left limb's art is not mirrored here — the displays show it as it
 * is), columns and rows in art pixels, [from, to).
 */
public final class PatchPieces {
	private PatchPieces() {}

	/**
	 * Bend a patch round the corners of its face, or lay the whole art flat on the cell's face and
	 * let the overhang stick out past the corner? Off for now: the cut pieces do not line up well
	 * enough with the armour, worse the further a stand's limbs are posed from rest, and two
	 * patches meeting round a sleeve's corner look broken.
	 */
	public static final boolean BEND_ROUND_CORNERS = false;

	/** Which plane a piece lies on, relative to the cell's face. */
	public enum Where { FACE, RIGHT, LEFT, TOP }

	/**
	 * @param start where the piece's first column (RIGHT), last column (LEFT) or last row (TOP)
	 *			  sits along the neighbouring plane, in sixteenths from the corner — a column
	 *			  straddling the corner is pulled up to it, so this is 0 or a little more
	 */
	public record Piece(Where where, int x0, int x1, int y0, int y1, double start) {
		public String key() {
			return x0 + "_" + x1 + "_" + y0 + "_" + y1;
		}
	}

	/**
	 * The visual offset of a cell's first column within its face, in art pixels from the face's left
	 * edge as seen. A cell on the box's top face is the whole of that face, and the top face is the
	 * strip's second block of four columns — the same columns as the front face — so the same
	 * arithmetic gives it 0.
	 */
	public static int columnInFace(Spot spot) {
		int faceStart = Spot.stripStart(spot) + faceStartLocal(spot), n = faceTexels(spot);
		int fromStart = (spot.u - faceStart) * Spot.ART_DETAIL;
		return spot.side == Spot.Side.LEFT ? Spot.ART_DETAIL * n - fromStart - spot.artPx() : fromStart;   // the model mirrors the left limb
	}

	private static int faceStartLocal(Spot spot) {
		int local = spot.u - Spot.stripStart(spot), n1 = Spot.stripWidth(spot) == 24 ? 8 : 4;
		return local < 4 ? 0 : local < 4 + n1 ? 4 : local < 8 + n1 ? 4 + n1 : 8 + n1;
	}

	/** The cell's face's width in skin texels. */
	public static int faceTexels(Spot spot) {
		int local = spot.u - Spot.stripStart(spot), n1 = Spot.stripWidth(spot) == 24 ? 8 : 4;
		return local < 4 ? 4 : local < 4 + n1 ? n1 : local < 8 + n1 ? 4 : n1;
	}

	/** @param art the PNG the cell shows, which is not always the catalogue's own size ({@link Patches#artFor}) */
	public static List<Piece> of(Spot spot, Patches.Art art) {
		int w = art.width(), h = art.height();
		// Flat: the whole art as one sprite on the cell's face (the seat's across both legs, on the seam).
		if (!BEND_ROUND_CORNERS) return List.of(new Piece(Where.FACE, 0, w, 0, h, 0));
		// A cell on the box's top face (the shoulders) is never cut: the four edges of a top face have
		// no neighbouring face in the layout to continue onto, so the sewn patch is clipped to the face
		// (GeneratedAssets.placed) and the sprite is the whole art lying flat on it, as it is.
		if (spot.top()) return List.of(new Piece(Where.FACE, 0, w, 0, h, 0));
		if (spot == Spot.SEAT) {
			// Half on each leg's back face; the halves are cut at the legs' inner corner, which the art never crosses.
			return List.of(new Piece(Where.FACE, 0, w / 2, 0, h, 0), new Piece(Where.FACE, w / 2, w, 0, h, 0));
		}
		double inflate = Spot.inflate(spot.piece), a = Spot.pixel(spot.u, inflate) / Spot.ART_DETAIL;   // sixteenths per art pixel
		int n = faceTexels(spot);
		double halfFace = (n + 2 * inflate) / 2, halfTop = (Spot.FACE_ROWS + 2 * inflate) / 2;
		int ax0 = columnInFace(spot) + art.artOffsetX(spot), ay0 = (spot.v - Spot.FACE_ROW) * Spot.ART_DETAIL + art.artOffsetY(spot);
		// Column c's left edge and row r's top edge, in sixteenths from the face's centre.
		int cL = 0, cR = w, rT = 0;
		for (int c = 0; c < w; c++) {
			double centre = (ax0 + c - n) * a + a / 2;
			if (centre < -halfFace) cL = c + 1;
			if (centre >= halfFace && cR == w) cR = c;
		}
		int halfRows = Spot.FACE_ROWS * Spot.ART_DETAIL / 2;   // art pixels from the top of the side rows to their middle
		for (int r = 0; r < h; r++) if ((ay0 + r - halfRows) * a + a / 2 < -halfTop) rT = r + 1;
		List<Piece> out = new ArrayList<>();
		out.add(new Piece(Where.FACE, cL, cR, rT, h, 0));
		if (cR < w) out.add(new Piece(Where.RIGHT, cR, w, rT, h, Math.max(0, (ax0 + cR - n) * a - halfFace)));
		if (cL > 0) out.add(new Piece(Where.LEFT, 0, cL, rT, h, Math.max(0, -halfFace - (ax0 + cL - n) * a)));
		if (rT > 0) out.add(new Piece(Where.TOP, cL, cR, 0, rT, Math.max(0, -halfTop - (ay0 + rT - halfRows) * a)));
		return out;
	}
}
