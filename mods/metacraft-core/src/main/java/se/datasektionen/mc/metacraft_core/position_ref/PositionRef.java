package se.datasektionen.mc.metacraft_core.position_ref;

import net.minecraft.util.math.Vec3d;
import se.datasektionen.mc.metacraft_core.util.RefContext;

import java.util.Optional;

public interface PositionRef {

	Optional<Vec3d> get(RefContext ctx);

	PositionRefType<?> getType();

}
