package se.datasektionen.mc.cutscenes.mixin;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.cutscenes.transitions.DeltaTickTransition;
import se.datasektionen.mc.cutscenes.util.helper.CutsceneHelper;

@Mixin(ServerPlayNetworkHandler.class)
public class MixinServerPlayNetworkHandler {

	@Shadow public ServerPlayerEntity player;

	@Inject(
		method = "onPlayerMove",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;disconnect(Lnet/minecraft/text/Text;)V"
		),
		cancellable = true
	)
	public void preventInvalidMoveDisconnectDuringCutscenes(PlayerMoveC2SPacket packet, CallbackInfo ci) {
		var scene = CutsceneHelper.getCutscene(player);
		if (scene.isPresent() && scene.get().getTransitions().getValuesAt(scene.get().getCurrentTime()).anyMatch(t -> t instanceof DeltaTickTransition)) {
			ci.cancel();
		}
	}

}
