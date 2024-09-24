package se.datasektionen.mc.metacraft_core.mixin;

import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_core.item.components.CommandComponents;

@Mixin(ServerPlayNetworkHandler.class)
public class MixinServerPlayNetworkHandler {

	@Shadow public ServerPlayerEntity player;

	@Inject(method = "onHandSwing", at = @At("RETURN"))
	public void onHandSwing(HandSwingC2SPacket packet, CallbackInfo ci) {
		if (packet.getHand() == Hand.MAIN_HAND) {
			var stack = player.getStackInHand(packet.getHand());
			if (stack.contains(CommandComponents.MAIN_HAND_SWING_COMMAND)) {
				CommandComponents.runCommand(player, player.getPos(), stack.get(CommandComponents.MAIN_HAND_SWING_COMMAND));
			}
		}
	}

}
