package nu.metacraft.resource_packs;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

public class ResourcePackHelper {

	public static void enableResourcePack(ServerPlayer player, UUID pack) {
		var config = ResourcePackConfig.getConfig();
		var entry = config.getResourcePack(pack);
		if (entry == null) return;
		if (!entry.isGlobal()) {
			PlayerPackDataManager.getInstance(player.level().getServer()).update(
					player.getGameProfile(), data -> data.addPack(pack)
			);
			player.connection.send(config.createEnablePacket(pack));
		}
	}

	public static void disableResourcePack(ServerPlayer player, UUID pack) {
		var config = ResourcePackConfig.getConfig();
		var entry = config.getResourcePack(pack);
		if (entry == null) return;
		if (!entry.isGlobal()) {
			PlayerPackDataManager.getInstance(player.level().getServer()).update(
					player.getGameProfile(), data -> data.removePack(pack)
			);
			player.connection.send(new ClientboundResourcePackPopPacket(Optional.of(pack)));
		}
	}

	public static boolean hasResourcePack(MinecraftServer server, GameProfile profile, UUID pack, ResourcePackConfig.ResourcePack packData) {
		return packData.isGlobal() || playerHasPack(server, profile, pack);
	}

	public static boolean hasResourcePack(ServerPlayer player, UUID pack) {
		return hasResourcePack(player.level().getServer(), player.getGameProfile(), pack);
	}

	public static boolean hasResourcePack(MinecraftServer server, GameProfile profile, UUID pack) {
		return hasResourcePack(server, profile, pack, ResourcePackConfig.getConfig().getResourcePack(pack));
	}

	private static boolean playerHasPack(MinecraftServer server, GameProfile profile, UUID pack) {
		return PlayerPackDataManager.getInstance(server).getFromPlayer(profile).hasPack(pack);
	}

	public static void resendResourcePacks(MinecraftServer server, boolean sendPackets) {
		var config = ResourcePackConfig.getConfig();
		if (sendPackets) { //Remove all removed resource packs from all players.
			List<Packet<? super ClientGamePacketListener>> removePackets = new ArrayList<>();
			for (var pack : config.getRemovedPacks()) {
				removePackets.add(new ClientboundResourcePackPopPacket(Optional.of(pack)));
			}
			if (!removePackets.isEmpty()) {
				var firstRemovePacket = new ClientboundBundlePacket(removePackets);
				for (var player : server.getPlayerList().getPlayers()) {
					player.connection.send(firstRemovePacket);
				}
			}
		}

		var packManager = PlayerPackDataManager.getInstance(server);

		//Remove all packs that were changed from global to non-global unless the player has it enabled.
		if (sendPackets) {
			for (var player : server.getPlayerList().getPlayers()) {
				List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
				for (var pack : config.getPrevGlobals()) {
					if (!packManager.getFromPlayer(player.getGameProfile()).hasPack(pack)) {
						packets.add(new ClientboundResourcePackPopPacket(Optional.of(pack)));
					}
				}
				if (!packets.isEmpty()) {
					player.connection.send(new ClientboundBundlePacket(packets));
				}
			}
		}
		//Send all updated global resource packs to the players.
		if (sendPackets) {
			for (var pack : config.getResourcePacks()) {
				List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
				if (pack.getValue().isGlobal() && (config.hasChangedButStillExists(pack.getKey()) || config.isNowGlobal(pack.getKey()))) {
					packets.add(config.createEnablePacket(pack.getKey()));
				}
				if (!packets.isEmpty()) {
					var packet = new ClientboundBundlePacket(packets);
					for (var player : server.getPlayerList().getPlayers()) {
						player.connection.send(packet);
					}
				}
			}
		}
		//Send all updated player-specific resource packs to affected players.
		for (var player : server.getPlayerList().getPlayers()) {
			List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
			packManager.getFromPlayer(player.getGameProfile()).resourcePacks().forEach(pack -> {
				if (ResourcePackConfig.getConfig().resourcePackExists(pack)) {
					if (config.hasChangedButStillExists(pack) && sendPackets) {
						packets.add(config.createEnablePacket(pack));
					}
					if (config.getResourcePack(pack).isGlobal()) {
						packManager.update(player.getGameProfile(), data -> data.removePack(pack));
					}
				} else {
					if (sendPackets) packets.add(new ClientboundResourcePackPopPacket(Optional.of(pack)));
					packManager.update(player.getGameProfile(), data -> data.removePack(pack));
				}
			});
			if (!packets.isEmpty()) {
				player.connection.send(new ClientboundBundlePacket(packets));
			}
		}
	}

}
