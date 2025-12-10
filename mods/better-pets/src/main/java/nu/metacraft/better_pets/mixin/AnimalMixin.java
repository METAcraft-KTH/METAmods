package nu.metacraft.better_pets.mixin;

import nu.metacraft.better_pets.TrustPlayerSelector;
import nu.metacraft.core.gui.MultiplePlayerSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import nu.metacraft.better_pets.TameableExtension;

@Mixin(Animal.class)
public class AnimalMixin {

	@SuppressWarnings("ConstantValue")
	@Inject(method = "mobInteract", at = @At("HEAD"))
	public void interactMob(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if ((Object) this instanceof TamableAnimal tameable && tameable.isTame() && player instanceof ServerPlayer p) {
			TameableExtension extension = (TameableExtension) tameable;
			if (tameable.isOwnedBy(player) || extension.metaraft$isTrusted(player)) {
				extension.metacraft$setCurrentFollowTarget(p);
			}
		}
	}

	@SuppressWarnings("ConstantValue")
	@Inject(method = "mobInteract", at = @At("TAIL"), cancellable = true)
	public void openGUI(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if ((Object) this instanceof TamableAnimal tameable && tameable.isTame() && player instanceof ServerPlayer p) {
			TameableExtension extension = (TameableExtension) tameable;
			if (tameable.isOwnedBy(player) && player.isShiftKeyDown()) {
				MultiplePlayerSelector selector = new TrustPlayerSelector(p, extension);
				selector.open();
				cir.setReturnValue(InteractionResult.SUCCESS_SERVER);
			}
		}
	}

	@Inject(method = "customServerAiStep", at = @At("RETURN"))
	public void mobTick(CallbackInfo ci) {
		if ((Object) this instanceof TameableExtension tameable) {
			tameable.metacraft$tick();
		}
	}

}
