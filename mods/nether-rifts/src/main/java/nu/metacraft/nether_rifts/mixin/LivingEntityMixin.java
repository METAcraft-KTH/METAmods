package nu.metacraft.nether_rifts.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Strider;
import nu.metacraft.nether_rifts.raid.RiftMobs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A rift's strider shrugs off the overworld's rain (it would kill one in 10 seconds), though water still hurts it as it
 * hurts any strider.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

	@WrapOperation(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isInWaterOrRain()Z"))
	private boolean netherRifts$riftStridersShrugOffRain(LivingEntity entity, Operation<Boolean> original) {
		if (entity instanceof Strider && RiftMobs.isRiftMob(entity)) {
			return entity.isInWater();
		}
		return original.call(entity);
	}
}
