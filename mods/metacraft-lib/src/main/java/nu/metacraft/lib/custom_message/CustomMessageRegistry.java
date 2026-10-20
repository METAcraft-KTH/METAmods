package nu.metacraft.lib.custom_message;

import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.minecraft.core.Registry;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.util.PotentialPlayer;

import java.util.Optional;

/**
 * Registry for custom message handlers.
 * When receiving a click event or dialog action with the type "minecraft:custom" from a player,
 * the server will check if a handler exists in {@link CustomMessageRegistry#REGISTRY} with the same id.
 * If it exists, the server will execute {@link CustomMessageHandler#handleMessage(Optional, MinecraftServer, PotentialPlayer)}.
 * If some edge case does not trigger your message handler, you can invoke it manually with
 * {@link CustomMessageRegistry#handleAction(Identifier, Optional, MinecraftServer, PotentialPlayer)}.
 * You can define your own custom implementation of {@link CustomMessageHandler}, but you may want to try
 * {@link SimpleMessageHandler} or {@link CodecMessageHandler}.
 */
public class CustomMessageRegistry {

	public static final Registry<CustomMessageHandler> REGISTRY = FabricRegistryBuilder.<CustomMessageHandler>create(
			ResourceKey.createRegistryKey(METAcraftLib.getID("message_handlers"))
	).attribute(RegistryAttribute.OPTIONAL).buildAndRegister();

	public static void init() {

	}

	/**
	 * Runs the message handler for the given id.
	 * @param id The id to fetch from the registry.
	 * @param payload The payload to provide.
	 * @param server The server this runs on.
	 * @param player The player who executed this.
	 * @return True if a message handler existed for the given id, false otherwise.
	 */
	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	public static boolean handleAction(
			Identifier id, Optional<Tag> payload, MinecraftServer server, PotentialPlayer player
	) {
		return CustomMessageRegistry.REGISTRY.get(id).map(handler -> {
			handler.value().handleMessage(payload, server, player);
			return true;
		}).orElse(false);
	}

}
