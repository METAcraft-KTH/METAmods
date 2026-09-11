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

	/** The visual offset of a cell's first column within its face, in art pixels from the face's left edge as seen. */
	public static int columnInFace(Spot spot) {
		int faceStart = Spot.stripStart(spot) + faceStartLocal(spot), n = faceTexels(spot);
		int fromStart = (spot.u - faceStart) * 2;
		return spot.side == Spot.Side.LEFT ? 2 * n - fromStart - Spot.PX : fromStart;   // the model mirrors the left limb
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

	public static List<Piece> of(Spot spot, Patches.Patch patch) {
		int w = patch.width(), h = patch.height();
		if (spot == Spot.SEAT) {
			// Half on each leg's back face; the halves are cut at the legs' inner corner, which the art never crosses.
			return List.of(new Piece(Where.FACE, 0, w / 2, 0, h, 0), new Piece(Where.FACE, w / 2, w, 0, h, 0));
		}
		if (!BEND_ROUND_CORNERS) return List.of(new Piece(Where.FACE, 0, w, 0, h, 0));
		double inflate = Spot.inflate(spot.piece), a = Spot.pixel(spot.u, inflate) / 2;   // sixteenths per art pixel
		int n = faceTexels(spot);
		double halfFace = (n + 2 * inflate) / 2, halfTop = (12 + 2 * inflate) / 2;
		int ax0 = columnInFace(spot) + (Spot.PX - w) / 2, ay0 = (spot.v - 20) * 2 + (Spot.PX - h) / 2;
		// Column c's left edge and row r's top edge, in sixteenths from the face's centre.
		int cL = 0, cR = w, rT = 0;
		for (int c = 0; c < w; c++) {
			double centre = (ax0 + c - n) * a + a / 2;
			if (centre < -halfFace) cL = c + 1;
			if (centre >= halfFace && cR == w) cR = c;
		}
		for (int r = 0; r < h; r++) if ((ay0 + r - 12) * a + a / 2 < -halfTop) rT = r + 1;
		List<Piece> out = new ArrayList<>();
		out.add(new Piece(Where.FACE, cL, cR, rT, h, 0));
		if (cR < w) out.add(new Piece(Where.RIGHT, cR, w, rT, h, Math.max(0, (ax0 + cR - n) * a - halfFace)));
		if (cL > 0) out.add(new Piece(Where.LEFT, 0, cL, rT, h, Math.max(0, -halfFace - (ax0 + cL - n) * a)));
		if (rT > 0) out.add(new Piece(Where.TOP, cL, cR, 0, rT, Math.max(0, -halfTop - (ay0 + rT - 12) * a)));
		return out;
	}
}
