package nu.metacraft.info_commands;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
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
		if (this.tickCount % config.getInfoMessageIntervalTicks() != 0) {
			return;
		}
		if (server.getCurrentPlayerCount() == 0) {
			return;
		}
		InfoMessage message = this.getNextInfoMessage(config.getInfoMessages());
		if (message == null) {
			return;
		}
		Text text = Text.empty()
			.append(config.getInfoMessagePrefix())
			.append(message.message());
		server.sendMessage(text);
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			player.sendMessage(text);
		}
	}
}
