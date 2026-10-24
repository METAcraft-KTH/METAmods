package se.metacraft.config_gui.message;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import se.metacraft.lib.custom_message.CustomMessageHandler;
import se.metacraft.lib.custom_message.CustomMessageRegistry;
import se.metacraft.lib.custom_message.SimpleMessageHandler;
import se.metacraft.config_gui.ConfigGUI;

public class DialogGUIMessages {

	public static final Holder.Reference<DialogGUIHandler> HANDLER = register("dialog_gui", new DialogGUIHandler());
	public static final Holder.Reference<SimpleMessageHandler> CLOSE = register(
		"close_gui", new SimpleMessageHandler(
			(server, player) -> player.getPlayer(server).ifPresent(DialogGUIHandler::closeGUI)
		)
	);

	public static void init() {

	}

	private static <T extends CustomMessageHandler> Holder.Reference<T> register(String id, T handler) {
		return Registry.registerForHolder(CustomMessageRegistry.REGISTRY, ConfigGUI.getID(id), handler);
	}

}
