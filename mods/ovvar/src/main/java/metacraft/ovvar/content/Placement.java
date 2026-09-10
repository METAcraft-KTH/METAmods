package metacraft.ovvar.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JavaOps;
import metacraft.ovvar.pack.Combos;

import java.util.Comparator;
import java.util.List;

/** One patch sewn on one cell. Stored as {@code spot.patch}; a half's set of them is a combo. */
public record Placement(Spot spot, Patches.Patch patch) implements Comparable<Placement> {
    private static final Comparator<Placement> ORDER = Comparator.comparing(Placement::spot).thenComparing(
            placement -> placement.patch.id()
    );

    public static final Codec<Placement> CODEC = Codec.STRING.comapFlatMap(
            key -> {
                int dot = key.indexOf('.');
                if (dot < 0) {
                    return DataResult.error(() -> "bad placement '" + key + "' (want spot.patch)");
                }
                return Spot.CODEC.parse(JavaOps.INSTANCE, key.substring(0, dot)).flatMap(
                        spot -> Patches.ID_CODEC.validate(
                                patch -> patch.fits(spot) ? DataResult.success(patch) : DataResult.error(() -> "patch '" + patch.id() + "' cannot go on " + spot.id())
                        ).parse(JavaOps.INSTANCE, key.substring(dot+1)).map(
                                patch -> new Placement(spot, patch)
                        )
                );
            },
		    Placement::key
    );

    public Placement {
        if (!patch.fits(spot)) throw new IllegalArgumentException("patch '" + patch + "' cannot go on " + spot.id());
    }

    public String key() {
        return spot.id() + "." + patch.id();
    }

    /** Never null: a bad key is a bug or foreign data, not a state. */
    public static Placement parse(String key) {
        return CODEC.parse(JavaOps.INSTANCE, key).getOrThrow(IllegalArgumentException::new);
    }

    public static boolean isKey(String s) {
        return CODEC.parse(JavaOps.INSTANCE, s).isSuccess();
    }

    public Piece piece() {
        return spot.piece;
    }

    /** The combo id of a set of placements on one half: sorted keys joined by '-', "" for none. */
    public static Combos.Combo combo(List<Placement> placements) {
        return new Combos.Combo(placements);
    }

    @Override
    public int compareTo(Placement o) {
        return ORDER.compare(this, o);
    }
}
