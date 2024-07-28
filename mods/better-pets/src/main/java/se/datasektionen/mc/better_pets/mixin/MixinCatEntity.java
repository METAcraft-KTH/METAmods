package se.datasektionen.mc.better_pets.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import se.datasektionen.mc.better_pets.TameableExtension;

@Mixin(CatEntity.class)
public class MixinCatEntity {

	@ModifyExpressionValue(
		method = "interactMob",
		at = @At(
			value = "INVOKE", target = "Lnet/minecraft/entity/passive/CatEntity;isOwner(Lnet/minecraft/entity/LivingEntity;)Z"
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
