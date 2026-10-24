package se.metacraft.lib.custom_message;

import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import se.metacraft.lib.util.PotentialPlayer;

import java.util.Optional;

public interface CustomMessageHandler {

	/**
	 * Handler for a message.
	 * @param payload The payload of the message. Note that hackers can send any arbitrary NBT tag here.
	 * @param server The server accepting this message.
	 * @param player The player who sent the request. Note that {@link PotentialPlayer#getPlayer(MinecraftServer) might be empty in the configuration stage, but {@link PotentialPlayer#getPlayerID()}} might not be.
	 */
	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	void handleMessage(
			Optional<Tag> payload, MinecraftServer server, PotentialPlayer player
	);

}
