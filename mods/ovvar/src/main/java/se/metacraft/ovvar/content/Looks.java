package se.metacraft.ovvar.content;

import se.metacraft.ovvar.Ovvar;
import se.metacraft.ovvar.pack.Combos;
import se.metacraft.ovvar.pack.Trims;
import se.metacraft.ovvar.content.item.OvveFeetItem;
import se.metacraft.ovvar.content.item.OvveItem;
import se.metacraft.ovvar.content.item.OvveTopItem;
import se.metacraft.ovvar.content.item.OvveTop;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import org.jspecify.annotations.Nullable;

import java.util.*;

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
	 * designs, the first codes in {@link Patches#byCode}. Ranked combinations use the bits far better than
	 * fixed slots: three on the top (19 cells × 23 = 437 states, C(437,3) ≈ 13.8M) fit under 255³.
	 * The shader's binomials are exact up to {@value #INSTANT_STATES} states; {@link #rank} checks
	 * the range, and a game test checks every half against it, so cells or designs added later
	 * cannot quietly walk past what the shader can unrank.
	 *
	 * <p><b>Cells and designs share the {@value #INSTANT_STATES}.</b> The top, with 19 cells, is the
	 * half that binds: 23 designs is the most that fits (19 × 24 = 456 would not), and the bottom's
	 * 13 cells sit well inside it. A cell added to the top costs designs.
	 *
	 * <p><b>Which designs.</b> The droppable ones, {@link PatchLoot#LOOT}, and only those: a patch
	 * that can turn up on anyone has to show at once, and {@link Patches#byCode} gives loot the first
	 * codes wherever its line is in the catalogue. The codes past the last loot patch are spare
	 * while loot is fewer than 23: the encoding keeps its fixed 23 (a state is cell × 23 + design, and the shader reads
	 * the count from the preview texture), but nothing is encoded with them and datagen writes no
	 * library art or table row for them, until a new loot patch takes one by itself.
	 * {@link PatchLoot#check} fails startup and datagen if loot ever outgrows the 23. A crafted or
	 * given patch is never instant: sewn, it waits for the pack to catch up (seconds, and the trim
	 * channel still carries one of them on the top).
	 */
	public static final int INSTANT = 3, INSTANT_DESIGNS = 23;
	/**
	 * The most (cell, design) states a half may have: past this the shader's float binomials
	 * (ovvar_c3) stop being exact, C(c,3) having to stay under 2²⁴.
	 */
	public static final int INSTANT_STATES = 448;

	/** Can this placement ride in the dye colour? (Its design must be a loot patch: see {@link #instant(Patches.Patch)}.) */
	public static boolean instant(Placement p) {
		return instant(p.patch());
	}

	/**
	 * Can this design ride in the dye colour? Exactly the droppable ones ({@link PatchLoot#LOOT}),
	 * which {@link Patches#byCode} puts first; the spare codes past them are nobody's.
	 */
	public static boolean instant(Patches.Patch p) {
		return PatchLoot.LOOT.contains(p.id());
	}

	// ---- placements

	/** All placements in sewing order (no preview). Pre-combo entries are placed on the fly. */
	public static Optional<SpotPlacements> sewn(ItemStack stack) {
		return Optional.ofNullable(stack.get(ModComponents.PATCHES));
	}

	public static Optional<SpotPlacements> sewn(ItemStack stack, Piece piece) {
		return sewn(stack).flatMap(placements -> placements.forPiece(piece));
	}

	/** Replace the sewn placements (order kept: it is the sewing order). */
	public static void setSewn(ItemStack stack, @Nullable SpotPlacements placements) {
		stack.set(ModComponents.PATCHES, placements);
	}

	/** Does this garment have the cell at all (a frack has no legs), and is it free enough to take the patch? */
	public static boolean canSew(ItemStack stack, Placement placement) {
		return hasPiece(stack, placement.piece()) && SpotPlacements.canApply(sewn(stack), placement);
	}

	/** Whether the garment is drawn with this half: everything but a frack's legs. A non-garment stack has both. */
	public static boolean hasPiece(ItemStack stack, Piece piece) {
		return !(stack.getItem() instanceof OvveItem item) || item.chapter.pieces().contains(piece);
	}

	/** Sew a patch on a spot, replacing whatever was there or overlapping it. */
	public static boolean sew(ItemStack stack, Placement placement) {
		var sewn = sewn(stack);
		if (hasPiece(stack, placement.piece()) && SpotPlacements.canApply(sewn, placement)) {
			setSewn(stack, SpotPlacements.apply(sewn, placement));
			return true;
		} else {
			return false;
		}
	}

	/** Unpick the patch on a spot; the patch id, or null if there was none. */
	public static Patches.Patch unpick(ItemStack stack, Spot spot) {
		var patches = sewn(stack);
		Patches.Patch patch = patches.flatMap(p -> p.get(spot)).orElse(null);
		if (patch == null) return null;
		setSewn(stack, patches.get().remove(spot).orElse(null));
		return patch;
	}

	public static Placement at(ItemStack stack, Spot spot) {
		return sewn(stack).flatMap(p -> p.getPlacement(spot)).orElse(null);
	}

	public static void setPreview(ItemStack stack, Placement placement) {
		if (placement == null) stack.remove(ModComponents.PREVIEW);
		else stack.set(ModComponents.PREVIEW, placement);
	}

	public static Placement preview(ItemStack stack) {
		return stack.get(ModComponents.PREVIEW);
	}

	// ---- what the client gets

	/**
	 * One half as the client should see it: the asset combo the pack holds, the dye bits for the
	 * rest, and — legs only, when the wearer's feet slot carries our second channel — the dye bits
	 * of the boots pass, three more; on the top, one more placement worn as the armour trim (or
	 * null), chest and back cells only ({@link Trims#fits}). {@code complete}: is every sewn patch
	 * drawn this way, or does this viewer need a newer pack to see them all? (The preview of a
	 * patch being aimed at is a display entity on the stand, never part of this.)
	 */
	public record Look(Combos.Combo combo, int dye, int feetDye, Placement trim, boolean complete) {}

	/** Does the wearer's feet slot carry the second channel for this ovve? (Set every tick by the wearer's sync.) */
	public static boolean feetChannel(ItemStack stack) {
		return Boolean.TRUE.equals(stack.get(ModComponents.FEET_CHANNEL));
	}

	/** The chapter of an ovve, its top or its cuffs; null for anything else. */
	public static @Nullable Chapter chapterOf(ItemStack stack) {
		if (stack.getItem() instanceof OvveItem item) return item.chapter;
		if (stack.getItem() instanceof OvveTopItem item) return item.chapter;
		if (stack.getItem() instanceof OvveFeetItem item) return item.chapter;
		return null;
	}

	/** @param player who the packet is for (their pack may be older than the current one), or null */
	public static Look look(ItemStack stack, Piece piece, UUID player) {
		return look(stack, piece, player, true);
	}

	/**
	 * @param requestCombo whether a patch set the pack does not yet hold should be queued for it. A
	 *   view that only draws the dye's instant patches (the chestplate wrap in {@link OvveTop}) passes
	 *   false, so wearing armour never triggers a pack build for the overflow it does not show.
	 *   A mob's garment ({@link MobOvvar}) never queues one either: its patches all ride the dye.
	 */
	public static Look look(ItemStack stack, Piece piece, UUID player, boolean requestCombo) {
		// On an armour stand the patches are display entities (StandDisplays); the armour draws none.
		var all = Boolean.TRUE.equals(stack.get(ModComponents.ON_STAND)) ? Optional.<SpotPlacements>empty() : sewn(stack, piece);
		List<Placement> core = SpotPlacements.asPlacementList(all);
		// Combinations are built per chapter; something that is not a garment has none built, and asks for none.
		Chapter chapter = chapterOf(stack);

		// The longest prefix (in sewing order) the pack already has; the rest rides in the dye bits
		// if it fits there (few enough, designs the channel can name), else the pack must catch up.
		int baked = core.size();
		while (baked > 0 && (chapter == null || !Combos.isBuilt(chapter, piece, Placement.combo(core.subList(0, baked)), player))) baked--;
		List<Placement> rest = new ArrayList<>(core.subList(baked, core.size()));
		boolean feet = piece == Piece.BOTTOM && feetChannel(stack);
		int room = INSTANT * (feet ? 2 : 1);
		boolean urgent = rest.size() > room + (piece == Piece.TOP ? 1 : 0) || rest.stream().filter(p -> !instant(p)).count() > (piece == Piece.TOP ? 1 : 0);
		if (requestCombo && chapter != null && baked < core.size() && !MobOvvar.is(stack)) Combos.request(chapter, piece, Placement.combo(core), urgent);
		List<Placement> shown = new ArrayList<>();
		for (int i = rest.size() - 1; i >= 0 && shown.size() < room; i--) if (instant(rest.get(i))) shown.add(rest.get(i));
		// What the dye cannot take, the top's trim can, one placement, any design: the newest left over.
		Placement trim = null;
		for (int i = rest.size() - 1; i >= 0 && trim == null; i--) {
			Placement p = rest.get(i);
			if (!shown.contains(p) && Trims.fits(p)) trim = p;
		}
		boolean complete = shown.size() + (trim == null ? 0 : 1) == rest.size();
		// The first three ride in the garment's own dye colour, the next three in the boots'.
		List<Placement> own = shown.subList(0, Math.min(INSTANT, shown.size()));
		List<Placement> boots = shown.subList(own.size(), shown.size());
		return new Look(Placement.combo(core.subList(0, baked)),
				own.isEmpty() ? 0 : encode(rank(piece, own)), boots.isEmpty() ? 0 : encode(rank(piece, boots)), trim, complete);
	}

	/**
	 * After a player changed what is sewn on an ovve (at a stand, or by command): any half they
	 * cannot be shown in full without a newer pack is claimed for them ({@link Combos#claim}).
	 */
	public static void claimIfNeeded(ServerPlayer player, ItemStack stack) {
		if (MobOvvar.is(stack)) return;   // a mob's garment is always drawn in full: no pack push for it
		Chapter chapter = chapterOf(stack);
		if (chapter == null) return;
		for (Piece piece : Piece.values()) {
			if (!look(stack, piece, player.getUUID()).complete()) {
				Combos.claim(player, chapter, piece, Placement.combo(SpotPlacements.asPlacementList(sewn(stack, piece))));
			}
		}
	}

	// ---- ranking the instant set (mirrored in ovvar.glsl)

	/** A placement's state: cell index in its half × designs + design index. */
	static int state(Placement p) {
		int cell = Spot.cells(p.piece()).indexOf(p.spot());
		int design = Patches.code(p.patch()) - 1;
		if (cell < 0 || !instant(p) || design < 0 || design >= INSTANT_DESIGNS) throw new IllegalArgumentException("not an instant placement: " + p);
		return cell * INSTANT_DESIGNS + design;
	}

	/** 1 + the rank of the set among k-sets of the half's states, after all smaller k (0 = empty set). */
	static int rank(Piece piece, List<Placement> set) {
		int m = Spot.cells(piece).size() * INSTANT_DESIGNS;
		if (m > INSTANT_STATES) throw new IllegalStateException("instant channel: " + m + " states; the shader's binomials are exact up to " + INSTANT_STATES);
		int[] s = set.stream().mapToInt(Looks::state).sorted().toArray();
		if (s.length > INSTANT) throw new IllegalArgumentException("more than " + INSTANT + " instant placements");
		for (int i = 1; i < s.length; i++) if (s[i] == s[i - 1]) throw new IllegalArgumentException("duplicate placement");
		long value = 0;
		for (int k = 0; k < s.length; k++) value += choose(m, k);   // all sets smaller than this one's size
		for (int i = 0; i < s.length; i++) value += choose(s[i], i + 1);  // combinadic
		if (value >= 255L * 255 * 255) throw new IllegalStateException("instant channel overflow: " + value);
		return (int) value;
	}

	/** The dye colour that carries exactly this instant set on a half, as {@link #look} encodes it. Public for tests. */
	public static int instantDye(Piece piece, List<Placement> set) {
		return encode(rank(piece, set));
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
}
