package metacraft.ovvar.content;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/**
 * The two halves an ovve is drawn as. They are render layers, not items: the ovve is one item in
 * the legs slot; its top is a companion stack the mod keeps in the chest slot while the top is up.
 */
public enum Piece implements StringRepresentable {
    /** Torso and sleeves: the chest slot's outer model (body + both arms). */
    TOP("top", "humanoid"),
    /** Legs and waist: the legs slot's inner model (body + both legs). */
    BOTTOM("bottom", "humanoid_leggings");

    public static final Codec<Piece> CODEC = StringRepresentable.fromEnum(Piece::values);

    public final String id;
    /** Key in the equipment JSON's {@code layers} and the texture folder under {@code textures/entity/equipment/}. */
    public final String layer;

    Piece(String id, String layer) {
        this.id = id;
        this.layer = layer;
    }

    @Override
    public @NonNull String getSerializedName() {
        return id;
    }
}
