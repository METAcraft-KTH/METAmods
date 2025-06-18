package se.datasektionen.mc.cutscenes.rotation_ref;

import net.minecraft.util.math.Vec2f;
import se.datasektionen.mc.cutscenes.util.RefContext;

import java.util.Optional;

public interface RotationRef {

	//Important: Pitch is x and yaw is y!
	Optional<Vec2f> get(RefContext ctx);

	RotationRefType<?> getType();

}
