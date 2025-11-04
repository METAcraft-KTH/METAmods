package nu.metacraft.resource_packs.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.resource_packs.PlayerPackDataManager;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {

	@Shadow @Final protected MinecraftServer server;

	@Shadow protected abstract GameProfile playerProfile();

	@Inject(method = "onDisconnect", at = @At("RETURN"))
	public void onDisconnected(DisconnectionDetails info, CallbackInfo ci) {
		PlayerPackDataManager.getInstance(server).unloadPlayer(playerProfile());
	}

}
