package metacraft.ovvar.sewing;

import metacraft.ovvar.Ovvar;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.Patches;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The stitching dialog's sprite font, {@code ovvar:sewing}: every button in the dialog is drawn
 * by its label. Shared by datagen, which writes the font JSON and the glyph textures from
 * {@code art/ovvar/sewing}, and {@link SewingGame}, which strings the glyphs into labels.
 *
 * <p>How it works on the client. A button draws its background sprite, then its label centred
 * over it, so an opaque glyph the size of the button hides the button. A label wider than the
 * button minus {@value #LABEL_INSET} px a side is scrolled and clipped instead, so every label's
 * total advance is exactly that ({@link Label} keeps the books; negative-advance spaces move the
 * cursor anywhere in between). A full-cell glyph is {@value #OVERHANG} px larger than its
 * button on every side, so the cells of the {@value #COLS}×{@value #ROWS} grid meet across the
 * 2 px gaps and read as one piece of cloth. Buttons draw in grid order, so everything that lies
 * on the cloth — the patch, the stitches, the needle — is drawn by the last cell's label, at
 * offsets reaching back over the whole picture, and comes out on top. The label sits at y + 6 in
 * a 20 px button and a glyph's top at {@code textY + 7 - ascent}, so an ascent of
 * {@value #ROW_ASCENT} puts a glyph's top 1 px above the button; each glyph has one codepoint
 * per vertical position it can take, differing only in ascent. Text colour multiplies the glyph
 * and the shadow is turned off, so the art shows as drawn.
 */
public final class SewingFont {
    private SewingFont() {}

    public static final Identifier ID = Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, "sewing");
    /** Under {@code textures/}: {@code ovvar:font/sewing/<glyph>.png}. */
    public static final String TEXTURE_DIR = "font/sewing/";

    // ---- the dialog's geometry, in GUI px

    /** The picture is a grid of square buttons. */
    public static final int CELL = 20, GAP = 2, COLS = 7, ROWS = 7;
    /** One cell and its gap; also a cell glyph's size, which with the overhang covers the gap. */
    public static final int PITCH = CELL + GAP;
    /** The picture's size: what the cell glyphs cover between them. */
    public static final int PICTURE_WIDTH = COLS * PITCH, PICTURE_HEIGHT = ROWS * PITCH;
    /** The exit button spans the grid. */
    public static final int BAND = COLS * CELL + (COLS - 1) * GAP;
    /** The client's label inset: a label wider than {@code width - 2 * LABEL_INSET} scrolls and clips. */
    public static final int LABEL_INSET = 2;
    /** A full-cell glyph is this much larger than its button on every side. */
    public static final int OVERHANG = 1;
    /** Ascent of a glyph whose top is the top of its row, 1 px above its button. */
    private static final int ROW_ASCENT = 14;

    public static int cellWidth(int buttonWidth) {
        return buttonWidth + 2 * OVERHANG;
    }

    /** Picture coordinates as the last cell's label sees them: x relative to its left edge … */
    public static int overlayX(int pictureX) {
        return pictureX - (COLS - 1) * PITCH;
    }

    /** … and y relative to its top, both 1 px outside the button. */
    public static int overlayTop(int pictureY) {
        return pictureY - (ROWS - 1) * PITCH;
    }

    // ---- glyphs

    /**
     * One texture with one codepoint per vertical position ({@code top}, the glyph's top in the
     * label's row coordinates, where 0 is 1 px above the button) it can be drawn at.
     */
    public record Glyph(String name, int width, int height, int minTop, int maxTop, char first) {
        /** What the client adds to the cursor after drawing it: the texture's width plus one. */
        public int advance() {
            return width + 1;
        }

        public char at(int top) {
            if (top < minTop || top > maxTop) throw new IllegalArgumentException(name + " cannot sit at y " + top);
            return (char) (first + (top - minTop));
        }

        public int variants() {
            return maxTop - minTop + 1;
        }

        /** The bitmap provider's ascent for the variant whose top is at {@code top}. */
        public static int ascent(int top) {
            return ROW_ASCENT - top;
        }
    }

    private static final Map<String, Glyph> GLYPHS = new LinkedHashMap<>();
    private static char nextChar = '\uE000';

    private static Glyph glyph(String name, int width, int height, int minTop, int maxTop) {
        Glyph g = new Glyph(name, width, height, minTop, maxTop, nextChar);
        nextChar += (char) g.variants();
        if (nextChar >= SPACE_FIRST) throw new IllegalStateException("too many sewing glyphs");
        if (GLYPHS.put(name, g) != null) throw new IllegalStateException("duplicate sewing glyph " + name);
        return g;
    }

    /** An overlay of the given height, anywhere on the picture. */
    private static Glyph overlay(String name, int width, int height) {
        return glyph(name, width, height, overlayTop(0), overlayTop(PICTURE_HEIGHT - height));
    }

    /** Stitch marks: 7×7, centred on the hole. */
    public static final int MARK = 7;
    /** The needle: 26 long, 9 across, the point on the hole. */
    public static final int NEEDLE_LENGTH = 26, NEEDLE_WIDTH = 9;

    private static final Map<Chapter, Glyph> CLOTH = new LinkedHashMap<>();
    private static final Map<String, Glyph> PATCH = new LinkedHashMap<>();
    public static final Glyph CROSS, HOLE, NEEDLE_R, NEEDLE_L, NEEDLE_D, NEEDLE_U, BAND_GLYPH;

    static {
        for (Chapter chapter : Chapter.values()) CLOTH.put(chapter, glyph("cloth_" + chapter.id, PITCH, PITCH, 0, 0));
        for (Patches.Patch patch : Patches.all()) {
            int cells = patch.cells(), top = overlayTop(Seam.patchY(cells));
            PATCH.put(patch.id(), glyph("patch_" + patch.id(), Seam.patchWidth(cells), Seam.patchHeight(cells), top, top));
        }
        CROSS = overlay("cross", MARK, MARK);
        HOLE = overlay("hole", MARK, MARK);
        NEEDLE_R = overlay("needle_r", NEEDLE_LENGTH, NEEDLE_WIDTH);   // pointing right: the needle comes from the left
        NEEDLE_L = overlay("needle_l", NEEDLE_LENGTH, NEEDLE_WIDTH);
        NEEDLE_D = overlay("needle_d", NEEDLE_WIDTH, NEEDLE_LENGTH);   // pointing down: from above
        NEEDLE_U = overlay("needle_u", NEEDLE_WIDTH, NEEDLE_LENGTH);
        BAND_GLYPH = glyph("band", cellWidth(BAND), PITCH, 0, 0);
    }

    public static Collection<Glyph> glyphs() {
        return Collections.unmodifiableCollection(GLYPHS.values());
    }

    public static Glyph cloth(Chapter chapter) {
        return CLOTH.get(chapter);
    }

    /** The patch's art, scaled, as one glyph. */
    public static Glyph patch(Patches.Patch patch) {
        return PATCH.get(patch.id());
    }

    // ---- spaces: advances of ±1, ±2, … ±128, so any move up to 255 px is a few codepoints

    private static final char SPACE_FIRST = '\uE800';
    private static final int SPACE_BITS = 8;

    /** Codepoint → advance, for the font's {@code space} provider. */
    public static Map<Character, Integer> spaceAdvances() {
        Map<Character, Integer> out = new LinkedHashMap<>();
        for (int bit = 0; bit < SPACE_BITS; bit++) {
            out.put((char) (SPACE_FIRST + bit), 1 << bit);
            out.put((char) (SPACE_FIRST + SPACE_BITS + bit), -(1 << bit));
        }
        return out;
    }

    /** Spaces that move the cursor {@code dx} px (either way). */
    public static String move(int dx) {
        int size = Math.abs(dx);
        if (size >= 1 << SPACE_BITS) throw new IllegalArgumentException("move of " + dx + " px is too far");
        StringBuilder out = new StringBuilder();
        for (int bit = 0; bit < SPACE_BITS; bit++) {
            if ((size >> bit & 1) != 0) out.append((char) (SPACE_FIRST + (dx < 0 ? SPACE_BITS : 0) + bit));
        }
        return out.toString();
    }

    // ---- labels

    public static final Style STYLE = Style.EMPTY.withFont(new FontDescription.Resource(ID))
            .withColor(ChatFormatting.WHITE).withShadowColor(0).withItalic(false).withBold(false);

    /**
     * A button's label, built in row coordinates: x = 0 is 1 px left of the button, y = 0 is 1 px
     * above it. Glyphs are laid down in order, later ones over earlier; the finished label's total
     * advance is the button's width minus the insets, so the client centres it with a full-cell
     * glyph exactly over the button.
     */
    public static final class Label {
        private final int buttonWidth;
        private final StringBuilder text = new StringBuilder();
        /** Where the cursor is; the client starts the label at {@code button.x + LABEL_INSET}. */
        private int pos = LABEL_INSET + OVERHANG;

        public Label(int buttonWidth) {
            this.buttonWidth = buttonWidth;
        }

        /** A glyph covering the whole button. */
        public Label cell(Glyph glyph) {
            if (glyph.width != cellWidth(buttonWidth)) throw new IllegalArgumentException(glyph.name + " is not a " + buttonWidth + " px cell");
            return at(glyph, 0, 0);
        }

        public Label at(Glyph glyph, int x, int top) {
            text.append(move(x - pos)).append(glyph.at(top));
            pos = x + glyph.advance();
            return this;
        }

        public String build() {
            int end = LABEL_INSET + OVERHANG + buttonWidth - 2 * LABEL_INSET;
            return text.toString() + move(end - pos);
        }

        public Component component() {
            return Component.literal(build()).withStyle(STYLE);
        }
    }

    /** What the client will measure a label as: the sum of its advances. */
    public static int width(String label) {
        int width = 0;
        for (char c : label.toCharArray()) width += advance(c);
        return width;
    }

    /** The leftmost and rightmost px a label draws on, in row coordinates, as {left, right}. */
    public static int[] extent(String label) {
        int pos = LABEL_INSET + OVERHANG, left = Integer.MAX_VALUE, right = Integer.MIN_VALUE;
        for (char c : label.toCharArray()) {
            Glyph g = glyphAt(c);
            if (g != null) {
                left = Math.min(left, pos);
                right = Math.max(right, pos + g.width);
            }
            pos += advance(c);
        }
        return new int[]{left, right};
    }

    private static int advance(char c) {
        if (c >= SPACE_FIRST && c < SPACE_FIRST + 2 * SPACE_BITS) {
            int bit = (c - SPACE_FIRST) % SPACE_BITS;
            return c - SPACE_FIRST < SPACE_BITS ? 1 << bit : -(1 << bit);
        }
        Glyph g = glyphAt(c);
        if (g == null) throw new IllegalArgumentException("not a sewing glyph: U+" + Integer.toHexString(c));
        return g.advance();
    }

    private static Glyph glyphAt(char c) {
        for (Glyph g : GLYPHS.values()) {
            if (c >= g.first && c < g.first + g.variants()) return g;
        }
        return null;
    }
}
