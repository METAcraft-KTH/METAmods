package metacraft.ovvar.mixin;

import metacraft.ovvar.pack.Combos;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When a player finishes loading a (re-sent) resource pack, the ovvar they can see are drawn
 * again with the assets that pack holds. Vanilla runs this on the server thread.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ResourcePackResponseMixin {
	@Inject(method = "handleResourcePackResponse", at = @At("TAIL"))
	private void ovvar$packLoaded(ServerboundResourcePackPacket packet, CallbackInfo ci) {
		if (packet.action() == ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED
				&& (Object) this instanceof ServerGamePacketListenerImpl game) {
			Combos.packLoaded(game.player);
		}
	}
}
