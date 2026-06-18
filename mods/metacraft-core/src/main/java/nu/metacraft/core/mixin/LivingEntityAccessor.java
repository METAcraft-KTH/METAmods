package nu.metacraft.core.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.throwables.MixinException;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

	@Invoker
	float callGetJumpPower();

	@Invoker
	static float callComputeModifiedFriction(final float friction, final float modifier) {
		throw new MixinException("Failure");
	}



}
