package metacraft.ovvar.content;

import metacraft.ovvar.Ovvar;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the client draws for a stack. The sewn patches live in {@code ovvar:patches} as
 * {@code field=patch} entries; the client sees one equipment asset per half and a dye colour
 * whose 24 bits, laid out by {@link Layout}, tell the shader which patch each field shows. A
 * transient {@code ovvar:preview} entry is drawn the same way and never persisted as sewn.
 */
public final class Looks {
    private Looks() {}

    public static ResourceKey<EquipmentAsset> asset(Chapter chapter, Piece piece, boolean nercabbad) {
        return ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(Ovvar.MOD_ID,
                chapter.id + "/" + piece.id + (nercabbad ? "_nercabbad" : "")));
    }

    /** The legs-slot look of an ovve stack. */
    public static ResourceKey<EquipmentAsset> bottom(ItemStack ovve) {
        return asset(ovve(ovve).chapter, Piece.BOTTOM, !OvveItem.topUp(ovve));
    }

    // ---- sewn patches

    /** field id → patch id, as sewn (no preview). Entries from before fields existed are placed on the fly. */
    public static Map<String, String> sewn(ItemStack stack) {
        Map<String, String> out = new LinkedHashMap<>();
        List<String> list = stack.get(ModComponents.PATCHES);
        if (list == null) return out;
        List<String> legacy = new ArrayList<>();
        for (String entry : list) {
            if (entry.indexOf('=') < 0) legacy.add(entry);
            else put(out, entry);
        }
        for (String patch : legacy) {
            // Before 0.2 the list held bare patch ids pinned by the catalogue; the first free field
            // that takes the patch gets it, and a patch that no longer exists is dropped.
            if (!Patches.exists(patch)) {
                Ovvar.LOGGER.warn("[ovvar] dropping unknown legacy patch '{}' from {}", patch, stack);
                continue;
            }
            Layout.all().stream().filter(f -> f.accepts(patch) && !out.containsKey(f.id())).findFirst()
                    .ifPresentOrElse(f -> out.put(f.id(), patch),
                            () -> Ovvar.LOGGER.warn("[ovvar] no free field for legacy patch '{}' on {}", patch, stack));
        }
        return out;
    }

    /** Rewrites a pre-field patch list in the current format; true if it changed. */
    public static boolean migrate(ItemStack stack) {
        List<String> list = stack.get(ModComponents.PATCHES);
        if (list == null || list.stream().allMatch(e -> e.indexOf('=') >= 0)) return false;
        Map<String, String> sewn = sewn(stack);
        setSewn(stack, sewn);
        Ovvar.LOGGER.info("[ovvar] migrated legacy patches {} -> {}", list, sewn);
        return true;
    }

    /** Sewn plus the preview, for display. */
    public static Map<String, String> shown(ItemStack stack) {
        Map<String, String> out = sewn(stack);
        String preview = stack.get(ModComponents.PREVIEW);
        if (preview != null) put(out, preview);
        return out;
    }

    private static void put(Map<String, String> out, String entry) {
        int eq = entry.indexOf('=');
        if (eq < 0) throw new IllegalStateException("bad patch entry '" + entry + "' (want field=patch)");
        String field = entry.substring(0, eq), patch = entry.substring(eq + 1);
        if (!Layout.get(field).accepts(patch)) throw new IllegalStateException("patch '" + patch + "' cannot be in field " + field);
        out.put(field, patch);
    }

    public static String entry(String field, String patch) {
        return field + "=" + patch;
    }

    /** Replace the sewn patches. Validated against the layout so a bad list never reaches a client. */
    public static void setSewn(ItemStack stack, Map<String, String> patches) {
        List<String> list = new ArrayList<>();
        patches.forEach((field, patch) -> {
            if (!Layout.get(field).accepts(patch)) throw new IllegalArgumentException("patch '" + patch + "' cannot be in field " + field);
            list.add(entry(field, patch));
        });
        if (list.isEmpty()) stack.remove(ModComponents.PATCHES);
        else stack.set(ModComponents.PATCHES, list);
    }

    public static void setPreview(ItemStack stack, String field, String patch) {
        if (field == null) stack.remove(ModComponents.PREVIEW);
        else stack.set(ModComponents.PREVIEW, entry(field, patch));
    }

    /** All patch ids on the stack (sewn), for tooltips and commands. */
    public static List<String> patches(ItemStack stack) {
        return new ArrayList<>(sewn(stack).values());
    }

    // ---- the bits

    /** Every field's value at its offset, for one half. */
    public static int bits(Piece piece, Map<String, String> shown) {
        int bits = 0;
        for (Layout.Field f : Layout.fields(piece)) {
            String patch = shown.get(f.id());
            if (patch != null) bits |= f.valueOf(patch) << f.offset();
        }
        return bits;
    }

    /**
     * The dye colour for one half, or 0 for "nothing sewn" (no dye: the patch layers are not drawn
     * at all). Otherwise the bits as three base-255 digits, each byte one more than its digit, so
     * that no byte is 0: a shaderpack's entity program divides the dye colour back out of the
     * patch art (see ovvar.glsl), which 0 would not survive.
     */
    public static int dye(Piece piece, Map<String, String> shown) {
        int bits = bits(piece, shown);
        if (bits == 0) return 0;
        return encode(bits);
    }

    static int encode(int bits) {
        if (bits < 0 || bits >= 255 * 255 * 255) throw new IllegalArgumentException("bits out of range: " + bits);
        int b = bits % 255 + 1, g = bits / 255 % 255 + 1, r = bits / (255 * 255) + 1;
        return r << 16 | g << 8 | b;
    }

    static int decode(int dye) {
        int r = (dye >> 16 & 0xFF) - 1, g = (dye >> 8 & 0xFF) - 1, b = (dye & 0xFF) - 1;
        if (r < 0 || g < 0 || b < 0) throw new IllegalArgumentException("not an ovvar dye colour: " + Integer.toHexString(dye));
        return r * 255 * 255 + g * 255 + b;
    }

    static OvveItem ovve(ItemStack stack) {
        if (!(stack.getItem() instanceof OvveItem item)) throw new IllegalArgumentException("not an ovve: " + stack);
        return item;
    }
}
