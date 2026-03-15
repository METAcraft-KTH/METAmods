package nu.metacraft.player_specific_scoreboards.mixin;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.scores.DisplaySlot;
import nu.metacraft.player_specific_scoreboards.PlayerScoreboardExtension;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public class ServerCommonPacketListenerImplMixin {

	@Inject(
			method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
			at = @At("HEAD"),
			cancellable = true
	)
	public void onSetDisplay(Packet<?> packet, @Nullable ChannelFutureListener channelFutureListener, CallbackInfo ci) {
		//noinspection ConstantValue
		if ((Object) this instanceof ServerGamePacketListenerImpl impl && ((PlayerScoreboardExtension) impl.player).metacraft$getExistsClientside()) {
			if (packet instanceof ClientboundSetDisplayObjectivePacket displayPacket && displayPacket.getSlot() == DisplaySlot.SIDEBAR) {
				ci.cancel();
			}
		}
	}

}
