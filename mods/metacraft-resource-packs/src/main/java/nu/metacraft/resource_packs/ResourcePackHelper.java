package nu.metacraft.resource_packs;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import nu.metacraft.resource_packs.extension.ServerPlayerExtension;

import java.util.*;
import java.util.function.UnaryOperator;

public class ResourcePackHelper {

	public static void enableResourcePack(ServerPlayer player, UUID pack, boolean persist) {
		var config = ResourcePackConfig.getConfig();
		var entry = config.getResourcePack(pack);
		if (entry.isEmpty()) return;
		if (!entry.get().isGlobal()) {
			update(player, data -> data.addPack(pack, persist));
			config.createEnablePacket(pack).ifPresent(player.connection::send);
		}
	}

	public static void disableResourcePack(ServerPlayer player, UUID pack) {
		var config = ResourcePackConfig.getConfig();
		var entry = config.getResourcePack(pack);
		if (entry.isEmpty()) return;
		if (!entry.get().isGlobal()) {
			update(player, data -> data.removePack(pack));
			player.connection.send(new ClientboundResourcePackPopPacket(Optional.of(pack)));
		}
	}

	public static boolean hasResourcePack(ServerPlayer player, UUID pack) {
		return ResourcePackConfig.getConfig().getResourcePack(pack).map(ResourcePackConfig.ResourcePack::isGlobal).orElse(false) ||
				playerHasPack(player, pack);
	}

	private static boolean playerHasPack(ServerPlayer player, UUID pack) {
		return getData(player).hasPack(pack);
	}

	private static PlayerPackData getData(ServerPlayer player) {
		return ((ServerPlayerExtension) player).metacraft$getPackData();
	}

	private static void update(ServerPlayer player, UnaryOperator<PlayerPackData> updater) {
		((ServerPlayerExtension) player).metacraft$updatePackData(updater);
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

		//Remove all packs that were changed from global to non-global unless the player has it enabled.
		if (sendPackets) {
			for (var player : server.getPlayerList().getPlayers()) {
				List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
				for (var pack : config.getPrevGlobals()) {
					if (!getData(player).hasPack(pack)) {
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
					config.createEnablePacket(pack.getKey()).ifPresent(packets::add);
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
			getData(player).resourcePacks().forEach(pack -> {
				config.getResourcePack(pack).ifPresentOrElse(p -> {
					if (config.hasChangedButStillExists(pack) && sendPackets) {
						config.createEnablePacket(pack).ifPresent(packets::add);
					}
					if (p.isGlobal()) {
						update(player, data -> data.removePack(pack));
					}
				}, () -> {
					if (sendPackets) packets.add(new ClientboundResourcePackPopPacket(Optional.of(pack)));
					update(player, data -> data.removePack(pack));
				});
			});
			if (!packets.isEmpty()) {
				player.connection.send(new ClientboundBundlePacket(packets));
			}
		}
	}

}
