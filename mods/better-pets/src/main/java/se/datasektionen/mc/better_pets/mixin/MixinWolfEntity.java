package se.datasektionen.mc.better_pets.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.better_pets.TameableExtension;
import se.datasektionen.mc.metacraft_lib.util.helper.TamedHelper;

@Mixin(WolfEntity.class)
public class MixinWolfEntity {

	@Inject(
			method = "canAttackWithOwner", at = @At("HEAD"), cancellable = true
	)
	public void canAttackWithOwner(LivingEntity target, LivingEntity owner, CallbackInfoReturnable<Boolean> cir) {
		if (target instanceof WolfEntity wolf && wolf.isTamed() && wolf.getOwner() != owner) {
			TamedHelper.getRelevantPlayer(target).ifPresent(t -> {
				if (((TameableExtension) this).metacraft$getTrustedPlayers().contains(t)) {
					cir.setReturnValue(false);
				}
			});
		}
	}

	@ModifyExpressionValue(
			method = "interactMob",
			at = @At(
					value = "INVOKE", target = "Lnet/minecraft/entity/passive/WolfEntity;isOwner(Lnet/minecraft/entity/LivingEntity;)Z"
			)
	)
	public boolean isTrusted(
			boolean original, @Local(argsOnly = true) PlayerEntity player, @Local(argsOnly = true) Hand hand, @Share("notOwner") LocalBooleanRef notOwner
	) {
		if (original) {
			return true;
		} else if (hand == Hand.MAIN_HAND) { //Check for main hand to prevent double interactions.
			notOwner.set(((TameableExtension) this).metaraft$isTrusted(player));
			return notOwner.get();
		}
		return false;
	}

	@ModifyArg(
			method = "interactMob",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/util/ActionResult;success(Z)Lnet/minecraft/util/ActionResult;"
			)
	)
	public boolean swingArm(boolean swingHand, @Share("notOwner") LocalBooleanRef notOwner) {
		return swingHand || notOwner.get();
	}

}
