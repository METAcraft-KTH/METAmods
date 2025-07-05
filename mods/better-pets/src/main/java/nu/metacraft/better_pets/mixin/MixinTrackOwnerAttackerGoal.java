package nu.metacraft.better_pets.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.TrackOwnerAttackerGoal;
import net.minecraft.entity.passive.TameableEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.better_pets.TameableExtension;

@Mixin(TrackOwnerAttackerGoal.class)
public class MixinTrackOwnerAttackerGoal {

	@WrapOperation(
		method = "canStart",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/passive/TameableEntity;getOwner()Lnet/minecraft/entity/LivingEntity;"
		)
	)
	public LivingEntity checkFollowTarget(TameableEntity instance, Operation<LivingEntity> original) {
		return ((TameableExtension) instance).metacraft$getCurrentFollowTarget();
	}

	@WrapOperation(
			method = "start",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/passive/TameableEntity;getOwner()Lnet/minecraft/entity/LivingEntity;"
			)
	)
	public LivingEntity checkFollowTarget2(TameableEntity instance, Operation<LivingEntity> original) {
		return ((TameableExtension) instance).metacraft$getCurrentFollowTarget();
	}

}
