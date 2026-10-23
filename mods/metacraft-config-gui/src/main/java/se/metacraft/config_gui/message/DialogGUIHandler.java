package se.metacraft.config_gui.message;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.level.ServerPlayer;
import se.metacraft.lib.custom_message.CustomMessageHandler;
import se.metacraft.lib.util.PotentialPlayer;
import se.metacraft.config_gui.DialogGUI;
import se.metacraft.config_gui.DialogGUIExtension;

import java.util.Optional;

public class DialogGUIHandler implements CustomMessageHandler {

	@Override
	public void handleMessage(Optional<Tag> payload, MinecraftServer server, PotentialPlayer player) {
		player.getPlayer(server).ifPresent(p -> {
			var guiHolder = ((DialogGUIExtension) p);
			guiHolder.metacraft$getCurrentGUI().ifPresent(gui -> {
				var newGUI = gui.onClick(p, payload);
				if (newGUI == gui && gui.createDialog(p).value().common().afterAction() == DialogAction.NONE) {
					return;
				}
				if (newGUI != null) {
					guiHolder.metacraft$openGUI(newGUI);
				} else {
					guiHolder.metacraft$closeGUI();
				}
			});
		});
	}

	public static void openGUI(ServerPlayer player, DialogGUI gui) {
		((DialogGUIExtension) player).metacraft$openGUI(gui);
	}

	public static void closeGUI(ServerPlayer player) {
		((DialogGUIExtension) player).metacraft$closeGUI();
	}

	protected static ClickEvent clickEvent(
		Holder.Reference<? extends CustomMessageHandler> handler,
		@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<Tag> payload
	) {
		return new ClickEvent.Custom(handler.key().identifier(), payload);
	}

	protected static Action noInputsAction(
		Holder.Reference<? extends CustomMessageHandler> handler,
		@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<Tag> payload
	) {
		return new StaticAction(clickEvent(handler, payload));
	}

	protected static Action withInputsAction(
		Holder.Reference<? extends CustomMessageHandler> handler,
		@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<CompoundTag> extraData
	) {
		return new CustomAll(handler.key().identifier(), extraData);
	}

	public static ClickEvent closeEvent() {
		return clickEvent(DialogGUIMessages.CLOSE, Optional.empty());
	}

	public static Action closeAction() {
		return noInputsAction(DialogGUIMessages.CLOSE, Optional.empty());
	}


	public static ClickEvent clickEvent(
		@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<Tag> payload
	) {
		return clickEvent(DialogGUIMessages.HANDLER, payload);
	}

	public static Action noInputsAction(
		@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<Tag> payload
	) {
		return noInputsAction(DialogGUIMessages.HANDLER, payload);
	}

	public static Action withInputsAction(
		@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<CompoundTag> extraData
	) {
		return withInputsAction(DialogGUIMessages.HANDLER, extraData);
	}

}
