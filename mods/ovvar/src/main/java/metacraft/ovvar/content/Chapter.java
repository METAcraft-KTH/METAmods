package metacraft.ovvar.content;

import net.minecraft.world.entity.EquipmentSlot;
import org.jspecify.annotations.Nullable;

import java.util.Set;


/**
 * A chapter's ovve. The art is the skin overlay from metacraft.se/style ({@code art/ovvar/<overlay>.png},
 * 64×64 skin layout, pure green = "erase the skin here"); datagen cuts the armour layers out of it.
 * {@code tint} recolours the overlay for chapters that only differ by colour.
 */
public enum Chapter {
	DATA("data", "Data", "data", "data-nercabbad", null, null, true, EquipmentSlot.LEGS),
	IT("it", "IT", "it", "it-nercabbad", null, null, true, EquipmentSlot.LEGS),
	/** The older silicon-blue IT ovve; PolymITer's kiselblå, applied to the IT overlay. */
	IT_KISEL("it_kisel", "Silicon-blue IT", "it", "it-nercabbad", null, 0x769BB0, true, EquipmentSlot.LEGS),
	/**
	 * The tailcoat: a chest-slot garment, the one thing that sets it apart from the ovvar. It is
	 * its own top — no companion, nothing to roll down, no legs and no cuffs; its pockets and its
	 * patches (the top's cells only) work as an ovve's do.
	 */
	MEDIA("media", "Media", "mediafrack", null, null, null, false, EquipmentSlot.CHEST),
	/**
	 * For comparison: the same ovvar with PolymITer's hand-drawn leggings texture
	 * ({@code art/ovvar/polymiter/nercabbad.png}, its red one) for the rolled-down state, shifted to
	 * the chapter's colour — PolymITer only ever drew that state, so the top is the website's.
	 */
	DATA_POLYMITER("data_polymiter", "Data (PolymITer)", "data", "data-nercabbad", "polymiter/nercabbad", null, true, EquipmentSlot.LEGS),
	IT_POLYMITER("it_polymiter", "IT (PolymITer)", "it", "it-nercabbad", "polymiter/nercabbad", null, true, EquipmentSlot.LEGS);

	public final String id;
	public final String name;
	public final String overlay;
	/** Overlay for the rolled-down state (legs + the top hanging at the waist), or null if the chapter has none. */
	public final @Nullable String nercabbadOverlay;
	/**
	 * A ready-made 64×32 leggings texture for the rolled-down state, recoloured to the chapter's
	 * colour and used instead of cutting {@link #nercabbadOverlay} (PolymITer's art), or null.
	 */
	public final @Nullable String nercabbadArmour;
	public final @Nullable Integer tint;
	/** Whether the top can be rolled down; needs a nercabbad overlay. */
	public final boolean rollable;
	/**
	 * The slot the garment itself is worn in. LEGS is an ovve: one item with pockets in the legs
	 * slot, its top a companion in the chest slot while it is up. CHEST is a frack: the same item,
	 * pockets and all, worn as a chestplate — only the {@link Piece#TOP} half exists for it.
	 */
	public final EquipmentSlot slot;

	Chapter(String id, String name, String overlay, @Nullable String nercabbadOverlay, @Nullable String nercabbadArmour,
			@Nullable Integer tint, boolean rollable, EquipmentSlot slot) {
		this.id = id;
		this.name = name;
		this.overlay = overlay;
		this.nercabbadOverlay = nercabbadOverlay;
		this.nercabbadArmour = nercabbadArmour;
		this.tint = tint;
		this.rollable = rollable;
		this.slot = slot;
		if (rollable && nercabbadOverlay == null) throw new IllegalStateException(id + " is rollable but has no nercabbad overlay");
		if (rollable && slot != EquipmentSlot.LEGS) throw new IllegalStateException(id + " is rollable but is not worn in the legs slot");
	}

	/** The halves this garment is drawn as, and so the cells a patch may go on: both for an ovve, the top alone for a frack. */
	public Set<Piece> pieces() {
		return slot == EquipmentSlot.CHEST ? Set.of(Piece.TOP) : Set.of(Piece.TOP, Piece.BOTTOM);
	}

	/** The half the garment item itself draws: the bottom of an ovve (its top is the companion), the whole of a frack. */
	public Piece ownPiece() {
		return slot == EquipmentSlot.CHEST ? Piece.TOP : Piece.BOTTOM;
	}

	/** The chapter with this id, or null. */
	public static @Nullable Chapter byId(String id) {
		for (Chapter chapter : values()) if (chapter.id.equals(id)) return chapter;
		return null;
	}

	/** The item id path: {@code data_ovve}, {@code media_frack}. */
	public String itemName() {
		return id + "_" + garmentWord();
	}

	/** Whether the garment's name reads "ovve" or "frack". */
	public String garmentWord() {
		return this == MEDIA ? "frack" : "ovve";
	}

}
