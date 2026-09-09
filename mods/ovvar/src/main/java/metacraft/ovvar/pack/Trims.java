package metacraft.ovvar.pack;

import metacraft.ovvar.Ovvar;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.Spot;
import net.minecraft.resources.Identifier;

/**
 * The trim channel: an item can wear one armour trim, and a trim is just a texture, so datagen
 * makes one trim pattern per (cell, plain patch) and the first patch sewn on a half rides as its
 * trim — drawn by vanilla, no bits, no pack build. Limb cells are tagged in the texture's alpha
 * ({@value #ALPHA_RIGHT} right, {@value #ALPHA_LEFT} left) so the shader hides the other limb.
 */
public final class Trims {
    private Trims() {}

    public static final String MATERIAL = "patch";
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

    public static Identifier material() {
        return Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, MATERIAL);
    }
}
