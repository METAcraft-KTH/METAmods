package se.metacraft.kth_auth.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.metacraft.kth_auth.KTHAuthTask;
import se.metacraft.kth_auth.PendingDisconnect;

@Mixin(ServerCommonPacketListenerImpl.class)
public class ServerCommonPacketListenerImplMixin implements PendingDisconnect {
	@Unique
	private volatile Component pendingDisconnectReason;

	@Override
	public void kth_auth$disconnectOnPong(Component reason) {
		this.pendingDisconnectReason = reason;
	}

	@Inject(method = "handlePong", at = @At("HEAD"))
	private void handlePong(ServerboundPongPacket packet, CallbackInfo ci) {
		if (packet.getId() == KTHAuthTask.DISCONNECT_PING_ID && this.pendingDisconnectReason != null) {
			((ServerCommonPacketListenerImpl) (Object) this).disconnect(this.pendingDisconnectReason);
		}
	}
}
