package nu.metacraft.better_pets.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Ownable;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.better_pets.TameableExtension;

@Mixin(LivingEntity.class)
public class MixinLivingEntity {

	@Inject(method = "getScaleFactor", at = @At("HEAD"), cancellable = true)
	public void getScaleFactor(CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ParrotEntity) {
			cir.setReturnValue(1.0f);
		}
	}


	@Inject(
		method = "damage",
		at = @At("HEAD"),
		cancellable = true
	)
	public void damage(
			ServerWorld world, DamageSource source,
			float amount, CallbackInfoReturnable<Boolean> cir
	) {
		if ((Object) this instanceof TameableEntity tameable) {
			var ext = (TameableExtension) tameable;
			var entity = source.getAttacker();
			if (entity instanceof Ownable ownable) {
				var owner = ownable.getOwner();
				if (owner != null) {
					entity = owner;
				}
			}
			if (entity instanceof LivingEntity living) {
				if (tameable.isOwner(living) || ext.metaraft$isTrusted(living)) {
					cir.setReturnValue(false);
				}
			}
		}
	}

}
