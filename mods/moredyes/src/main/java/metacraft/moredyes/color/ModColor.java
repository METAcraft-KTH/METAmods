package metacraft.moredyes.color;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.material.MapColor;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One colour from colors.json. Everything colour-specific in the mod derives from an instance of
 * this; no code path is allowed to know the colour ids.
 *
 * @param id        registry-safe id, e.g. {@code cerise}; used as the prefix of every block/item id
 * @param name      display name, e.g. {@code Cerise}
 * @param rgb       the colour itself, {@code 0xRRGGBB}; used wherever vanilla renders an arbitrary
 *                  RGB (dyed leather, fireworks, display-entity tints)
 * @param rampDark  darkest stop of the texture ramp (the texture script uses it, we keep it for
 *                  runtime-generated textures later)
 * @param rampLight lightest stop of the texture ramp
 * @param mapColor  nearest vanilla map colour by RGB distance — the one approximation the client's
 *                  fixed 64-entry map palette forces on us
 */
public record ModColor(String id, String name, int rgb, int rampDark, int rampLight, MapColor mapColor) {

    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");

    public static final Codec<ModColor> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.STRING.fieldOf("id").validate(
                            id -> ID.matcher(id).matches() ? DataResult.success(id) : DataResult.error(() -> "id '" + id + "' must match [a-z0-9_]+")
                    ).forGetter(ModColor::id),
                    Codec.STRING.fieldOf("name").forGetter(ModColor::name),
                    ExtraCodecs.STRING_RGB_COLOR.fieldOf("rgb").forGetter(ModColor::rgb),
                    Ramp.CODEC.optionalFieldOf("ramp").forGetter(colour -> Optional.of(new Ramp(colour.rampDark(), colour.rampLight())))
            ).apply(instance, ModColor::of)
    );

    public record Ramp(int rampDark, int rampLight) {
        public static final Codec<Ramp> CODEC = ExtraCodecs.STRING_RGB_COLOR.listOf().comapFlatMap(
                colours -> colours.size() == 2 ?
                        DataResult.success(new Ramp(colours.get(0), colours.get(1))) :
                        DataResult.error(() -> "\"ramp\" must be [dark, light]"),
                ramp -> List.of(ramp.rampDark, ramp.rampLight)
        );
    }

    public static ModColor of(String id, String name, int rgb, Optional<Ramp> ramp) {
        return of(
                id, name, rgb,
                ramp.map(Ramp::rampDark).orElseGet(() -> deriveRamp(rgb, true)),
                ramp.map(Ramp::rampLight).orElseGet(() -> deriveRamp(rgb, false))
        );
    }

    public static ModColor of(String id, String name, int rgb, int rampDark, int rampLight) {
        return new ModColor(id, name, rgb, rampDark, rampLight, nearestMapColor(rgb));
    }

    /** {@code 0xAARRGGBB} with full alpha, the form most vanilla colour APIs take. */
    public int argb() {
        return 0xFF000000 | rgb;
    }

    /**
     * Nearest of vanilla's map colours in CIELAB (perceptual) distance, skipping NONE. Plain RGB
     * distance sends Cerise to crimson nylium; Lab sends it to pink, which is what a map should show.
     */
    static MapColor nearestMapColor(int rgb) {
        double[] target = lab(rgb);
        MapColor best = MapColor.COLOR_MAGENTA;
        double bestDist = Double.MAX_VALUE;
        for (int i = 1; i < 64; i++) {
            MapColor mc;
            try {
                mc = MapColor.byId(i);
            } catch (RuntimeException e) {
                continue;
            }
            if (mc == null || mc == MapColor.NONE) continue;
            double[] c = lab(mc.col);
            double d = (target[0] - c[0]) * (target[0] - c[0])
                    + (target[1] - c[1]) * (target[1] - c[1])
                    + (target[2] - c[2]) * (target[2] - c[2]);
            if (d < bestDist) {
                bestDist = d;
                best = mc;
            }
        }
        return best;
    }

    /**
     * Default texture ramp stops when colors.json gives none: in CIELAB, the dark stop keeps 60% of
     * the lightness and 90% of the chroma, the light stop goes halfway to white with 55% chroma.
     * Calibrated against the hand-tuned Cerise and Laserviolet ramps; gen_assets.py uses the same rule.
     */
    public static int deriveRamp(int rgb, boolean dark) {
        double[] l = lab(rgb);
        double chroma = Math.hypot(l[1], l[2]);
        double hue = Math.atan2(l[2], l[1]);
        double lightness = dark ? l[0] * 0.6 : l[0] + (100 - l[0]) * 0.5;
        double c = chroma * (dark ? 0.9 : 0.55);
        return fromLab(lightness, c * Math.cos(hue), c * Math.sin(hue));
    }

    /** CIELAB (D65) to sRGB {@code 0xRRGGBB}, clamped to gamut. */
    private static int fromLab(double l, double a, double b) {
        double fy = (l + 16) / 116, fx = fy + a / 500, fz = fy - b / 200;
        double x = finv(fx) * 0.95047, y = finv(fy), z = finv(fz) * 1.08883;
        double r = x * 3.2406 + y * -1.5372 + z * -0.4986;
        double g = x * -0.9689 + y * 1.8758 + z * 0.0415;
        double bl = x * 0.0557 + y * -0.2040 + z * 1.0570;
        return (gamma(r) << 16) | (gamma(g) << 8) | gamma(bl);
    }

    private static double finv(double t) {
        return t > 0.206893 ? t * t * t : (t - 16.0 / 116.0) / 7.787;
    }

    private static int gamma(double v) {
        v = Math.max(0, Math.min(1, v));
        v = v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
        return (int) Math.round(v * 255);
    }

    /** sRGB {@code 0xRRGGBB} to CIELAB (D65). */
    private static double[] lab(int rgb) {
        double r = linear((rgb >> 16) & 0xFF), g = linear((rgb >> 8) & 0xFF), b = linear(rgb & 0xFF);
        double x = (r * 0.4124 + g * 0.3576 + b * 0.1805) / 0.95047;
        double y = (r * 0.2126 + g * 0.7152 + b * 0.0722);
        double z = (r * 0.0193 + g * 0.1192 + b * 0.9505) / 1.08883;
        double fx = f(x), fy = f(y), fz = f(z);
        return new double[]{116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double linear(int c) {
        double v = c / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static double f(double t) {
        return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116.0;
    }
}
