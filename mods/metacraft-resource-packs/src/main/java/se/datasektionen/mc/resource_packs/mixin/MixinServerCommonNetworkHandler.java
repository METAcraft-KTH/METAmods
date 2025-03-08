package se.datasektionen.mc.resource_packs.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.resource_packs.PlayerPackDataManager;

@Mixin(ServerCommonNetworkHandler.class)
public abstract class MixinServerCommonNetworkHandler {

	@Shadow @Final protected MinecraftServer server;

	@Shadow protected abstract GameProfile getProfile();

	@Inject(method = "onDisconnected", at = @At("RETURN"))
	public void onDisconnected(DisconnectionInfo info, CallbackInfo ci) {
		PlayerPackDataManager.getInstance(server).unloadPlayer(getProfile());
	}

}
