package nu.metacraft.core.mixin;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import nu.metacraft.core.compat.Vanish;
import nu.metacraft.lib.compat.IsLoaded;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.core.block.blocks.BlockWithDisguise;
import nu.metacraft.core.util.helper.MusicHelper;

@Mixin(value = ServerCommonPacketListenerImpl.class, priority = 0)
public abstract class ServerCommonPacketListenerImplMixin {

	@Shadow
	@Final
	protected MinecraftServer server;

	@Shadow
	public abstract void send(Packet<?> packet);

	@SuppressWarnings("ConstantValue")
	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"))
	public void fixTrapBlock(Packet<?> packet, ChannelFutureListener channelFutureListener, CallbackInfo ci) {
		//Required to fix flickering whenever player interacts in vicinity of trap block.
		if ((Object) this instanceof ServerGamePacketListenerImpl play && packet instanceof ClientboundBlockUpdatePacket blockUpdate) {
			if (blockUpdate.getBlockState().getBlock() instanceof BlockWithDisguise disguised) {
				disguised.getBlockEntity(play.player.level(), blockUpdate.getPos()).ifPresent(entity -> {
					((ClientboundBlockUpdatePacketAccessor) blockUpdate).setBlockState(entity.getDisplayedBlockState());
				});
			}
		}

		// Required to fix metacraft:player always invisible when vanish is installed.
		if (
				(Object) this instanceof ServerGamePacketListenerImpl listener && IsLoaded.VANISH.isLoaded() &&
				packet instanceof ClientboundPlayerInfoUpdatePacket playerListPacket
		) {
			Vanish.vanishDoNotHideMETAcraftPlayerMob(server, playerListPacket, listener, this::send);
		}
	}

	@Unique
	private int lastResourcePackTime = -1;

	@Inject(method = "handleResourcePackResponse", at = @At("RETURN"))
	public void onResourcePackStatus(ServerboundResourcePackPacket packet, CallbackInfo ci) {
		if ((Object) this instanceof ServerGamePacketListenerImpl h) {
			if (packet.action() == ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED) {
				int time = h.getPlayer().tickCount;
				if (time <= lastResourcePackTime) {
					return;
				}
				lastResourcePackTime = time;
				MusicHelper.resetMusicTimer(h.getPlayer());
			}
		}
	}

}
