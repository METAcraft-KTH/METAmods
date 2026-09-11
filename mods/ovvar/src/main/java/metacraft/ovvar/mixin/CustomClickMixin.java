package metacraft.ovvar.mixin;

import metacraft.ovvar.sewing.SewingGame;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dialog buttons with a custom click action reach the server here, with no player attached by
 * the time vanilla hands them to {@code MinecraftServer.handleCustomClickAction}. The stitching
 * dialog's clicks are taken on the server thread (after vanilla's thread check) for the player
 * whose connection this is, and go no further.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class CustomClickMixin {
	@Inject(method = "handleCustomClickAction",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;handleCustomClickAction(Lnet/minecraft/resources/Identifier;Ljava/util/Optional;)V"),
			cancellable = true)
	private void ovvar$customClick(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
		if ((Object) this instanceof ServerGamePacketListenerImpl game && SewingGame.click(game.player, packet.id(), packet.payload())) {
			ci.cancel();
		}
	}
}
