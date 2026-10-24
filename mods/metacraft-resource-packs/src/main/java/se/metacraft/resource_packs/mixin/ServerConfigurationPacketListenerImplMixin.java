package se.metacraft.resource_packs.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.*;
import se.metacraft.resource_packs.CustomPackTask;
import se.metacraft.resource_packs.extension.ServerConfigurationPacketListenerImplExtension;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(ServerConfigurationPacketListenerImpl.class)
public abstract class ServerConfigurationPacketListenerImplMixin extends ServerCommonPacketListenerImpl implements ServerConfigurationPacketListenerImplExtension {

	@Shadow @Final private Queue<ConfigurationTask> configurationTasks;

	@Shadow @Final private GameProfile gameProfile;

	@Shadow
	private @Nullable ConfigurationTask currentTask;

	@Unique
	private boolean shouldAddPackTask = true;

	@Shadow
	protected abstract void finishCurrentTask(ConfigurationTask.Type taskTypeToFinish);

	public ServerConfigurationPacketListenerImplMixin(MinecraftServer server, Connection connection, CommonListenerCookie clientData) {
		super(server, connection, clientData);
	}

	@Override
	public void metacraft$disablePackTask() {
		shouldAddPackTask = false;
	}

	@Inject(
		method = "handleResourcePackResponse",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;handleResourcePackResponse(Lnet/minecraft/network/protocol/common/ServerboundResourcePackPacket;)V",
			shift = At.Shift.AFTER
		),
		order = 0
	)
	public void onResourcePackStatus(ServerboundResourcePackPacket packet, CallbackInfo ci) {
		if (currentTask instanceof CustomPackTask task && task.checkPacket(packet)) {
			finishCurrentTask(CustomPackTask.TYPE);
		}
	}

	@WrapWithCondition(
		method = "handleResourcePackResponse",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerConfigurationPacketListenerImpl;finishCurrentTask(Lnet/minecraft/server/network/ConfigurationTask$Type;)V"
		)
	)
	public boolean preventVanillaHandling(ServerConfigurationPacketListenerImpl instance, ConfigurationTask.Type taskTypeToFinish) {
		return !(currentTask instanceof CustomPackTask);
	}

	@Inject(method = "addOptionalTasks", at = @At("RETURN"), order = 1100) // Make sure this runs after AutoHost
	public void sendPacket(CallbackInfo ci) {
		if (shouldAddPackTask) {
			var packs = CustomPackTask.preparePacks(server, gameProfile, connection);
			if (!packs.isEmpty()) {
				this.configurationTasks.add(new CustomPackTask(packs));
			}
		}
	}

}
