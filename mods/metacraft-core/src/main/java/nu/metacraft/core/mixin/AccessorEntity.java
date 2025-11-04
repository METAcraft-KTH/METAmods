package nu.metacraft.core.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface AccessorEntity {
	@Invoker
	Vec3 callCollide(Vec3 movement);

	@Invoker
	Vec3 callMaybeBackOffFromEdge(Vec3 movement, MoverType type);
}
