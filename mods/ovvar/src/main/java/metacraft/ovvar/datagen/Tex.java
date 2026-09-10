package metacraft.ovvar.datagen;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/** ARGB texture with the few operations the generator needs. Immutable; every op returns a copy. */
final class Tex {
    final int width, height;
    private final int[] argb;

    private Tex(int width, int height, int[] argb) {
        this.width = width;
        this.height = height;
        this.argb = argb;
    }

    static Tex blank(int width, int height) {
        return new Tex(width, height, new int[width * height]);
    }

    static Tex read(InputStream in) {
        try (in) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) throw new IOException("not an image");
            int w = img.getWidth(), h = img.getHeight();
            int bands = img.getRaster().getNumBands();
            int[] px;
            if (bands <= 2 && img.getColorModel().getColorSpace().getType() == java.awt.color.ColorSpace.TYPE_GRAY) {
                // Greyscale PNGs: getRGB would run the samples through ImageIO's linear grey colour
                // space and shift them; take the raw 8-bit samples like every other PNG decoder does.
                px = new int[w * h];
                int[] s = new int[bands];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        img.getRaster().getPixel(x, y, s);
                        int v = s[0], a = bands == 2 ? s[1] : 255;
                        px[y * w + x] = (a << 24) | (v << 16) | (v << 8) | v;
                    }
                }
            } else {
                px = img.getRGB(0, 0, w, h, null, 0, w);
            }
            return new Tex(w, h, px);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    byte[] png() {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, width, height, argb, 0, width);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    static int a(int p) { return (p >>> 24) & 0xFF; }
    static int r(int p) { return (p >>> 16) & 0xFF; }
    static int g(int p) { return (p >>> 8) & 0xFF; }
    static int b(int p) { return p & 0xFF; }
    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
    private static int pack(int a, int r, int g, int b) { return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b); }

    int get(int x, int y) {
        return argb[y * width + x];
    }

    /** Pixels of {@code src} at (sx, sy, w, h) copied onto a copy of this at (dx, dy); source alpha replaces, no blending. */
    Tex blit(Tex src, int sx, int sy, int w, int h, int dx, int dy) {
        if (sx + w > src.width || sy + h > src.height || dx + w > width || dy + h > height) {
            throw new IllegalArgumentException("blit outside bounds: " + w + "x" + h + " from (" + sx + "," + sy + ") to (" + dx + "," + dy + ")");
        }
        int[] out = argb.clone();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = src.argb[(sy + y) * src.width + sx + x];
                if (a(p) > 0) out[(dy + y) * width + dx + x] = p;
            }
        }
        return new Tex(width, height, out);
    }

    /** Copy with every fully opaque texel's alpha set to {@code alpha} (a tag the shader can read). */
    Tex tagOpaque(int alpha) {
        int[] out = this.argb.clone();
        for (int i = 0; i < out.length; i++) if (a(out[i]) == 255) out[i] = pack(alpha, r(out[i]), g(out[i]), b(out[i]));
        return new Tex(width, height, out);
    }

    /** Copy with one texel replaced. */
    Tex with(int x, int y, int argb) {
        int[] out = this.argb.clone();
        out[y * width + x] = argb;
        return new Tex(width, height, out);
    }

    /** Copy with the rectangle (x, y, w, h) mirrored horizontally in place. */
    Tex flipX(int x, int y, int w, int h) {
        int[] out = argb.clone();
        for (int yy = y; yy < y + h; yy++) {
            for (int i = 0; i < w; i++) out[yy * width + x + i] = argb[yy * width + x + (w - 1 - i)];
        }
        return new Tex(width, height, out);
    }

    /** Copy with the whole image mirrored horizontally. */
    Tex flipX() {
        return flipX(0, 0, width, height);
    }

    /** Copy mirrored vertically. */
    Tex flipY() {
        int[] out = new int[argb.length];
        for (int y = 0; y < height; y++) System.arraycopy(argb, (height - 1 - y) * width, out, y * width, width);
        return new Tex(width, height, out);
    }

    /** Copy turned a quarter turn clockwise (something pointing right comes to point down). */
    Tex rotated() {
        int[] out = new int[argb.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) out[x * height + (height - 1 - y)] = argb[y * width + x];
        }
        return new Tex(height, width, out);
    }

    /** {@code over} alpha-composited on top of this (same size). */
    Tex composite(Tex over) {
        if (over.width != width || over.height != height) throw new IllegalArgumentException("size mismatch");
        int[] out = new int[argb.length];
        for (int i = 0; i < argb.length; i++) {
            int base = argb[i], top = over.argb[i];
            double ta = a(top) / 255.0, ba = a(base) / 255.0;
            double oa = ta + ba * (1 - ta);
            if (oa == 0) { out[i] = 0; continue; }
            int rr = (int) Math.round((r(top) * ta + r(base) * ba * (1 - ta)) / oa);
            int gg = (int) Math.round((g(top) * ta + g(base) * ba * (1 - ta)) / oa);
            int bb = (int) Math.round((b(top) * ta + b(base) * ba * (1 - ta)) / oa);
            out[i] = pack((int) Math.round(oa * 255), rr, gg, bb);
        }
        return new Tex(width, height, out);
    }

    /**
     * The website overlays mark "erase the skin here" with pure green. Armour has nothing to erase,
     * so those pixels become transparent. Matched loosely: a colour-managed PNG can decode 00FF00 as 01FE00.
     */
    Tex withoutGreenKey() {
        int[] out = new int[argb.length];
        for (int i = 0; i < argb.length; i++) {
            int p = argb[i];
            boolean key = r(p) < 32 && g(p) > 223 && b(p) < 32 && a(p) > 127;
            out[i] = key ? 0 : p;
        }
        return new Tex(width, height, out);
    }

    /** Nearest-neighbour upscale by an integer factor. */
    Tex scale(int factor) {
        int[] out = new int[argb.length * factor * factor];
        int w = width * factor;
        for (int y = 0; y < height * factor; y++) {
            for (int x = 0; x < w; x++) out[y * w + x] = argb[(y / factor) * width + x / factor];
        }
        return new Tex(w, height * factor, out);
    }

    /**
     * Replace the hue/saturation of every visible pixel with {@code target}'s, keeping this pixel's
     * relative lightness. Turns the purple IT ovve into the silicon-blue one without repainting it.
     */
    Tex tinted(int target) {
        float[] t = java.awt.Color.RGBtoHSB(r(target), g(target), b(target), null);
        int[] out = new int[argb.length];
        for (int i = 0; i < argb.length; i++) {
            int p = argb[i];
            if (a(p) == 0) continue;
            float[] hsb = java.awt.Color.RGBtoHSB(r(p), g(p), b(p), null);
            int rgb = java.awt.Color.HSBtoRGB(t[0], t[1] * hsb[1] / Math.max(0.01f, sat(target)), hsb[2]);
            out[i] = (a(p) << 24) | (rgb & 0xFFFFFF);
        }
        return new Tex(width, height, out);
    }

    private static float sat(int p) {
        return java.awt.Color.RGBtoHSB(r(p), g(p), b(p), null)[1];
    }

    /** Copy with every texel's HSB brightness multiplied by {@code factor} (clamped). */
    Tex brightened(float factor) {
        int[] out = new int[argb.length];
        for (int i = 0; i < argb.length; i++) {
            int p = argb[i];
            if (a(p) == 0) continue;
            float[] hsb = java.awt.Color.RGBtoHSB(r(p), g(p), b(p), null);
            int rgb = java.awt.Color.HSBtoRGB(hsb[0], hsb[1], Math.min(1.0f, hsb[2] * factor));
            out[i] = (a(p) << 24) | (rgb & 0xFFFFFF);
        }
        return new Tex(width, height, out);
    }

    /** HSB brightness of a colour, 0–1. */
    static float brightness(int rgb) {
        return java.awt.Color.RGBtoHSB(r(rgb), g(rgb), b(rgb), null)[2];
    }

    /** Every distinct fully opaque colour (ARGB, alpha 255), in first-seen order. */
    List<Integer> opaqueColours() {
        List<Integer> out = new ArrayList<>();
        for (int p : argb) if (a(p) == 255 && !out.contains(p)) out.add(p);
        return out;
    }

    /** {@code a} mixed towards {@code b} by {@code t} (0 = a, 1 = b), alpha from {@code a}. */
    static int mix(int a, int b, double t) {
        return pack(a(a), (int) Math.round(r(a) + (r(b) - r(a)) * t), (int) Math.round(g(a) + (g(b) - g(a)) * t), (int) Math.round(b(a) + (b(b) - b(a)) * t));
    }

    /** Most frequent opaque colour, for deriving a chapter's colour from its overlay. */
    int dominant() {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int p : argb) if (a(p) == 255) counts.merge(p & 0xFFFFFF, 1, Integer::sum);
        return counts.entrySet().stream().max(Map.Entry.comparingByValue())
                .orElseThrow(() -> new IllegalStateException("no opaque pixels")).getKey();
    }

    boolean isEmpty() {
        return Arrays.stream(argb).allMatch(p -> a(p) == 0);
    }

    /** The rectangle (x, y, w, h) as its own texture. */
    Tex crop(int x, int y, int w, int h) {
        return blank(w, h).blit(this, x, y, w, h, 0, 0);
    }

    /**
     * Every visible pixel takes {@code target}'s hue and saturation and keeps its own brightness:
     * one piece of cloth in every chapter's colour. (Unlike {@link #tinted}, which scales
     * saturation, this works on grey art too.)
     */
    Tex colourised(int target) {
        float[] t = java.awt.Color.RGBtoHSB(r(target), g(target), b(target), null);
        int[] out = new int[argb.length];
        for (int i = 0; i < argb.length; i++) {
            int p = argb[i];
            if (a(p) == 0) continue;
            float[] hsb = java.awt.Color.RGBtoHSB(r(p), g(p), b(p), null);
            out[i] = (a(p) << 24) | (java.awt.Color.HSBtoRGB(t[0], t[1], hsb[2]) & 0xFFFFFF);
        }
        return new Tex(width, height, out);
    }

    /** Copy with a horizontal line of {@code argb} from (x, y), {@code length} px long. */
    Tex line(int x, int y, int length, int argb) {
        int[] out = this.argb.clone();
        for (int i = 0; i < length; i++) out[y * width + x + i] = argb;
        return new Tex(width, height, out);
    }

    /**
     * A font glyph's advance is measured to its rightmost visible column, so a glyph must reach its
     * right edge for the label arithmetic in SewingFont to hold. Copy with the bottom-right pixel
     * made just barely visible if the last column is empty.
     */
    Tex reachingRightEdge() {
        for (int y = 0; y < height; y++) if (a(get(width - 1, y)) != 0) return this;
        return with(width - 1, height - 1, 0x01000000);
    }

    /**
     * {@code text} stamped in the vanilla font ({@code ascii.png}: 16×16 cells of 8×8, cell = code
     * point) at (x, y) in {@code argb}, with the usual shadow. Only ASCII; the width is what the
     * client would measure. Returns the copy and its width via {@code widthOut[0]}.
     */
    Tex stampText(Tex ascii, String text, int x, int y, int argb) {
        Tex out = this;
        int shadow = 0xFF000000 | ((r(argb) / 4) << 16) | ((g(argb) / 4) << 8) | (b(argb) / 4);
        for (int pass = 0; pass < 2; pass++) {
            int cx = x + (pass == 0 ? 1 : 0), cy = y + (pass == 0 ? 1 : 0);
            int colour = pass == 0 ? shadow : argb;
            for (char c : text.toCharArray()) {
                if (c == ' ') { cx += 4; continue; }
                if (c < 0x20 || c > 0x7E) throw new IllegalArgumentException("stampText is ASCII only: " + text);
                int gx = (c % 16) * 8, gy = (c / 16) * 8, glyphWidth = 0;
                for (int yy = 0; yy < 8; yy++) {
                    for (int xx = 0; xx < 8; xx++) {
                        if (a(ascii.get(gx + xx, gy + yy)) == 0) continue;
                        glyphWidth = Math.max(glyphWidth, xx + 1);
                        if (cx + xx < width && cy + yy < height) out = out.with(cx + xx, cy + yy, colour);
                    }
                }
                cx += glyphWidth + 1;
            }
        }
        return out;
    }

    /** What {@link #stampText} advances by: the vanilla widths of {@code text}. */
    static int textWidth(Tex ascii, String text) {
        int w = 0;
        for (char c : text.toCharArray()) {
            if (c == ' ') { w += 4; continue; }
            int gx = (c % 16) * 8, gy = (c / 16) * 8, glyphWidth = 0;
            for (int yy = 0; yy < 8; yy++) for (int xx = 0; xx < 8; xx++) if (a(ascii.get(gx + xx, gy + yy)) != 0) glyphWidth = Math.max(glyphWidth, xx + 1);
            w += glyphWidth + 1;
        }
        return w;
    }
}
