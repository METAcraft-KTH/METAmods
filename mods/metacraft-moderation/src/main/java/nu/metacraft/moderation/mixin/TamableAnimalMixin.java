package nu.metacraft.moderation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.moderation.ModerationPlayerData;

@Mixin(TamableAnimal.class)
public class TamableAnimalMixin {

	@WrapOperation(
			method = "unableToMoveToOwner",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;isSpectator()Z"
			)
	)
	public boolean cannotFollowOwner(LivingEntity owner, Operation<Boolean> original) {
		if (owner instanceof ModerationPlayerData player) {
			if (
					player.METAcraft_Moderation$getModerationMode().map(
							mode -> !mode.getDef().followedByTamedMobs()
					).orElse(false)
			) {
				return true;
			}
		}
		return original.call(owner);
	}

}
