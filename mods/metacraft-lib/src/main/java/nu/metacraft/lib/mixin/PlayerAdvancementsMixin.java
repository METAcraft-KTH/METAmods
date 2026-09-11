package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import nu.metacraft.lib.util.NoOpAdvancementTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.lib.extensions.ServerPlayerExtensions;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerAdvancements.class)
public class PlayerAdvancementsMixin {

	@Shadow private ServerPlayer player;

	@ModifyExpressionValue(
		method = "lambda$award$0",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/advancements/DisplayInfo;announceToChat()Z"
		)
	)
	public boolean grantCriterion(boolean original) {
		if (!((ServerPlayerExtensions) player).metacraft_lib$getAnnounceAdvancements()) {
			return false;
		}
		return original;
	}

	@Inject(method = "load", at = @At("HEAD"), cancellable = true)
	public void load(ServerAdvancementManager serverAdvancementManager, CallbackInfo ci) {
		if ((Object) this instanceof NoOpAdvancementTracker) {
			ci.cancel();
		}
	}

}
