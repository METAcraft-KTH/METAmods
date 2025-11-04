package nu.metacraft.better_pets.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.better_pets.TameableExtension;
import nu.metacraft.lib.util.helper.TamedHelper;

@Mixin(Wolf.class)
public class MixinWolfEntity {

	@Inject(
			method = "wantsToAttack", at = @At("HEAD"), cancellable = true
	)
	public void canAttackWithOwner(LivingEntity target, LivingEntity owner, CallbackInfoReturnable<Boolean> cir) {
		if (target instanceof Wolf wolf && wolf.isTame() && wolf.getOwner() != owner) {
			TamedHelper.getRelevantPlayer(target).ifPresent(t -> {
				if (((TameableExtension) this).metacraft$getTrustedPlayers().contains(t)) {
					cir.setReturnValue(false);
				}
			});
		}
	}

	@ModifyExpressionValue(
			method = "mobInteract",
			at = @At(
					value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/wolf/Wolf;isOwnedBy(Lnet/minecraft/world/entity/LivingEntity;)Z"
			)
	)
	public boolean isTrusted(
			boolean original, @Local(argsOnly = true) Player player, @Local(argsOnly = true) InteractionHand hand, @Share("notOwner") LocalBooleanRef notOwner
	) {
		if (original) {
			return true;
		} else if (hand == InteractionHand.MAIN_HAND) { //Check for main hand to prevent double interactions.
			notOwner.set(((TameableExtension) this).metaraft$isTrusted(player));
			return notOwner.get();
		}
		return false;
	}

	@ModifyExpressionValue(
		method = "mobInteract",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/world/InteractionResult;SUCCESS:Lnet/minecraft/world/InteractionResult$Success;"
		)
	)
	public InteractionResult.Success swingArm(InteractionResult.Success original, @Share("notOwner") LocalBooleanRef notOwner) {
		if (notOwner.get()) {
			return InteractionResult.SUCCESS_SERVER;
		}
		return original;
	}

}
