package nu.metacraft.better_pets.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.better_pets.TameableExtension;

@Mixin(Cat.class)
public class CatMixin {

	@ModifyExpressionValue(
		method = "mobInteract",
		at = @At(
			value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/Cat;isOwnedBy(Lnet/minecraft/world/entity/LivingEntity;)Z"
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
