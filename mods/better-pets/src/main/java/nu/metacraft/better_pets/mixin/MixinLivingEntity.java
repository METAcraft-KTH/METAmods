package nu.metacraft.better_pets.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.animal.Parrot;
import nu.metacraft.better_pets.TameableExtension;

@Mixin(LivingEntity.class)
public class MixinLivingEntity {

	@Inject(method = "getAgeScale", at = @At("HEAD"), cancellable = true)
	public void getScaleFactor(CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof Parrot) {
			cir.setReturnValue(1.0f);
		}
	}


	@Inject(
		method = "hurtServer",
		at = @At("HEAD"),
		cancellable = true
	)
	public void damage(
			ServerLevel world, DamageSource source,
			float amount, CallbackInfoReturnable<Boolean> cir
	) {
		if ((Object) this instanceof TamableAnimal tameable) {
			var ext = (TameableExtension) tameable;
			var entity = source.getEntity();
			if (entity instanceof TraceableEntity ownable) {
				var owner = ownable.getOwner();
				if (owner != null) {
					entity = owner;
				}
			}
			if (entity instanceof LivingEntity living) {
				if (tameable.isOwnedBy(living) || ext.metaraft$isTrusted(living)) {
					cir.setReturnValue(false);
				}
			}
		}
	}

}
