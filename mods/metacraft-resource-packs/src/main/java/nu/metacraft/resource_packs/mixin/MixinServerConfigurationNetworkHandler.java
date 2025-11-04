package nu.metacraft.resource_packs.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.*;
import net.minecraft.server.network.config.ServerResourcePackConfigurationTask;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.resource_packs.PlayerPackDataManager;
import nu.metacraft.resource_packs.ResourcePackConfig;
import nu.metacraft.resource_packs.ResourcePackHelper;

import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;

@Mixin(ServerConfigurationPacketListenerImpl.class)
public abstract class MixinServerConfigurationNetworkHandler extends ServerCommonPacketListenerImpl {

	@Shadow @Final private Queue<ConfigurationTask> configurationTasks;

	@Shadow @Final private GameProfile gameProfile;
	@Unique
	private boolean receivedResourcePack = false;

	public MixinServerConfigurationNetworkHandler(MinecraftServer server, Connection connection, CommonListenerCookie clientData) {
		super(server, connection, clientData);
	}

	@Inject(method = "<init>", at = @At("RETURN"))
	public void init(MinecraftServer minecraftServer, Connection clientConnection, CommonListenerCookie connectedClientData, CallbackInfo ci) {
		PlayerPackDataManager.getInstance(minecraftServer).loadPlayer(gameProfile);
	}

	@WrapWithCondition(
		method = "handleResourcePackResponse",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerConfigurationPacketListenerImpl;finishCurrentTask(Lnet/minecraft/server/network/ConfigurationTask$Type;)V"
		)
	)
	public boolean onResourcePackStatus(ServerConfigurationPacketListenerImpl instance, ConfigurationTask.Type key) {
		if (receivedResourcePack) {
			return false;
		}
		receivedResourcePack = true;
		return true;
	}

	@Inject(method = "addOptionalTasks", at = @At("RETURN"))
	public void sendPacket(CallbackInfo ci) {
		var config = ResourcePackConfig.getConfig();
		var packs = config.getResourcePacks().stream().filter(
				entry -> ResourcePackHelper.hasResourcePack(server, gameProfile, entry.getKey(), entry.getValue())
		).map(Map.Entry::getKey).toList();
		if (!packs.isEmpty()) {
			this.configurationTasks.add(new ConfigurationTask() {
				@Override
				public void start(Consumer<Packet<?>> sender) {
					for (var pack : packs) {
						sender.accept(config.createEnablePacket(pack));
					}
				}

				@Override
				public Type type() {
					return ServerResourcePackConfigurationTask.TYPE;
				}
			});
		}

	}

}
