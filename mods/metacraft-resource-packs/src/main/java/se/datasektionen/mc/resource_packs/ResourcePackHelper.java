package se.datasektionen.mc.resource_packs;

import net.minecraft.network.packet.s2c.common.ResourcePackRemoveS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Optional;
import java.util.UUID;

public class ResourcePackHelper {

	public static void enableResourcePack(ServerPlayerEntity player, UUID uuid) {
		var config = ResourcePackConfig.getConfig();
		var entry = config.getResourcePack(uuid);
		if (entry == null) return;
		player.networkHandler.sendPacket(config.createEnablePacket(uuid));
	}

	public static void disableResourcePack(ServerPlayerEntity player, UUID uuid) {
		var config = ResourcePackConfig.getConfig();
		var entry = config.getResourcePack(uuid);
		if (entry == null) return;
		player.networkHandler.sendPacket(new ResourcePackRemoveS2CPacket(Optional.of(uuid)));
	}

}
