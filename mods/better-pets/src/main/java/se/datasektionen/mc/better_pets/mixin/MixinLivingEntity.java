package se.datasektionen.mc.better_pets.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.ParrotEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class MixinLivingEntity {

	@Inject(method = "getScaleFactor", at = @At("HEAD"), cancellable = true)
	public void getScaleFactor(CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ParrotEntity) {
			cir.setReturnValue(1.0f);
		}
	}

}
