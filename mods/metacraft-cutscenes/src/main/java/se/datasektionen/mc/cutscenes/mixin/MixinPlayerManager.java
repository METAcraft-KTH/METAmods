package se.datasektionen.mc.cutscenes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
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
			target = "Lnet/minecraft/world/World;getRegistryKey()Lnet/minecraft/registry/RegistryKey;"
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
		}
		return original;
	}

}
