package se.metacraft.lib.custom_message;

import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import se.metacraft.lib.util.PotentialPlayer;

import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * A simple message handler that discards any additional data and just runs the given function.
 * @param onMessage The code to execute.
 */
public record SimpleMessageHandler(BiConsumer<MinecraftServer, PotentialPlayer> onMessage) implements CustomMessageHandler {

	@Override
	public void handleMessage(Optional<Tag> payload, MinecraftServer server, PotentialPlayer player) {
		onMessage.accept(server, player);
	}

}
