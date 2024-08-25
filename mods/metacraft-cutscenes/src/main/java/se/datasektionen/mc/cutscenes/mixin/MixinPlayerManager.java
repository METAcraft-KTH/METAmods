package se.datasektionen.mc.cutscenes.mixin;

import net.minecraft.network.ClientConnection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.cutscenes.cutscene.MultiplayerCutsceneManager;

@Mixin(PlayerManager.class)
public class MixinPlayerManager {

	@Shadow @Final private MinecraftServer server;

	@Inject(method = "onPlayerConnect", at = @At("RETURN"))
	public void onPlayerConnect(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData clientData, CallbackInfo ci) {
		MultiplayerCutsceneManager.getInstance(server).onPlayerJoin(player);
	}

}
