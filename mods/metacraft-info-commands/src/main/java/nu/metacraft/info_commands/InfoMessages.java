package nu.metacraft.info_commands;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class InfoMessages {
	private int tickCount = 0;
	private int messageIndex = 0;

	@Nullable
	public InfoMessage getNextInfoMessage(List<InfoMessage> infoMessages) {
		if (infoMessages.isEmpty()) {
			return null;
		}
		return infoMessages.get((this.messageIndex++ % infoMessages.size()));
	}

	public void tick(MinecraftServer server, InfoConfig config) {
		this.tickCount++;
		if (this.tickCount % config.infoMessageIntervalTicks() != 0) {
			return;
		}
		if (server.getPlayerCount() == 0) {
			return;
		}
		InfoMessage message = this.getNextInfoMessage(config.infoMessages());
		if (message == null) {
			return;
		}
		Component text = Component.empty()
			.append(config.infoMessagePrefix())
			.append(message.message());
		server.sendSystemMessage(text);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.sendSystemMessage(text);
		}
	}
}
