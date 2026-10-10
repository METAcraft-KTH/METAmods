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

		config.getReloadState().ifPresent(reloadState -> {
			//Send all updated player-specific resource packs to affected players.
			for (var player : server.getPlayerList().getPlayers()) {
				var playerData = getData(player);
				List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
				for (var id : reloadState.toRemove()) {
					if (playerData.resourcePacks().contains(id)) {
						update(player, data -> data.removePack(id));
					}
					if (sendPackets) packets.add(new ClientboundResourcePackPopPacket(Optional.of(id)));
				}

				for (var id : reloadState.oldGlobals()) {
					if (sendPackets && !playerData.resourcePacks().contains(id)) {
						packets.add(new ClientboundResourcePackPopPacket(Optional.of(id)));
					}
				}

				for (var id : reloadState.newGlobals()) {
					if (sendPackets && !playerData.resourcePacks().contains(id)) {
						config.createEnablePacket(id).ifPresent(packets::add);
					}
					update(player, data -> data.removePack(id));
				}

				for (var id : reloadState.toSubmit()) {
					if (
						sendPackets &&
						!reloadState.newGlobals().contains(id) &&
						(
							playerData.resourcePacks().contains(id) ||
							config.getResourcePack(id).map(ResourcePackConfig.ResourcePack::isGlobal).orElse(false)
						)
					) {
						config.createEnablePacket(id).ifPresent(packets::add);
					}
				}

				if (!packets.isEmpty()) {
					player.connection.send(new ClientboundBundlePacket(packets));
				}
			}

			if (sendPackets) {
				ResourcePackConfig.clearReloadState();
			}
		});
	}

}
