package nu.metacraft.lib.custom_message;

import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import nu.metacraft.lib.util.PotentialPlayer;

import java.util.Optional;

public interface CustomMessageHandler {

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	void handleMessage(
			Optional<Tag> payload, MinecraftServer server, PotentialPlayer player
	);

}
