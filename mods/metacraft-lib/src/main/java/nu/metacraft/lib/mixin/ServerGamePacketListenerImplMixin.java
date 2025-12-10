package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.lib.extensions.ServerPlayerExtensions;
import nu.metacraft.lib.util.helper.PlayerDataHelper;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {

	@Shadow public ServerPlayer player;

	@WrapWithCondition(
		method = "removePlayerFromWorld",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"
		)
	)
	private boolean shouldAnnounceLeave(PlayerList instance, Component message, boolean overlay) {
		return PlayerDataHelper.getAnnounceJoinLeave(this.player);
	}

	@ModifyExpressionValue(
		method = "handleMoveVehicle",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;hasClientLoaded()Z")
	)
	public boolean onVehicleMove(boolean original) {
		if (((ServerPlayerExtensions) player).metacraft_lib$isTeleportingOnVehicle()) {
			return false;
		}
		return original;
	}

}
