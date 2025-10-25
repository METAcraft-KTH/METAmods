package nu.metacraft.core.mixin;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
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

@Mixin(value = ServerCommonNetworkHandler.class, priority = 0)
public abstract class MixinServerCommonNetworkHandler {

	@Shadow
	@Final
	protected MinecraftServer server;

	@Shadow
	public abstract void sendPacket(Packet<?> packet);

	@SuppressWarnings("ConstantValue")
	@Inject(method = "send", at = @At("HEAD"))
	public void fixTrapBlock(Packet<?> packet, ChannelFutureListener channelFutureListener, CallbackInfo ci) {
		//Required to fix flickering whenever player interacts in vicinity of trap block.
		if ((Object) this instanceof ServerPlayNetworkHandler play && packet instanceof BlockUpdateS2CPacket blockUpdate) {
			if (blockUpdate.getState().getBlock() instanceof BlockWithDisguise disguised) {
				disguised.getBlockEntity(play.player.getEntityWorld(), blockUpdate.getPos()).ifPresent(entity -> {
					((AccessorBlockUpdateS2CPacket) blockUpdate).setState(entity.getBlockState());
				});
			}
		}

		// Required to fix metacraft:player always invisible when vanish is installed.
		if (
				(Object) this instanceof ServerPlayNetworkHandler listener && IsLoaded.VANISH.isLoaded() &&
				packet instanceof PlayerListS2CPacket playerListPacket
		) {
			Vanish.vanishDoNotHideMETAcraftPlayerMob(server, playerListPacket, listener, this::sendPacket);
		}
	}

	@Unique
	private int lastResourcePackTime = -1;

	@Inject(method = "onResourcePackStatus", at = @At("RETURN"))
	public void onResourcePackStatus(ResourcePackStatusC2SPacket packet, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayNetworkHandler h) {
			if (packet.status() == ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED) {
				int time = h.getPlayer().age;
				if (time <= lastResourcePackTime) {
					return;
				}
				lastResourcePackTime = time;
				MusicHelper.resetMusicTimer(h.getPlayer());
			}
		}
	}

}
