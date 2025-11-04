package nu.metacraft.core.compat;

import me.drex.vanish.util.Arguments;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import nu.metacraft.core.mixin.AccessorPlayerListS2CPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Vanish {

	public static void vanishDoNotHideMETAcraftPlayerMob(
			MinecraftServer server, ClientboundPlayerInfoUpdatePacket playerListPacket,
			ServerGamePacketListenerImpl listener, Consumer<Packet<ClientGamePacketListener>> packetSender
	) {
		if (Arguments.PACKET_CONTEXT.get() != null) {
			return;
		}
		List<ClientboundPlayerInfoUpdatePacket.Entry> playersToSendAnyway = new ArrayList<>();
		for (var player : playerListPacket.entries()) {
			if (server.getPlayerList().getPlayer(player.profileId()) == null) {
				playersToSendAnyway.add(player);
			}
		}

		if (!playersToSendAnyway.isEmpty()) {
			ServerPlayer prev = Arguments.PACKET_CONTEXT.get();
			Arguments.PACKET_CONTEXT.set(listener.player);
			var fixedPacket = new ClientboundPlayerInfoUpdatePacket(playerListPacket.actions(), List.of());
			((AccessorPlayerListS2CPacket) fixedPacket).setEntries(playersToSendAnyway);
			packetSender.accept(fixedPacket);
			Arguments.PACKET_CONTEXT.set(prev);
		}
	}

}
