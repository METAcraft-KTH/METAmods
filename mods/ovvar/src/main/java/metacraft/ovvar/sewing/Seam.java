package metacraft.ovvar.sewing;

import metacraft.ovvar.content.Patches;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

import static metacraft.ovvar.sewing.SewingFont.PICTURE_HEIGHT;
import static metacraft.ovvar.sewing.SewingFont.PICTURE_WIDTH;
import static metacraft.ovvar.sewing.SewingFont.PITCH;

/**
 * The seam around a patch, in picture px: the patch's art scaled up and centred on the cloth, and
 * {@code stitches} holes along its {@link Outline}, clockwise from the top left. The holes come in
 * pairs — one just outside the edge on the cloth, where the thread comes up, and one just inside
 * it on the patch, where it goes down again — so the thread crosses the edge and back like a whip
 * stitch, and runs on to the next pair underneath. The needle always comes in from outside the
 * patch, from whichever side the edge faces.
 *
 * @param patch    what is being sewn on
 * @param stitches how many holes the seam has (two per visible stitch)
 */
public record Seam(Patches.Patch patch, int stitches) {
    /** How the seam looks: how far apart the two holes of a stitch are, and how the runs between stitches are drawn. */
    public enum Style {
        /** Hand-sewn: steep bars over the edge, the runs between them under the cloth (drawn dashed). */
        WHIP,
        /** Machine-sewn: the thread zigzags across the edge, every run on top. */
        ZIGZAG
    }

    public static final Style STYLE = Style.WHIP;

    /** The patch's art is scaled up whole to fit this many px in either direction: an 8×8 at 96 px, a 16×8 seat at 96×48, a 12×12 at 96. */
    public static final int PATCH_FIT = 96;
    /** How far outside and inside the edge the holes sit. */
    private static final int OUT = 4, IN = 4;
    /** A whip stitch's two holes are this far, along the edge, from the stitch's centre. */
    private static final int WHIP_HALF = 2;

    /** Picture px per art px: whole, the largest that fits {@link #PATCH_FIT}. */
    public static int scale(Patches.Patch patch) {
        return Math.max(1, Math.min(PATCH_FIT / patch.width(), PATCH_FIT / patch.height()));
    }

    public static int patchWidth(Patches.Patch patch) {
        return patch.width() * scale(patch);
    }

    public static int patchHeight(Patches.Patch patch) {
        return patch.height() * scale(patch);
    }

    /** The patch's top-left on the picture: centred. */
    public static int patchX(Patches.Patch patch) {
        return (PICTURE_WIDTH - patchWidth(patch)) / 2;
    }

    public static int patchY(Patches.Patch patch) {
        return (PICTURE_HEIGHT - patchHeight(patch)) / 2;
    }

    /** Where the needle comes from: the side of the edge that faces outward. */
    public enum From { LEFT, RIGHT, ABOVE, BELOW }

    /**
     * One hole: its centre on the picture and which cell of the grid it is in.
     *
     * @param outside whether it is on the cloth just outside the patch (else on the patch just inside its edge)
     * @param along   how far along the outline it sits, in outline units (texels)
     */
    public record Hole(int x, int y, boolean outside, From from, double along) {
        public int col() {
            return x / PITCH;
        }

        public int row() {
            return y / PITCH;
        }
    }

    public List<Hole> holes() {
        Outline outline = Outline.of(patch.id());
        int scale = scale(patch), x0 = patchX(patch), y0 = patchY(patch);
        int pairs = (stitches + 1) / 2;
        double pitch = outline.length() / pairs;
        double half = STYLE == Style.ZIGZAG ? pitch / 4 : Math.min(pitch / 4, (double) WHIP_HALF / scale);
        List<Hole> holes = new ArrayList<>(stitches);
        for (int i = 0; i < stitches; i++) {
            boolean outside = i % 2 == 0;
            double along = pitch * (i / 2 + 0.5) + (outside ? -half : half);
            Outline.Point p = outline.at(along);
            int shift = outside ? OUT : -IN;
            int x = (int) Math.round(x0 + p.x() * scale + p.nx() * shift);
            int y = (int) Math.round(y0 + p.y() * scale + p.ny() * shift);
            From from = Math.abs(p.nx()) >= Math.abs(p.ny()) ? (p.nx() < 0 ? From.LEFT : From.RIGHT) : (p.ny() < 0 ? From.ABOVE : From.BELOW);
            // Marks stay on the picture, and off its last two rows, where a mark's glyph would need a negative ascent.
            int margin = SewingFont.MARK / 2;
            holes.add(new Hole(Mth.clamp(x, margin, PICTURE_WIDTH - 1 - margin), Mth.clamp(y, margin, PICTURE_HEIGHT - 2 - margin), outside, from, along));
        }
        return holes;
    }

    /**
     * The thread's way from one hole to the next when it runs along the edge (under the cloth,
     * or on top in a zigzag): from the hole to the edge, along the outline — round corners, into
     * the notch of a heart — and out to the next hole. Picture points about {@code step} px apart.
     */
    public List<int[]> alongTheEdge(Hole from, Hole to, int step) {
        Outline outline = Outline.of(patch.id());
        int scale = scale(patch), x0 = patchX(patch), y0 = patchY(patch);
        double start = from.along(), end = to.along();
        if (end < start) end += outline.length();
        List<int[]> points = new ArrayList<>();
        int[] a = edgePoint(outline, start, scale, x0, y0), b = edgePoint(outline, end, scale, x0, y0);
        line(points, from.x(), from.y(), a[0], a[1], step);
        double stepAlong = (double) step / scale;
        for (double d = start + stepAlong; d < end; d += stepAlong) points.add(edgePoint(outline, d, scale, x0, y0));
        line(points, b[0], b[1], to.x(), to.y(), step);
        return points;
    }

    private static int[] edgePoint(Outline outline, double along, int scale, int x0, int y0) {
        Outline.Point p = outline.at(along);
        return new int[]{(int) Math.round(x0 + p.x() * scale), (int) Math.round(y0 + p.y() * scale)};
    }

    /** Points about {@code step} px apart from (x0, y0) to (x1, y1), both ends included. */
    public static void line(List<int[]> points, int x0, int y0, int x1, int y1, int step) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int s = 0; s <= steps; s += step) {
            points.add(new int[]{Math.round(x0 + (x1 - x0) * s / (float) Math.max(1, steps)), Math.round(y0 + (y1 - y0) * s / (float) Math.max(1, steps))});
        }
    }
}
