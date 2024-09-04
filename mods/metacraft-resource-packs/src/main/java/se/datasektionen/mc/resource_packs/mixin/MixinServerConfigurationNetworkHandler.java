package se.datasektionen.mc.resource_packs.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.SendResourcePackTask;
import net.minecraft.server.network.ServerConfigurationNetworkHandler;
import net.minecraft.server.network.ServerPlayerConfigurationTask;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.resource_packs.ResourcePackConfig;

import java.util.Queue;
import java.util.function.Consumer;

@Mixin(ServerConfigurationNetworkHandler.class)
public class MixinServerConfigurationNetworkHandler {

	@Shadow @Final private Queue<ServerPlayerConfigurationTask> tasks;

	@Unique
	private boolean receivedResourcePack = false;

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
				entry -> entry.getValue().isGlobal()
		).toList();
		if (!packs.isEmpty()) {
			this.tasks.add(new ServerPlayerConfigurationTask() {
				@Override
				public void sendPacket(Consumer<Packet<?>> sender) {
					for (var pack : packs) {
						if (pack.getValue().isGlobal()) {
							sender.accept(config.createEnablePacket(pack.getKey()));
						}
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
