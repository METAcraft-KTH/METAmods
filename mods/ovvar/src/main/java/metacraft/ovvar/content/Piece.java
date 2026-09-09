package metacraft.ovvar.content;

/**
 * The two halves an ovve is drawn as. They are render layers, not items: the ovve is one item in
 * the legs slot; its top is a companion stack the mod keeps in the chest slot while the top is up.
 */
public enum Piece {
    /** Torso and sleeves: the chest slot's outer model (body + both arms). */
    TOP("top", "humanoid"),
    /** Legs and waist: the legs slot's inner model (body + both legs). */
    BOTTOM("bottom", "humanoid_leggings");

    public final String id;
    /** Key in the equipment JSON's {@code layers} and the texture folder under {@code textures/entity/equipment/}. */
    public final String layer;

    Piece(String id, String layer) {
        this.id = id;
        this.layer = layer;
    }
}
