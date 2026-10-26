package se.metacraft.nether_rifts.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Strider;
import se.metacraft.nether_rifts.raid.RiftMobs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A rift's mobs never hurt each other: the hit doesn't land, so no knockback, no hurt flash and no anger (a piglin's
 * fleeing, a hoglin's retaliation) follow it. And a rift's strider shrugs off the overworld's rain (it would kill one
 * in 10 seconds), though water still hurts it as it hurts any strider.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void netherRifts$spareOwnSide(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		if (RiftMobs.spares((LivingEntity) (Object) this, source)) {
			cir.setReturnValue(false);
		}
	}

	@WrapOperation(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isInWaterOrRain()Z"))
	private boolean netherRifts$riftStridersShrugOffRain(LivingEntity entity, Operation<Boolean> original) {
		if (entity instanceof Strider && RiftMobs.isRiftMob(entity)) {
			return entity.isInWater();
		}
		return original.call(entity);
	}
}
