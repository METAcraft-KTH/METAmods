package nu.metacraft.fake_player_blocker.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import nu.metacraft.fake_player_blocker.RealPlayerStorage;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerConfigurationPacketListenerImpl.class)
public abstract class ServerConfigurationPacketListenerImplMixin extends ServerCommonPacketListenerImpl {

	public ServerConfigurationPacketListenerImplMixin(MinecraftServer server, Connection connection, CommonListenerCookie cookie) {
		super(server, connection, cookie);
	}

	@Shadow
	protected abstract @NotNull GameProfile playerProfile();

	@Inject(
		method = "handleConfigurationFinished",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/server/network/config/PrepareSpawnTask;spawnPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/network/CommonListenerCookie;)Lnet/minecraft/server/level/ServerPlayer;"
		)
	)
	public void handleConfigurationFinished(ServerboundFinishConfigurationPacket packet, CallbackInfo ci) {
		RealPlayerStorage.getInstance(server).addRealUsername(playerProfile().name());
	}

}
