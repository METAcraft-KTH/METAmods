package se.metacraft.resource_packs;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConfigurationTask;
import se.metacraft.lib.util.helper.DisconnectedPlayerHelper;
import se.metacraft.resource_packs.extension.ConnectionExtension;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CustomPackTask implements ConfigurationTask {

	public static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(ResourcePacks.getID("send_packs").toString());

	private final Set<ClientboundResourcePackPushPacket> packets;
	private int packsReceived = 0;

	public CustomPackTask(Set<ClientboundResourcePackPushPacket> packets) {
		this.packets = packets;
	}

	public boolean checkPacket(ServerboundResourcePackPacket packet) {
		if (packet.action().isTerminal()) {
			packsReceived++;
			if (packsReceived >= packets.size()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void start(@NonNull Consumer<Packet<?>> connection) {
		for (var pack : packets) {
			connection.accept(pack);
		}
	}

	@Override
	public @NonNull Type type() {
		return TYPE;
	}

	public static Set<ClientboundResourcePackPushPacket> preparePacks(MinecraftServer server, GameProfile gameProfile, Connection connection) {
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
		var packets = Stream.concat(globals, Stream.concat(nonGlobals, addedPacks.stream())).map(
			config::createEnablePacket
		).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toCollection(HashSet::new));
		EarlyPackPacketsCallback.EVENT.invoker().addPackets(server, gameProfile, packets::add);
		return packets;
	}
}
