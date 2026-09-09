package metacraft.moredyes.recipe;

import metacraft.moredyes.content.ModDyeItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Reading a colour out of a dye stack, ours or vanilla's. Vanilla's dye recipes only understand the
 * {@code DYE} component (a {@code DyeColor}); ours carry an RGB, so the mixed recipes here take both
 * and require at least one of ours (otherwise the vanilla recipe already matched).
 */
public final class DyeStacks {
    private DyeStacks() {}

    public static boolean isOurs(ItemStack stack) {
        return stack.getItem() instanceof ModDyeItem;
    }

    public static boolean isVanilla(ItemStack stack) {
        return stack.has(DataComponents.DYE);
    }

    public static boolean isAnyDye(ItemStack stack) {
        return isOurs(stack) || isVanilla(stack);
    }

    /** The RGB vanilla uses for leather and other {@code DYED_COLOR} items (texture-diffuse). */
    public static int armorRgb(ItemStack stack) {
        if (stack.getItem() instanceof ModDyeItem dye) return dye.color().rgb();
        DyeColor color = stack.getOrDefault(DataComponents.DYE, DyeColor.WHITE);
        return color.getTextureDiffuseColor() & 0xFFFFFF;
    }

    /** The RGB vanilla uses for firework explosions. */
    public static int fireworkRgb(ItemStack stack) {
        if (stack.getItem() instanceof ModDyeItem dye) return dye.color().rgb();
        DyeColor color = stack.getOrDefault(DataComponents.DYE, DyeColor.WHITE);
        return color.getFireworkColor() & 0xFFFFFF;
    }

    /**
     * Vanilla's {@code DyedItemColor.applyDyes} arithmetic (average the channels, then rescale so the
     * brightest channel keeps the average of the inputs' brightest channels), on raw RGBs.
     */
    public static DyedItemColor mix(@Nullable DyedItemColor existing, List<Integer> rgbs) {
        int r = 0, g = 0, b = 0, maxSum = 0, count = 0;
        if (existing != null) {
            int c = existing.rgb();
            int cr = ARGB.red(c), cg = ARGB.green(c), cb = ARGB.blue(c);
            maxSum += Math.max(cr, Math.max(cg, cb));
            r += cr;
            g += cg;
            b += cb;
            count++;
        }
        for (int c : rgbs) {
            int cr = ARGB.red(c), cg = ARGB.green(c), cb = ARGB.blue(c);
            maxSum += Math.max(cr, Math.max(cg, cb));
            r += cr;
            g += cg;
            b += cb;
            count++;
        }
        r /= count;
        g /= count;
        b /= count;
        float maxAverage = (float) maxSum / count;
        float brightest = Math.max(r, Math.max(g, b));
        r = (int) (r * maxAverage / brightest);
        g = (int) (g * maxAverage / brightest);
        b = (int) (b * maxAverage / brightest);
        return new DyedItemColor(ARGB.color(0, r, g, b));
    }
}
