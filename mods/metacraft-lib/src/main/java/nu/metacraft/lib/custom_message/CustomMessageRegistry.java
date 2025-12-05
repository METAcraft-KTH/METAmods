package nu.metacraft.lib.custom_message;

import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.world.entity.player.Player;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.util.PotentialPlayer;

import java.util.Optional;

public class CustomMessageRegistry {

	public static final Registry<CustomMessageHandler> REGISTRY = FabricRegistryBuilder.<CustomMessageHandler>createSimple(
			ResourceKey.createRegistryKey(METAcraftLib.getID("message_handlers"))
	).attribute(RegistryAttribute.OPTIONAL).buildAndRegister();

	public static void init() {

	}

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
