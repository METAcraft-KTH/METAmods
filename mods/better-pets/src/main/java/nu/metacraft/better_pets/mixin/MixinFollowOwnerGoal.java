package nu.metacraft.better_pets.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.better_pets.TameableExtension;

@Mixin(FollowOwnerGoal.class)
public class MixinFollowOwnerGoal {

	@WrapOperation(
		method = "canUse",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/TamableAnimal;getOwner()Lnet/minecraft/world/entity/LivingEntity;"
		)
	)
	public LivingEntity checkFollowTarget(TamableAnimal instance, Operation<LivingEntity> original) {
		return ((TameableExtension) instance).metacraft$getCurrentFollowTarget();
	}

}
