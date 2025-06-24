package se.datasektionen.mc.metacraft_season_4.mixin;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_season_4.extensions.LivingEntityExtensionsInternal;

@Mixin(PlayerEntity.class)
public class MixinPlayerEntity {

	@Inject(method = "applyDamage", at = @At("HEAD"))
	public void applyDamage(ServerWorld world, DamageSource source, float amount, CallbackInfo ci) {
		((LivingEntityExtensionsInternal) this).metacraft_season_4$setPrevDamageAmount(0);
	}

	@ModifyArg(
			method = "applyDamage",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/damage/DamageTracker;onDamage(Lnet/minecraft/entity/damage/DamageSource;F)V"
			),
			index = 1
	)
	public float applyDamage(float damage) {
		((LivingEntityExtensionsInternal) this).metacraft_season_4$setPrevDamageAmount(damage);
		return damage;
	}

}
