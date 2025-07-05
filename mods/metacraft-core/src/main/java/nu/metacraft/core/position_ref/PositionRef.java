package nu.metacraft.core.position_ref;

import net.minecraft.util.math.Vec3d;
import nu.metacraft.core.util.RefContext;

import java.util.Optional;

public interface PositionRef {

	Optional<Vec3d> get(RefContext ctx);

	PositionRefType<?> getType();

}
