package nu.metacraft.core.compat;

import me.drex.vanish.util.Arguments;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import nu.metacraft.core.mixin.AccessorPlayerListS2CPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Vanish {

	public static void vanishDoNotHideMETAcraftPlayerMob(
			MinecraftServer server, PlayerListS2CPacket playerListPacket,
			ServerPlayNetworkHandler listener, Consumer<Packet<ClientPlayPacketListener>> packetSender
	) {
		if (Arguments.PACKET_CONTEXT.get() != null) {
			return;
		}
		List<PlayerListS2CPacket.Entry> playersToSendAnyway = new ArrayList<>();
		for (var player : playerListPacket.getEntries()) {
			if (server.getPlayerManager().getPlayer(player.profileId()) == null) {
				playersToSendAnyway.add(player);
			}
		}

		if (!playersToSendAnyway.isEmpty()) {
			ServerPlayerEntity prev = Arguments.PACKET_CONTEXT.get();
			Arguments.PACKET_CONTEXT.set(listener.player);
			var fixedPacket = new PlayerListS2CPacket(playerListPacket.getActions(), List.of());
			((AccessorPlayerListS2CPacket) fixedPacket).setEntries(playersToSendAnyway);
			packetSender.accept(fixedPacket);
			Arguments.PACKET_CONTEXT.set(prev);
		}
	}

}
