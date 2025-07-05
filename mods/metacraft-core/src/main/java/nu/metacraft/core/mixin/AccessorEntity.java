package nu.metacraft.core.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface AccessorEntity {
	@Invoker
	Vec3d callAdjustMovementForCollisions(Vec3d movement);

	@Invoker
	Vec3d callAdjustMovementForSneaking(Vec3d movement, MovementType type);
}
