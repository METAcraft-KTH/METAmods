package metacraft.ovvar.sewing;

import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Spot;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

import static metacraft.ovvar.sewing.SewingFont.PICTURE_HEIGHT;
import static metacraft.ovvar.sewing.SewingFont.PICTURE_WIDTH;
import static metacraft.ovvar.sewing.SewingFont.PITCH;

/**
 * The seam of {@code stitches} holes around a patch, in picture px: the patch's art scaled up and
 * centred on the cloth, and the holes spread evenly along its {@link Outline}, going clockwise
 * from the top left, alternately just outside the edge and just inside it — the needle goes over
 * the edge and back like a whip stitch, each stitch a little further along. The needle always
 * comes in from outside the patch, from whichever side the edge faces.
 *
 * @param patch    what is being sewn on
 * @param stitches how many holes the seam has
 */
public record Seam(Patches.Patch patch, int stitches) {
    /** Art px per texel: a 4×4 patch at 80 px, an 8×4 seat patch at 96×48. */
    public static final int PLAIN_SCALE = 20, SEAT_SCALE = 12;
    /** How far outside and inside the edge the holes sit. */
    private static final int OUT = 4, IN = 4;

    public static int scale(int cells) {
        return cells == 1 ? PLAIN_SCALE : SEAT_SCALE;
    }

    public static int patchWidth(int cells) {
        return Spot.SIZE * cells * scale(cells);
    }

    public static int patchHeight(int cells) {
        return Spot.SIZE * scale(cells);
    }

    /** The patch's top-left on the picture: centred. */
    public static int patchX(int cells) {
        return (PICTURE_WIDTH - patchWidth(cells)) / 2;
    }

    public static int patchY(int cells) {
        return (PICTURE_HEIGHT - patchHeight(cells)) / 2;
    }

    /** Where the needle comes from: the side of the edge that faces outward. */
    public enum From { LEFT, RIGHT, ABOVE, BELOW }

    /**
     * One hole: its centre on the picture and which cell of the grid it is in.
     *
     * @param outside whether it is on the cloth just outside the patch (else on the patch just inside its edge)
     */
    public record Hole(int x, int y, boolean outside, From from) {
        public int col() {
            return x / PITCH;
        }

        public int row() {
            return y / PITCH;
        }
    }

    public List<Hole> holes() {
        Outline outline = Outline.of(patch.id());
        int cells = patch.cells(), scale = scale(cells), x0 = patchX(cells), y0 = patchY(cells);
        double length = outline.length();
        List<Hole> holes = new ArrayList<>(stitches);
        for (int i = 0; i < stitches; i++) {
            Outline.Point p = outline.at(length * (i + 0.5) / stitches);
            boolean outside = i % 2 == 0;
            int shift = outside ? OUT : -IN;
            int x = (int) Math.round(x0 + p.x() * scale + p.nx() * shift);
            int y = (int) Math.round(y0 + p.y() * scale + p.ny() * shift);
            From from = Math.abs(p.nx()) >= Math.abs(p.ny()) ? (p.nx() < 0 ? From.LEFT : From.RIGHT) : (p.ny() < 0 ? From.ABOVE : From.BELOW);
            int margin = SewingFont.MARK / 2;
            holes.add(new Hole(Mth.clamp(x, margin, PICTURE_WIDTH - 1 - margin), Mth.clamp(y, margin, PICTURE_HEIGHT - 1 - margin), outside, from));
        }
        return holes;
    }
}
