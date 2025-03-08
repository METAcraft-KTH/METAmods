package se.datasektionen.mc.resource_packs.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.*;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.resource_packs.PlayerPackDataManager;
import se.datasektionen.mc.resource_packs.ResourcePackConfig;
import se.datasektionen.mc.resource_packs.ResourcePackHelper;

import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;

@Mixin(ServerConfigurationNetworkHandler.class)
public abstract class MixinServerConfigurationNetworkHandler extends ServerCommonNetworkHandler {

	@Shadow @Final private Queue<ServerPlayerConfigurationTask> tasks;

	@Shadow @Final private GameProfile profile;
	@Unique
	private boolean receivedResourcePack = false;

	public MixinServerConfigurationNetworkHandler(MinecraftServer server, ClientConnection connection, ConnectedClientData clientData) {
		super(server, connection, clientData);
	}

	@Inject(method = "<init>", at = @At("RETURN"))
	public void init(MinecraftServer minecraftServer, ClientConnection clientConnection, ConnectedClientData connectedClientData, CallbackInfo ci) {
		PlayerPackDataManager.getInstance(minecraftServer).loadPlayer(profile);
	}

	@WrapWithCondition(
		method = "onResourcePackStatus",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerConfigurationNetworkHandler;onTaskFinished(Lnet/minecraft/server/network/ServerPlayerConfigurationTask$Key;)V"
		)
	)
	public boolean onResourcePackStatus(ServerConfigurationNetworkHandler instance, ServerPlayerConfigurationTask.Key key) {
		if (receivedResourcePack) {
			return false;
		}
		receivedResourcePack = true;
		return true;
	}

	@Inject(method = "queueSendResourcePackTask", at = @At("RETURN"))
	public void sendPacket(CallbackInfo ci) {
		var config = ResourcePackConfig.getConfig();
		var packs = config.getResourcePacks().stream().filter(
				entry -> ResourcePackHelper.hasResourcePack(server, profile, entry.getKey(), entry.getValue())
		).map(Map.Entry::getKey).toList();
		if (!packs.isEmpty()) {
			this.tasks.add(new ServerPlayerConfigurationTask() {
				@Override
				public void sendPacket(Consumer<Packet<?>> sender) {
					for (var pack : packs) {
						sender.accept(config.createEnablePacket(pack));
					}
				}

				@Override
				public Key getKey() {
					return SendResourcePackTask.KEY;
				}
			});
		}

	}

}
