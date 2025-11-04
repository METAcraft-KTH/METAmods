package nu.metacraft.core.rotation_ref;

import nu.metacraft.core.util.RefContext;

import java.util.Optional;
import net.minecraft.world.phys.Vec2;

public interface RotationRef {

	//Important: Pitch is x and yaw is y!
	Optional<Vec2> get(RefContext ctx);

	RotationRefType<?> getType();

}
