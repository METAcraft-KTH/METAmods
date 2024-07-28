package se.datasektionen.mc.better_pets.mixin;

import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.better_pets.TameableExtension;

@Mixin(AnimalEntity.class)
public class MixinAnimalEntity {

	@Inject(method = "interactMob", at = @At("HEAD"))
	public void interactMob(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		if ((Object) this instanceof TameableExtension tameable && player instanceof ServerPlayerEntity p) {
			tameable.metacraft$setCurrentFollowTarget(p);
		}
	}

	@Inject(method = "mobTick", at = @At("RETURN"))
	public void mobTick(CallbackInfo ci) {
		if ((Object) this instanceof TameableExtension tameable) {
			tameable.metacraft$tick();
		}
	}

}
