package metacraft.ovvar.content;

import metacraft.ovvar.Ovvar;
import metacraft.ovvar.pack.Combos;
import metacraft.ovvar.pack.Trims;
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

    /**
     * The instant channel: up to {@value #INSTANT} placements per half ride in the dye colour, as
     * the rank of their set among all sets of (cell, design) states — cells × {@value #INSTANT_DESIGNS}
     * designs, the first ones in the catalogue. Ranked combinations use the bits far better than
     * fixed slots: three on the top (20 cells × 22 = 440 states, C(440,3) ≈ 14.1M) fit under 255³.
     * The shader's binomials are exact up to 448 states; {@link #rank} checks the range.
     */
    public static final int INSTANT = 3, INSTANT_DESIGNS = 22;

    /** Can this placement ride in the dye colour? (Its design must be among the first {@value #INSTANT_DESIGNS}, and cell-sized.) */
    public static boolean instant(Placement p) {
        return Patches.code(p.patch()) <= INSTANT_DESIGNS && !Patches.get(p.patch()).oversize();
    }

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

    /**
     * One half as the client should see it: the placement worn as the armour trim (or null) and
     * whether it is the ghosted preview, the asset combo the pack holds, the dye bits for the rest,
     * and — legs only, when the wearer's feet slot carries our second channel — the dye bits of
     * the boots pass, three more.
     */
    public record Look(Placement trim, boolean ghost, String combo, int dye, int feetDye) {}

    /** Does the wearer's feet slot carry the second channel for this ovve? (Set every tick by the wearer's sync.) */
    public static boolean feetChannel(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModComponents.FEET_CHANNEL));
    }

    /** @param player who the packet is for (their pack may be older than the current one), or null */
    public static Look look(ItemStack stack, Piece piece, UUID player) {
        List<Placement> all = sewn(stack, piece);
        Placement preview = preview(stack);
        if (preview != null && preview.piece() != piece) preview = null;

        // A patch being aimed at is worn as the trim, in the ghost material — any design, any size.
        // A seat patch cannot be a trim and previews solid in the dye bits instead (if the channel
        // can name it). Sewn patches never ride as the trim: vanilla draws trims, so their art is
        // squeezed to square pixels texel by texel, fine for a ghost and not for the real thing.
        Placement trim = null;
        boolean ghost = preview != null && Trims.fits(preview);
        if (ghost) {
            trim = preview;
            preview = null;
        } else if (preview != null && !instant(preview)) {
            preview = null;
        }
        List<Placement> core = new ArrayList<>(all);

        // The longest prefix (in sewing order) the pack already has; the rest rides in the dye bits
        // if it fits there (few enough, designs the channel can name), else the pack must catch up.
        int baked = core.size();
        while (baked > 0 && !Combos.isBuilt(piece, Placement.combo(core.subList(0, baked)), player)) baked--;
        List<Placement> rest = new ArrayList<>(core.subList(baked, core.size()));
        boolean feet = piece == Piece.BOTTOM && feetChannel(stack);
        int room = INSTANT * (feet ? 2 : 1) - (preview == null ? 0 : 1);
        boolean urgent = rest.size() > room || !rest.stream().allMatch(Looks::instant);
        if (baked < core.size()) Combos.request(piece, Placement.combo(core), urgent, player);
        List<Placement> shown = new ArrayList<>();
        for (int i = rest.size() - 1; i >= 0 && shown.size() < room; i--) if (instant(rest.get(i))) shown.add(rest.get(i));
        if (preview != null) {
            Spot aimed = preview.spot();
            shown.removeIf(p -> p.spot() == aimed || aimed.overlapping().contains(p.spot()));
            shown.add(preview);
        }
        // The first three ride in the garment's own dye colour, the next three in the boots'.
        List<Placement> own = shown.subList(0, Math.min(INSTANT, shown.size()));
        List<Placement> boots = shown.subList(own.size(), shown.size());
        return new Look(trim, ghost, Placement.combo(core.subList(0, baked)),
                own.isEmpty() ? 0 : encode(rank(piece, own)), boots.isEmpty() ? 0 : encode(rank(piece, boots)));
    }

    // ---- ranking the instant set (mirrored in ovvar.glsl)

    /** A placement's state: cell index in its half × designs + design index. */
    static int state(Placement p) {
        int cell = Spot.cells(p.piece()).indexOf(p.spot());
        int design = Patches.code(p.patch()) - 1;
        if (cell < 0 || design < 0 || design >= INSTANT_DESIGNS) throw new IllegalArgumentException("not an instant placement: " + p);
        return cell * INSTANT_DESIGNS + design;
    }

    /** 1 + the rank of the set among k-sets of the half's states, after all smaller k (0 = empty set). */
    static int rank(Piece piece, List<Placement> set) {
        int m = Spot.cells(piece).size() * INSTANT_DESIGNS;
        if (m > 448) throw new IllegalStateException("instant channel: " + m + " states; the shader's binomials are exact up to 448");
        int[] s = set.stream().mapToInt(Looks::state).sorted().toArray();
        if (s.length > INSTANT) throw new IllegalArgumentException("more than " + INSTANT + " instant placements");
        for (int i = 1; i < s.length; i++) if (s[i] == s[i - 1]) throw new IllegalArgumentException("duplicate placement");
        long value = 0;
        for (int k = 0; k < s.length; k++) value += choose(m, k);   // all sets smaller than this one's size
        for (int i = 0; i < s.length; i++) value += choose(s[i], i + 1);  // combinadic
        if (value >= 255L * 255 * 255) throw new IllegalStateException("instant channel overflow: " + value);
        return (int) value;
    }

    static long choose(int n, int k) {
        if (k < 0 || k > n) return 0;
        long r = 1;
        for (int i = 1; i <= k; i++) r = r * (n - k + i) / i;
        return r;
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
        // A cell that no longer exists (spot.patch with an unknown spot): the patch moves to a free cell.
        int dot = patch.indexOf('.');
        if (dot > 0 && !Patches.exists(patch) && Patches.exists(patch.substring(dot + 1))) patch = patch.substring(dot + 1);
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
