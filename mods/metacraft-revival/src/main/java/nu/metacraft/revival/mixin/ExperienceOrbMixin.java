package nu.metacraft.revival.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import nu.metacraft.revival.RevivalConfig;
import nu.metacraft.revival.extension.ServerPlayerExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceOrb.class)
public class ExperienceOrbMixin {

	@Unique
	private boolean canPickUp(Player player) {
		if (player instanceof ServerPlayerExtension ext && ext.metacraft$isUnconscious()) {
			return RevivalConfig.getConfig().xpPickup();
		}
		return true;
	}

	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	public void playerTouch(Player player, CallbackInfo ci) {
		if (!canPickUp(player)) {
			ci.cancel();
		}
	}

	@WrapOperation(
			method = "followNearbyPlayer",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/player/Player;isSpectator()Z"
			)
	)
	public boolean followNearbyPlayer(Player player, Operation<Boolean> original) {
		if (!canPickUp(player)) {
			return true;
		}
		return original.call(player);
	}

}
