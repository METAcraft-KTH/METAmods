package nu.metacraft.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.cutscenes.cutscene.MultiplayerCutsceneManager;
import nu.metacraft.cutscenes.util.helper.CutsceneHelper;

@Mixin(PlayerList.class)
public class PlayerListMixin {

	@Shadow @Final private MinecraftServer server;

	@Inject(method = "placeNewPlayer", at = @At("RETURN"))
	public void onPlayerConnect(Connection connection, ServerPlayer player, CommonListenerCookie clientData, CallbackInfo ci) {
		MultiplayerCutsceneManager.getInstance(server).onPlayerJoin(player);
	}

	@ModifyExpressionValue(
		method = "broadcastAll(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/resources/ResourceKey;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;dimension()Lnet/minecraft/resources/ResourceKey;"
		)
	)
	public ResourceKey<Level> sendToDimension(
			ResourceKey<Level> original,
			@Local(argsOnly = true) Packet<?> packet,
			@Local ServerPlayer player
	) {
		if (CutsceneHelper.isInCutscene(player)) {
			if (packet instanceof ClientboundSetTimePacket) {
				return null;
			}
			if (packet instanceof ClientboundGameEventPacket p) {
				if (p.getEvent() == ClientboundGameEventPacket.RAIN_LEVEL_CHANGE || p.getEvent() == ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE) {
					return null;
				}
			}
		}
		return original;
	}

	@WrapOperation(
			method = "broadcastAll(Lnet/minecraft/network/protocol/Packet;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"
			)
	)
	public void sendToDimension(
			ServerGamePacketListenerImpl instance, Packet<?> packet,
			Operation<Void> original, @Local ServerPlayer player
	) {
		if (CutsceneHelper.isInCutscene(player)) {
			if (packet instanceof ClientboundGameEventPacket p) {
				if (
						p.getEvent() == ClientboundGameEventPacket.START_RAINING ||
						p.getEvent() == ClientboundGameEventPacket.STOP_RAINING ||
						p.getEvent() == ClientboundGameEventPacket.RAIN_LEVEL_CHANGE ||
						p.getEvent() == ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE
				) {
					return;
				}
			}
		}
		original.call(instance, packet);
	}

}
