package metacraft.ovvar.pack;

import metacraft.ovvar.Ovvar;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.Spot;
import net.minecraft.resources.Identifier;

/**
 * The trim channel: an item can wear one armour trim, and a trim is just a texture, so datagen
 * makes one trim pattern per (cell, plain patch) — drawn by vanilla, no bits, no pack build. It
 * carries the preview of the patch being aimed at, in the {@value #GHOST} material (washed out,
 * so it reads as "not sewn yet"); {@value #MATERIAL} draws the art as it is. Materials are colour
 * permutations of a key palette, so datagen's key palette is every colour any patch uses. Limb
 * cells are tagged in the texture's alpha ({@value #ALPHA_RIGHT} right, {@value #ALPHA_LEFT} left)
 * so the shader hides the other limb. Sewn patches do not ride here: vanilla cannot squeeze the
 * art to square pixels the way the shader does (Spot.squeeze), only datagen can, texel by texel.
 */
public final class Trims {
    private Trims() {}

    public static final String MATERIAL = "patch", GHOST = "ghost";
    public static final int ALPHA_RIGHT = 254, ALPHA_LEFT = 253;

    /** Can this placement be worn as a trim? (Seat patches span two sided cells; a trim is one texture.) */
    public static boolean fits(Placement p) {
        return !Patches.get(p.patch()).seat() && p.spot() != Spot.SEAT;
    }

    public static String patternName(Placement p) {
        return p.spot().id() + "_" + p.patch();
    }

    public static Identifier pattern(Placement p) {
        return Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, patternName(p));
    }

    public static Identifier material(boolean ghost) {
        return Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, ghost ? GHOST : MATERIAL);
    }
}
