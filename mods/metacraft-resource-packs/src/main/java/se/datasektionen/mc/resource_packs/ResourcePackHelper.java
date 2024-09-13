package se.datasektionen.mc.resource_packs;

import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.ResourcePackRemoveS2CPacket;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ResourcePackHelper {

	public static void enableResourcePack(ServerPlayerEntity player, UUID uuid) {
		var config = ResourcePackConfig.getConfig();
		var entry = config.getResourcePack(uuid);
		if (entry == null) return;
		if (!entry.isGlobal()) {
			((ServerPlayerEntityExtension) player).metacraft$getResourcePacks().add(uuid);
		}
		player.networkHandler.sendPacket(config.createEnablePacket(uuid));
	}

	public static void disableResourcePack(ServerPlayerEntity player, UUID uuid) {
		var config = ResourcePackConfig.getConfig();
		var entry = config.getResourcePack(uuid);
		if (entry == null) return;
		if (!entry.isGlobal()) {
			((ServerPlayerEntityExtension) player).metacraft$getResourcePacks().remove(uuid);
		}
		player.networkHandler.sendPacket(new ResourcePackRemoveS2CPacket(Optional.of(uuid)));
	}

	public static boolean hasResourcePack(ServerPlayerEntity player, UUID uuid) {
		var pack = ResourcePackConfig.getConfig().getResourcePack(uuid);
		if (pack == null) return false;
		if (pack.isGlobal()) {
			return true;
		}
		return ((ServerPlayerEntityExtension) player).metacraft$getResourcePacks().contains(uuid);
	}

	public static void resendResourcePacks(MinecraftServer server) {
		var config = ResourcePackConfig.getConfig();
		{ //Remove all removed resource packs from all players.
			List<Packet<? super ClientPlayPacketListener>> removePackets = new ArrayList<>();
			for (var pack : config.getRemovedPacks()) {
				removePackets.add(new ResourcePackRemoveS2CPacket(Optional.of(pack)));
			}
			if (!removePackets.isEmpty()) {
				var firstRemovePacket = new BundleS2CPacket(removePackets);
				for (var player : server.getPlayerManager().getPlayerList()) {
					player.networkHandler.sendPacket(firstRemovePacket);
				}
			}
		}
		//Remove all packs that were changed from global to non-global unless the player has it enabled.
		for (var player : server.getPlayerManager().getPlayerList()) {
			List<Packet<? super ClientPlayPacketListener>> packets = new ArrayList<>();
			for (var pack : config.getPrevGlobals()) {
				if (!((ServerPlayerEntityExtension) player).metacraft$getResourcePacks().contains(pack)) {
					packets.add(new ResourcePackRemoveS2CPacket(Optional.of(pack)));
				}
			}
			if (!packets.isEmpty()) {
				player.networkHandler.sendPacket(new BundleS2CPacket(packets));
			}
		}
		//Send all updated global resource packs to the players.
		for (var pack : config.getResourcePacks()) {
			List<Packet<? super ClientPlayPacketListener>> packets = new ArrayList<>();
			if (pack.getValue().isGlobal() && (config.hasChanged(pack.getKey()) || config.isNowGlobal(pack.getKey()))) {
				packets.add(config.createEnablePacket(pack.getKey()));
			}
			if (!packets.isEmpty()) {
				var packet = new BundleS2CPacket(packets);
				for (var player : server.getPlayerManager().getPlayerList()) {
					player.networkHandler.sendPacket(packet);
				}
			}
		}
		//Send all updated player-specific resource packs to affected players.
		for (var player : server.getPlayerManager().getPlayerList()) {
			List<Packet<? super ClientPlayPacketListener>> packets = new ArrayList<>();
			((ServerPlayerEntityExtension) player).metacraft$getResourcePacks().removeIf(pack -> {
				if (ResourcePackConfig.getConfig().resourcePackExists(pack)) {
					if (config.hasChanged(pack)) {
						packets.add(config.createEnablePacket(pack));
					}
					if (config.getResourcePack(pack).isGlobal()) {
						return true;
					}
				} else {
					packets.add(new ResourcePackRemoveS2CPacket(Optional.of(pack)));
					return true;
				}
				return false;
			});
			if (!packets.isEmpty()) {
				player.networkHandler.sendPacket(new BundleS2CPacket(packets));
			}
		}
	}

}
