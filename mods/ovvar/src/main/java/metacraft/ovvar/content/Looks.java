package metacraft.ovvar.content;

import metacraft.ovvar.Ovvar;
import metacraft.ovvar.pack.Combos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the client draws for a stack. The sewn patches live in {@code ovvar:patches} as
 * {@code spot.patch} keys in sewing order. Each half (top = chest slot, bottom = legs slot) is
 * drawn as an equipment asset whose layers are the chapter's base plus one static texture per
 * placement — an asset per combination, which {@link Combos} puts into the pack on demand — and
 * a dyeable preview layer whose dye colour carries up to two placements the pack doesn't hold
 * yet (the newest sewn ones, and the {@code ovvar:preview} being aimed), drawn by the shader.
 */
public final class Looks {
    private Looks() {}

    /** Preview slots in the dye colour: two of {@value #SLOT_BITS} bits (cell in 6, patch in 5). */
    public static final int SLOTS = 2, SLOT_BITS = 11, CELL_BITS = 6;

    // ---- placements

    /** All placements in sewing order (no preview). Pre-combo entries are placed on the fly. */
    public static List<Placement> sewn(ItemStack stack) {
        List<Placement> out = new ArrayList<>();
        List<String> list = stack.get(ModComponents.PATCHES);
        if (list == null) return out;
        for (String entry : list) {
            if (Placement.isKey(entry)) out.add(Placement.parse(entry));
            else migrateEntry(entry, out, stack);
        }
        return out;
    }

    public static List<Placement> sewn(ItemStack stack, Piece piece) {
        return sewn(stack).stream().filter(p -> p.piece() == piece).toList();
    }

    /** Replace the sewn placements (order kept: it is the sewing order). */
    public static void setSewn(ItemStack stack, List<Placement> placements) {
        if (placements.isEmpty()) stack.remove(ModComponents.PATCHES);
        else stack.set(ModComponents.PATCHES, placements.stream().map(Placement::key).toList());
    }

    /** Sew a patch on a spot, replacing whatever was there or overlapping it. */
    public static void sew(ItemStack stack, Placement placement) {
        List<Placement> list = new ArrayList<>(sewn(stack));
        list.removeIf(p -> p.spot() == placement.spot() || placement.spot().overlapping().contains(p.spot()));
        list.add(placement);
        setSewn(stack, list);
    }

    /** Unpick the patch on a spot; the patch id, or null if there was none. */
    public static String unpick(ItemStack stack, Spot spot) {
        List<Placement> list = new ArrayList<>(sewn(stack));
        String patch = null;
        for (Placement p : list) if (p.spot() == spot) patch = p.patch();
        if (patch == null) return null;
        list.removeIf(p -> p.spot() == spot);
        setSewn(stack, list);
        return patch;
    }

    public static Placement at(ItemStack stack, Spot spot) {
        for (Placement p : sewn(stack)) if (p.spot() == spot) return p;
        return null;
    }

    public static void setPreview(ItemStack stack, Placement placement) {
        if (placement == null) stack.remove(ModComponents.PREVIEW);
        else stack.set(ModComponents.PREVIEW, placement.key());
    }

    public static Placement preview(ItemStack stack) {
        String key = stack.get(ModComponents.PREVIEW);
        return key == null || !Placement.isKey(key) ? null : Placement.parse(key);
    }

    /** All patch ids on the stack (sewn), for tooltips and commands. */
    public static List<String> patches(ItemStack stack) {
        return sewn(stack).stream().map(Placement::patch).toList();
    }

    // ---- what the client gets

    /** One half as the client should see it: the asset combo the pack holds, and the dye bits for the rest. */
    public record Look(String combo, int dye) {}

    /** @param player who the packet is for (their pack may be older than the current one), or null */
    public static Look look(ItemStack stack, Piece piece, UUID player) {
        List<Placement> all = sewn(stack, piece);
        Placement preview = preview(stack);
        if (preview != null && preview.piece() != piece) preview = null;

        // The longest prefix (in sewing order) the pack already has; the rest rides in the dye bits.
        int baked = all.size();
        while (baked > 0 && !Combos.isBuilt(piece, Placement.combo(all.subList(0, baked)), player)) baked--;
        List<Placement> rest = new ArrayList<>(all.subList(baked, all.size()));
        int room = SLOTS - (preview == null ? 0 : 1);
        boolean urgent = rest.size() > room;
        if (baked < all.size()) Combos.request(piece, Placement.combo(all), urgent, player);
        List<Placement> slots = new ArrayList<>(rest.subList(Math.max(0, rest.size() - room), rest.size()));
        if (preview != null) slots.add(preview);

        int bits = 0;
        for (int i = 0; i < slots.size(); i++) {
            Placement p = slots.get(i);
            int cell = p.spot().ordinal() + 1, patch = Patches.code(p.patch());
            if (cell >= 1 << CELL_BITS || patch >= 1 << (SLOT_BITS - CELL_BITS)) throw new IllegalStateException("preview slot cannot hold " + p);
            bits |= (cell | patch << CELL_BITS) << (SLOT_BITS * i);
        }
        return new Look(Placement.combo(all.subList(0, baked)), bits == 0 ? 0 : encode(bits));
    }

    // ---- assets

    public static ResourceKey<EquipmentAsset> asset(Chapter chapter, Piece piece, boolean nercabbad, String combo) {
        return ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, assetPath(chapter, piece, nercabbad, combo)));
    }

    public static String assetPath(Chapter chapter, Piece piece, boolean nercabbad, String combo) {
        return chapter.id + "/" + piece.id + (nercabbad ? "_nercabbad" : "") + (combo.isEmpty() ? "" : "/" + combo);
    }

    // ---- the dye bits

    /**
     * Bits as three base-255 digits, each byte one more than its digit, so that no byte is 0: a
     * shaderpack's entity program divides the dye colour back out of the patch art (see
     * ovvar.glsl), which 0 would not survive. 255³ > 2²³, and the slots use 22 bits.
     */
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

    // ---- older formats

    /** Before free placement the list held field ids ({@code chest_l=beer}) or, earlier, bare patch ids. */
    private static final Map<String, Spot> LEGACY_FIELDS = Map.ofEntries(
            Map.entry("chest_l", Spot.FRONT_TOP_RIGHT), Map.entry("chest_r", Spot.FRONT_TOP_LEFT),
            Map.entry("back_l", Spot.BACK_TOP_LEFT), Map.entry("back_r", Spot.BACK_TOP_RIGHT),
            Map.entry("sleeve_l", Spot.SLEEVE_OUT_TOP_L), Map.entry("sleeve_r", Spot.SLEEVE_OUT_TOP_R),
            Map.entry("leg_l", Spot.LEG_OUT_TOP_L), Map.entry("leg_r", Spot.LEG_OUT_TOP_R),
            Map.entry("leg_l_front", Spot.LEG_FRONT_TOP_L), Map.entry("leg_r_front", Spot.LEG_FRONT_TOP_R),
            Map.entry("seat", Spot.SEAT));

    private static void migrateEntry(String entry, List<Placement> out, ItemStack stack) {
        int eq = entry.indexOf('=');
        String patch = eq < 0 ? entry : entry.substring(eq + 1);
        if (!Patches.exists(patch)) {
            Ovvar.LOGGER.warn("[ovvar] dropping unknown legacy patch entry '{}' from {}", entry, stack);
            return;
        }
        Patches.Patch p = Patches.get(patch);
        Spot legacy = eq < 0 ? null : LEGACY_FIELDS.get(entry.substring(0, eq));
        Spot spot = legacy;
        if (legacy == null || !p.fits(legacy) || out.stream().anyMatch(o -> o.spot() == legacy)) {
            // First free cell that takes it.
            spot = null;
            for (Spot s : Spot.values()) {
                if (!p.fits(s)) continue;
                Spot candidate = s;
                if (out.stream().noneMatch(o -> o.spot() == candidate || candidate.overlapping().contains(o.spot()))) { spot = s; break; }
            }
        }
        if (spot == null) {
            Ovvar.LOGGER.warn("[ovvar] no free cell for legacy patch entry '{}' on {}", entry, stack);
            return;
        }
        out.add(new Placement(spot, patch));
    }

    /** Rewrites an older patch list in the current format; true if it changed. */
    public static boolean migrate(ItemStack stack) {
        List<String> list = stack.get(ModComponents.PATCHES);
        if (list == null || list.stream().allMatch(Placement::isKey)) return false;
        List<Placement> sewn = sewn(stack);
        setSewn(stack, sewn);
        Ovvar.LOGGER.info("[ovvar] migrated legacy patches {} -> {}", list, sewn);
        return true;
    }

    static OvveItem ovve(ItemStack stack) {
        if (!(stack.getItem() instanceof OvveItem item)) throw new IllegalArgumentException("not an ovve: " + stack);
        return item;
    }
}
