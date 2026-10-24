package se.metacraft.core.position_ref;

import se.metacraft.core.util.RefContext;

import java.util.Optional;
import net.minecraft.world.phys.Vec3;

public interface PositionRef {

	Optional<Vec3> get(RefContext ctx);

	PositionRefType<?> getType();

}
