package se.datasektionen.mc.metacraft_moderation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.metacraft_moderation.ModerationPlayerData;

@Mixin(TameableEntity.class)
public class MixinTameableEntity {

	@WrapOperation(
			method = "cannotFollowOwner",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/LivingEntity;isSpectator()Z"
			)
	)
	public boolean cannotFollowOwner(LivingEntity owner, Operation<Boolean> original) {
		if (owner instanceof ModerationPlayerData player) {
			if (
					player.METAcraft_Moderation$getModerationMode().map(
							mode -> mode.getDef().preventTamedMobFollow()
					).orElse(false)
			) {
				return true;
			}
		}
		return original.call(owner);
	}

}
