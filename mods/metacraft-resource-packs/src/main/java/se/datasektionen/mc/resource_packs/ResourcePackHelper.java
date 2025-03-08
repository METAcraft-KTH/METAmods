package se.datasektionen.mc.resource_packs;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.ResourcePackRemoveS2CPacket;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.*;

public class ResourcePackHelper {

	public static void enableResourcePack(ServerPlayerEntity player, UUID pack) {
		var config = ResourcePackConfig.getConfig();
		var entry = config.getResourcePack(pack);
		if (entry == null) return;
		if (!entry.isGlobal()) {
			PlayerPackDataManager.getInstance(player.getServer()).update(
					player.getGameProfile(), data -> data.addPack(pack)
			);
			player.networkHandler.sendPacket(config.createEnablePacket(pack));
		}
	}

	public static void disableResourcePack(ServerPlayerEntity player, UUID pack) {
		var config = ResourcePackConfig.getConfig();
		var entry = config.getResourcePack(pack);
		if (entry == null) return;
		if (!entry.isGlobal()) {
			PlayerPackDataManager.getInstance(player.getServer()).update(
					player.getGameProfile(), data -> data.removePack(pack)
			);
			player.networkHandler.sendPacket(new ResourcePackRemoveS2CPacket(Optional.of(pack)));
		}
	}

	public static boolean hasResourcePack(MinecraftServer server, GameProfile profile, UUID pack, ResourcePackConfig.ResourcePack packData) {
		return packData.isGlobal() || playerHasPack(server, profile, pack);
	}

	public static boolean hasResourcePack(ServerPlayerEntity player, UUID pack) {
		return hasResourcePack(player.getServer(), player.getGameProfile(), pack);
	}

	public static boolean hasResourcePack(MinecraftServer server, GameProfile profile, UUID pack) {
		return hasResourcePack(server, profile, pack, ResourcePackConfig.getConfig().getResourcePack(pack));
	}

	private static boolean playerHasPack(MinecraftServer server, GameProfile profile, UUID pack) {
		return PlayerPackDataManager.getInstance(server).getFromPlayer(profile).hasPack(pack);
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

		var packManager = PlayerPackDataManager.getInstance(server);

		//Remove all packs that were changed from global to non-global unless the player has it enabled.
		for (var player : server.getPlayerManager().getPlayerList()) {
			List<Packet<? super ClientPlayPacketListener>> packets = new ArrayList<>();
			for (var pack : config.getPrevGlobals()) {
				if (!packManager.getFromPlayer(player.getGameProfile()).hasPack(pack)) {
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
			packManager.getFromPlayer(player.getGameProfile()).resourcePacks().forEach(pack -> {
				if (ResourcePackConfig.getConfig().resourcePackExists(pack)) {
					if (config.hasChanged(pack)) {
						packets.add(config.createEnablePacket(pack));
					}
					if (config.getResourcePack(pack).isGlobal()) {
						packManager.update(player.getGameProfile(), data -> data.removePack(pack));
					}
				} else {
					packets.add(new ResourcePackRemoveS2CPacket(Optional.of(pack)));
					packManager.update(player.getGameProfile(), data -> data.removePack(pack));
				}
			});
			if (!packets.isEmpty()) {
				player.networkHandler.sendPacket(new BundleS2CPacket(packets));
			}
		}
	}

}
