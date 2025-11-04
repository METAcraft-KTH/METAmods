package nu.metacraft.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.cutscenes.extension.ServerPlayerEntityExtensions;
import nu.metacraft.cutscenes.util.helper.CutsceneHelper;

@Mixin(ServerGamePacketListenerImpl.class)
public class MixinServerPlayNetworkHandler {

	@Shadow public ServerPlayer player;

	@ModifyExpressionValue(
		method = "handleMovePlayer",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerPlayer;isChangingDimension()Z"
		)
	)
	public boolean onPlayerMove(boolean inTPState) {
		if (((ServerPlayerEntityExtensions) player).metacraft$getAllowWrongMovements()) {
			return true;
		}
		return inTPState;
	}

	@Inject(
		method = "shouldCheckPlayerMovement",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;getGameRules()Lnet/minecraft/world/level/GameRules;"
		),
		cancellable = true
	)
	private void shouldCheckMovement(boolean elytra, CallbackInfoReturnable<Boolean> cir) {
		if (player.getCamera() != null && CutsceneHelper.isInCutscene(player)) {
			cir.setReturnValue(false);
		}
	}

}
