package nu.metacraft.better_pets.mixin;

import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import nu.metacraft.better_pets.TrustPlayerSelector;
import nu.metacraft.core.gui.MultiplePlayerSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.better_pets.TameableExtension;

@Mixin(AnimalEntity.class)
public class MixinAnimalEntity {

	@SuppressWarnings("ConstantValue")
	@Inject(method = "interactMob", at = @At("HEAD"))
	public void interactMob(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		if ((Object) this instanceof TameableEntity tameable && tameable.isTamed() && player instanceof ServerPlayerEntity p) {
			TameableExtension extension = (TameableExtension) tameable;
			if (tameable.isOwner(player) || extension.metaraft$isTrusted(player)) {
				extension.metacraft$setCurrentFollowTarget(p);
			}
		}
	}

	@SuppressWarnings("ConstantValue")
	@Inject(method = "interactMob", at = @At("TAIL"), cancellable = true)
	public void openGUI(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		if ((Object) this instanceof TameableEntity tameable && tameable.isTamed() && player instanceof ServerPlayerEntity p) {
			TameableExtension extension = (TameableExtension) tameable;
			if (tameable.isOwner(player) && player.isSneaking()) {
				MultiplePlayerSelector selector = new TrustPlayerSelector(p, extension);
				selector.open();
				cir.setReturnValue(ActionResult.SUCCESS_SERVER);
			}
		}
	}

	@Inject(method = "mobTick", at = @At("RETURN"))
	public void mobTick(CallbackInfo ci) {
		if ((Object) this instanceof TameableExtension tameable) {
			tameable.metacraft$tick();
		}
	}

}
