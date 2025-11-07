package nu.metacraft.resource_packs.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.*;
import net.minecraft.server.network.config.ServerResourcePackConfigurationTask;
import nu.metacraft.lib.util.helper.DisconnectedPlayerHelper;
import nu.metacraft.resource_packs.EarlyPacksCallback;
import nu.metacraft.resource_packs.PlayerPackData;
import nu.metacraft.resource_packs.extension.ConnectionExtension;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.resource_packs.ResourcePackConfig;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mixin(ServerConfigurationPacketListenerImpl.class)
public abstract class ServerConfigurationPacketListenerImplMixin extends ServerCommonPacketListenerImpl {

	@Shadow @Final private Queue<ConfigurationTask> configurationTasks;

	@Shadow @Final private GameProfile gameProfile;
	@Unique
	private boolean receivedResourcePack = false;

	public ServerConfigurationPacketListenerImplMixin(MinecraftServer server, Connection connection, CommonListenerCookie clientData) {
		super(server, connection, clientData);
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
		var globals = config.getResourcePacks().stream().filter(
				entry -> entry.getValue().isGlobal()
		).map(Map.Entry::getKey);
		var data = DisconnectedPlayerHelper.getPlayerData(server, gameProfile.id());
		var packData = data.flatMap(d -> d.read(PlayerPackData.KEY, PlayerPackData.CODEC)).orElse(PlayerPackData.EMPTY);
		var nonGlobals = config.getResourcePacks().stream().filter(
				entry -> !entry.getValue().isGlobal() && packData.hasPack(entry.getKey())
		).map(Map.Entry::getKey);
		List<UUID> addedPacks = new ArrayList<>();
		EarlyPacksCallback.EVENT.invoker().addPacks(server, gameProfile, data, addedPacks::add);
		if (!addedPacks.isEmpty()) {
			((ConnectionExtension) connection).metacraft$updateAddedPacks(packs -> packs.plusAll(addedPacks));
		}
		var packs = Stream.concat(globals, Stream.concat(nonGlobals, addedPacks.stream())).collect(Collectors.toSet());
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
