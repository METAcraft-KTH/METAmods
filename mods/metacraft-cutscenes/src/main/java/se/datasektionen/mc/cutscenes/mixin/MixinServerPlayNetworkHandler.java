package se.datasektionen.mc.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.cutscenes.extension.ServerPlayerEntityExtensions;
import se.datasektionen.mc.cutscenes.util.helper.CutsceneHelper;

@Mixin(ServerPlayNetworkHandler.class)
public class MixinServerPlayNetworkHandler {

	@Shadow public ServerPlayerEntity player;

	@ModifyExpressionValue(
		method = "onPlayerMove",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerPlayerEntity;isInTeleportationState()Z"
		)
	)
	public boolean onPlayerMove(boolean inTPState) {
		if (((ServerPlayerEntityExtensions) player).metacraft$getAllowWrongMovements()) {
			return true;
		}
		return inTPState;
	}

	@Inject(
		method = "shouldCheckMovement",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/world/ServerWorld;getGameRules()Lnet/minecraft/world/GameRules;"
		),
		cancellable = true
	)
	private void shouldCheckMovement(boolean elytra, CallbackInfoReturnable<Boolean> cir) {
		if (player.getCameraEntity() != null && CutsceneHelper.isInCutscene(player)) {
			cir.setReturnValue(false);
		}
	}

}
