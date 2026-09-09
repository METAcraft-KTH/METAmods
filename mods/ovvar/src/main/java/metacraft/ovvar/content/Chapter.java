package metacraft.ovvar.content;

import org.jspecify.annotations.Nullable;


/**
 * A chapter's ovve. The art is the skin overlay from metacraft.se/style ({@code art/ovvar/<overlay>.png},
 * 64×64 skin layout, pure green = "erase the skin here"); datagen cuts the armour layers out of it.
 * {@code tint} recolours the overlay for chapters that only differ by colour.
 */
public enum Chapter {
    DATA("data", "Data", "data", "data-nercabbad", null, true),
    IT("it", "IT", "it", "it-nercabbad", null, true),
    /** The older silicon-blue IT ovve; PolymITer's kiselblå, applied to the IT overlay. */
    IT_KISEL("it_kisel", "Silicon-blue IT", "it", "it-nercabbad", 0x769BB0, true),
    /** The tailcoat: always up (a frack has nothing to roll down). */
    MEDIA("media", "Media", "mediafrack", null, null, false);

    public final String id;
    public final String name;
    public final String overlay;
    /** Overlay for the rolled-down state (legs + the top hanging at the waist), or null if the chapter has none. */
    public final @Nullable String nercabbadOverlay;
    public final @Nullable Integer tint;
    /** Whether the top can be rolled down; needs a nercabbad overlay. */
    public final boolean rollable;

    Chapter(String id, String name, String overlay, @Nullable String nercabbadOverlay, @Nullable Integer tint, boolean rollable) {
        this.id = id;
        this.name = name;
        this.overlay = overlay;
        this.nercabbadOverlay = nercabbadOverlay;
        this.tint = tint;
        this.rollable = rollable;
        if (rollable && nercabbadOverlay == null) throw new IllegalStateException(id + " is rollable but has no nercabbad overlay");
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
