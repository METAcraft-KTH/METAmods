package nu.metacraft.revival.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import nu.metacraft.revival.extension.ServerPlayerExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin extends Avatar {

	protected PlayerMixin(EntityType<? extends LivingEntity> entityType, Level level) {
		super(entityType, level);
	}

	@Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
	public void updatePlayerPose(CallbackInfo ci) {
		if (this instanceof ServerPlayerExtension ext && ext.metacraft$isUnconscious()) {
			setPose(Pose.SWIMMING);
			ci.cancel();
		}
	}

	@ModifyReturnValue(method = "isImmobile", at = @At("RETURN"))
	protected boolean isImmobile(boolean original) {
		if (this instanceof ServerPlayerExtension ext && ext.metacraft$isUnconscious()) {
			return true;
		}
		return original;
	}

	@Inject(method = "interactOn", at = @At("RETURN"), cancellable = true)
	public void interactOn(
			Entity entityToInteractOn, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir
	) {
		if (this instanceof ServerPlayerExtension ext && ext.metacraft$isUnconscious()) {
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}

	@Inject(method = "attack", at = @At("RETURN"), cancellable = true)
	public void interactOn(
			Entity target, CallbackInfo ci
	) {
		if (this instanceof ServerPlayerExtension ext && ext.metacraft$isUnconscious()) {
			ci.cancel();
		}
	}

}
