package se.datasektionen.mc.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.cutscenes.cutscene.MultiplayerCutsceneManager;
import se.datasektionen.mc.cutscenes.util.helper.CutsceneHelper;

@Mixin(PlayerManager.class)
public class MixinPlayerManager {

	@Shadow @Final private MinecraftServer server;

	@Inject(method = "onPlayerConnect", at = @At("RETURN"))
	public void onPlayerConnect(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData clientData, CallbackInfo ci) {
		MultiplayerCutsceneManager.getInstance(server).onPlayerJoin(player);
	}

	@ModifyExpressionValue(
		method = "sendToDimension",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/world/ServerWorld;getRegistryKey()Lnet/minecraft/registry/RegistryKey;"
		)
	)
	public RegistryKey<World> sendToDimension(
			RegistryKey<World> original,
			@Local(argsOnly = true) Packet<?> packet,
			@Local ServerPlayerEntity player
	) {
		if (CutsceneHelper.isInCutscene(player)) {
			if (packet instanceof WorldTimeUpdateS2CPacket) {
				return null;
			}
			if (packet instanceof GameStateChangeS2CPacket p) {
				if (p.getReason() == GameStateChangeS2CPacket.RAIN_GRADIENT_CHANGED || p.getReason() == GameStateChangeS2CPacket.THUNDER_GRADIENT_CHANGED) {
					return null;
				}
			}
		}
		return original;
	}

	@WrapOperation(
			method = "sendToAll",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"
			)
	)
	public void sendToDimension(
			ServerPlayNetworkHandler instance, Packet<?> packet,
			Operation<Void> original, @Local ServerPlayerEntity player
	) {
		if (CutsceneHelper.isInCutscene(player)) {
			if (packet instanceof GameStateChangeS2CPacket p) {
				if (
						p.getReason() == GameStateChangeS2CPacket.RAIN_STARTED ||
						p.getReason() == GameStateChangeS2CPacket.RAIN_STOPPED ||
						p.getReason() == GameStateChangeS2CPacket.RAIN_GRADIENT_CHANGED ||
						p.getReason() == GameStateChangeS2CPacket.THUNDER_GRADIENT_CHANGED
				) {
					return;
				}
			}
		}
		original.call(instance, packet);
	}

}
