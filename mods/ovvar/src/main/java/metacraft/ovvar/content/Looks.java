package metacraft.ovvar.content;

import metacraft.ovvar.Ovvar;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.ArrayList;
import java.util.List;

/**
 * Which equipment asset a stack shows. This is the one seam between "what is sewn on" (components
 * on the ovve stack) and "what the client draws" (a pre-generated equipment JSON): the datagen
 * enumerates every key this can return, so a key without a JSON is a bug, not a fallback.
 *
 * Key shape: {@code ovvar:<chapter>/<piece>[_nercabbad]/<patches>} where {@code patches} is the
 * hex bitmask over the piece's patch catalogue ({@link Patches#forPiece}), bit i = patch i sewn.
 * The ovve item (legs slot) shows the {@code bottom} key, {@code bottom_nercabbad} while its top
 * is down; the companion top stack (chest slot) shows the {@code top} key.
 */
public final class Looks {
    private Looks() {}

    public static ResourceKey<EquipmentAsset> asset(Chapter chapter, Piece piece, boolean nercabbad, String patches) {
        String state = piece.id + (nercabbad ? "_nercabbad" : "");
        return ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(Ovvar.MOD_ID,
                chapter.id + "/" + state + "/" + patches));
    }

    /** The legs-slot look of an ovve stack. */
    public static ResourceKey<EquipmentAsset> bottom(ItemStack ovve) {
        OvveItem item = ovve(ovve);
        return asset(item.chapter, Piece.BOTTOM, !OvveItem.topUp(ovve), key(Piece.BOTTOM, patches(ovve)));
    }

    /** The chest-slot look of an ovve stack's top. */
    public static ResourceKey<EquipmentAsset> top(ItemStack ovve) {
        OvveItem item = ovve(ovve);
        return asset(item.chapter, Piece.TOP, false, key(Piece.TOP, patches(ovve)));
    }

    /**
     * Hex bitmask of the given patch ids that belong to {@code piece}; ids of the other piece are
     * skipped (an ovve carries both halves' patches in one list). Throws on an id not in the catalogue.
     */
    public static String key(Piece piece, List<String> patchIds) {
        List<Patches.Patch> catalogue = Patches.forPiece(piece);
        long bits = 0;
        for (String id : patchIds) {
            int index = catalogue.indexOf(Patches.get(id));
            if (index >= 0) bits |= 1L << index;
        }
        return key(bits);
    }

    public static String key(long bits) {
        return Long.toHexString(bits);
    }

    /** The patches for a bitmask, in catalogue order. */
    public static List<Patches.Patch> patches(Piece piece, long bits) {
        List<Patches.Patch> catalogue = Patches.forPiece(piece);
        List<Patches.Patch> out = new ArrayList<>();
        for (int i = 0; i < catalogue.size(); i++) {
            if ((bits & (1L << i)) != 0) out.add(catalogue.get(i));
        }
        return out;
    }

    public static List<String> patches(ItemStack stack) {
        List<String> list = stack.get(ModComponents.PATCHES);
        return list == null ? List.of() : list;
    }

    /** Replace the sewn patches. Validated against the catalogue so a bad list never reaches a client. */
    public static void setPatches(ItemStack ovve, List<String> patchIds) {
        ovve(ovve);
        for (String id : patchIds) Patches.get(id);
        if (patchIds.isEmpty()) ovve.remove(ModComponents.PATCHES);
        else ovve.set(ModComponents.PATCHES, List.copyOf(patchIds));
    }

    private static OvveItem ovve(ItemStack stack) {
        if (!(stack.getItem() instanceof OvveItem item)) throw new IllegalArgumentException("not an ovve: " + stack);
        return item;
    }
}
