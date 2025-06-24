package se.datasektionen.mc.metacraft_core.rotation_ref;

import net.minecraft.util.math.Vec2f;
import se.datasektionen.mc.metacraft_core.util.RefContext;

import java.util.Optional;

public interface RotationRef {

	//Important: Pitch is x and yaw is y!
	Optional<Vec2f> get(RefContext ctx);

	RotationRefType<?> getType();

}
