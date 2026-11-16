package se.metacraft.ovvar.content;

import net.minecraft.world.entity.EquipmentSlot;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * A chapter's ovve. The art is the skin overlay from metacraft.se/style ({@code art/ovvar/<overlay>.png},
 * 64×64 skin layout, pure green = "erase the skin here"); datagen cuts the armour layers out of it.
 * {@code tint} recolours the overlay for chapters that only differ by colour (Silicon-blue IT); the
 * other chapters' art comes coloured.
 */
public enum Chapter {
	DATA("data", "Data", "Data ovve", "Data-ovve", 0xBD3754, "data", "data-nercabbad", null, true, EquipmentSlot.LEGS, false),
	IT("it", "IT", "IT ovve", "IT-ovve", 0x8A57BD, "it", "it-nercabbad", null, true, EquipmentSlot.LEGS, false),
	/** The older silicon-blue IT ovve: kiselblå, applied to the IT overlay. */
	IT_KISEL("it_kisel", "Silicon-blue IT", "Silicon-blue IT ovve", "Kiselblå IT-ovve", 0x769BB0, "it", "it-nercabbad", 0x769BB0, true, EquipmentSlot.LEGS, false),
	/**
	 * The tailcoat: a chest-slot garment, the one thing that sets it apart from the ovvar. It is
	 * its own top — no companion, nothing to roll down, no legs and no cuffs; its pockets and its
	 * patches (the top's cells only) work as an ovve's do.
	 */
	MEDIA("media", "Media", "Media frack", "Media-frack", 0x1B1B1B, "mediafrack", null, null, false, EquipmentSlot.CHEST, false),
	// The rest of KTH's chapters: pre-coloured art (tools/chapter_overlays.py), no recipe — their only survival source is the wandering Punkphöz.
	ARKITEKTUR("arkitektur", "Arkitektur", "Arkitektur ovve", "Arkitektur-ovve", 0xA43AA8, "arkitektur", "arkitektur-nercabbad", null, true, EquipmentSlot.LEGS, true),
	/** Ingenjör until 2026-11: the chapter is called Bygg nowadays ({@link #RETIRED} maps the old id). */
	BYGG("bygg", "Bygg", "Bygg ovve", "Bygg-ovve", 0xA0522D, "bygg", "bygg-nercabbad", null, true, EquipmentSlot.LEGS, true),
	CL("cl", "CL", "CL ovve", "CL-ovve", 0x1A1A1A, "cl", "cl-nercabbad", null, true, EquipmentSlot.LEGS, true),
	DOKTORAND("doktorand", "Doktorand", "Doktorand ovve", "Doktorand-ovve", 0x1A1A1A, "doktorand", "doktorand-nercabbad", null, true, EquipmentSlot.LEGS, true),
	ELEKTRO("elektro", "Elektro", "Elektro ovve", "Elektro-ovve", 0xF5F5F5, "elektro", "elektro-nercabbad", null, true, EquipmentSlot.LEGS, true),
	ENERGI("energi", "Energi och miljö", "Energi och miljö ovve", "Energi och miljö-ovve", 0x006B71, "energi", "energi-nercabbad", null, true, EquipmentSlot.LEGS, true),
	FYSIK("fysik", "Fysik", "Fysik ovve", "Fysik-ovve", 0xFF642B, "fysik", "fysik-nercabbad", null, true, EquipmentSlot.LEGS, true),
	INDEK("indek", "Indek", "Indek ovve", "Indek-ovve", 0x4A2C1D, "indek", "indek-nercabbad", null, true, EquipmentSlot.LEGS, true),
	KEMI("kemi", "Kemi", "Kemi ovve", "Kemi-ovve", 0xFFCB05, "kemi", "kemi-nercabbad", null, true, EquipmentSlot.LEGS, true),
	MIT("mit", "Medicinsk teknik", "Medicinsk teknik ovve", "Medicinsk teknik-ovve", 0x8EC9EB, "mit", "mit-nercabbad", null, true, EquipmentSlot.LEGS, true),
	OPEN("open", "OPEN", "OPEN ovve", "OPEN-ovve", 0xC62C22, "open", "open-nercabbad", null, true, EquipmentSlot.LEGS, true),
	SAMHALL("samhall", "Samhällsbyggnad", "Samhällsbyggnad ovve", "Samhällsbyggnad-ovve", 0x294630, "samhall", "samhall-nercabbad", null, true, EquipmentSlot.LEGS, true),
	TBAS("tbas", "TBas", "TBas ovve", "TBas-ovve", 0xA2EE8D, "tbas", "tbas-nercabbad", null, true, EquipmentSlot.LEGS, true),
	/** Media's frack without Media's emblem: the same cut, its own chapter. */
	B_FRACK("b", "B-frack", "B-frack", "B-frack", 0x1B1B1B, "bfrack", null, null, false, EquipmentSlot.CHEST, true);

	/**
	 * Chapters that were removed, by their old id, with the chapter whose items and designs they
	 * now load as. In order: the wardrobe migration applies them one after the other.
	 */
	public static final Map<String, Chapter> RETIRED;

	static {
		Map<String, Chapter> retired = new LinkedHashMap<>();
		retired.put("data_polymiter", IT);
		retired.put("it_polymiter", IT_KISEL);
		retired.put("ingenjor", BYGG);
		RETIRED = Collections.unmodifiableMap(retired);
	}

	public final String id;
	/** The chapter's own name, as the wardrobe's title falls back to when the full name does not fit. */
	public final String name;
	/** The garment's English item name: "Data ovve", "Media frack", "B-frack". */
	public final String fullName;
	/** The garment's Swedish item name: "Data-ovve", "Kiselblå IT-ovve". */
	public final String svName;
	/** The cloth colour, RGB without alpha: the wardrobe's background is tinted with it. */
	public final int colour;
	public final String overlay;
	/** Overlay for the rolled-down state (legs + the top hanging at the waist), or null if the chapter has none. */
	public final @Nullable String nercabbadOverlay;
	public final @Nullable Integer tint;
	/** Whether the top can be rolled down; needs a nercabbad overlay. */
	public final boolean rollable;
	/**
	 * The slot the garment itself is worn in. LEGS is an ovve: one item with pockets in the legs
	 * slot, its top a companion in the chest slot while it is up. CHEST is a frack: the same item,
	 * pockets and all, worn as a chestplate — only the {@link Piece#TOP} half exists for it.
	 */
	public final EquipmentSlot slot;
	/** Sold by the wandering Punkphöz and nowhere else in survival: no recipe, no loot. */
	public final boolean wandererOnly;

	Chapter(String id, String name, String fullName, String svName, int colour, String overlay, @Nullable String nercabbadOverlay,
			@Nullable Integer tint, boolean rollable, EquipmentSlot slot, boolean wandererOnly) {
		this.id = id;
		this.name = name;
		this.fullName = fullName;
		this.svName = svName;
		this.colour = colour;
		this.wandererOnly = wandererOnly;
		this.overlay = overlay;
		this.nercabbadOverlay = nercabbadOverlay;
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

	/** The item id path: {@code data_ovve}, {@code media_frack}, {@code b_frack}. */
	public String itemName() {
		return id + "_" + garmentWord();
	}

	/** Whether the garment's name reads "ovve" or "frack". */
	public String garmentWord() {
		return slot == EquipmentSlot.CHEST ? "frack" : "ovve";
	}

	/** The chapters only the wandering Punkphöz sells, in enum order. */
	public static List<Chapter> wandererStock() {
		List<Chapter> out = new ArrayList<>();
		for (Chapter chapter : values()) if (chapter.wandererOnly) out.add(chapter);
		return List.copyOf(out);
	}

}
