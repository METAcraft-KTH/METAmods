package nu.metacraft.lib.custom_message;

import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import nu.metacraft.lib.util.PotentialPlayer;

import java.util.Optional;
import java.util.function.BiConsumer;

public record SimpleMessageHandler(BiConsumer<MinecraftServer, PotentialPlayer> onMessage) implements CustomMessageHandler {

	@Override
	public void handleMessage(Optional<Tag> payload, MinecraftServer server, PotentialPlayer player) {
		onMessage.accept(server, player);
	}

}
