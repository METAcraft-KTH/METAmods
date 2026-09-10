package metacraft.ovvar.content;

import org.jspecify.annotations.Nullable;


/**
 * A chapter's ovve. The art is the skin overlay from metacraft.se/style ({@code art/ovvar/<overlay>.png},
 * 64×64 skin layout, pure green = "erase the skin here"); datagen cuts the armour layers out of it.
 * {@code tint} recolours the overlay for chapters that only differ by colour.
 */
public enum Chapter {
    DATA("data", "Data", "data", "data-nercabbad", null, null, true),
    IT("it", "IT", "it", "it-nercabbad", null, null, true),
    /** The older silicon-blue IT ovve; PolymITer's kiselblå, applied to the IT overlay. */
    IT_KISEL("it_kisel", "Silicon-blue IT", "it", "it-nercabbad", null, 0x769BB0, true),
    /** The tailcoat: always up (a frack has nothing to roll down). */
    MEDIA("media", "Media", "mediafrack", null, null, null, false),
    /**
     * For comparison: the same ovvar with PolymITer's hand-drawn leggings texture
     * ({@code art/ovvar/polymiter/nercabbad.png}, its red one) for the rolled-down state, shifted to
     * the chapter's colour — PolymITer only ever drew that state, so the top is the website's.
     */
    DATA_POLYMITER("data_polymiter", "Data (PolymITer)", "data", "data-nercabbad", "polymiter/nercabbad", null, true),
    IT_POLYMITER("it_polymiter", "IT (PolymITer)", "it", "it-nercabbad", "polymiter/nercabbad", null, true);

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

    Chapter(String id, String name, String overlay, @Nullable String nercabbadOverlay, @Nullable String nercabbadArmour,
            @Nullable Integer tint, boolean rollable) {
        this.id = id;
        this.name = name;
        this.overlay = overlay;
        this.nercabbadOverlay = nercabbadOverlay;
        this.nercabbadArmour = nercabbadArmour;
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
