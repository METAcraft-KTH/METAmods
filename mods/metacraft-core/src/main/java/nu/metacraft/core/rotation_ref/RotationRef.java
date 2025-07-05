package nu.metacraft.core.rotation_ref;

import net.minecraft.util.math.Vec2f;
import nu.metacraft.core.util.RefContext;

import java.util.Optional;

public interface RotationRef {

	//Important: Pitch is x and yaw is y!
	Optional<Vec2f> get(RefContext ctx);

	RotationRefType<?> getType();

}
