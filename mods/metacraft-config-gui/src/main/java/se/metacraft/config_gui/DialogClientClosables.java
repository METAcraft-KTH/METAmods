package se.metacraft.config_gui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.util.Unit;
import se.metacraft.lib.custom_message.CustomMessageRegistry;

import java.util.List;
import java.util.Optional;

public class DialogClientClosables {

	private static boolean clientClosable(CommonDialogData data, boolean isExit) {
		return data.afterAction() != DialogAction.NONE || (data.canCloseWithEscape() && isExit);
	}

	private static void warnCustomMessageID(Identifier id) {
		if (FabricLoader.getInstance().isDevelopmentEnvironment() && !CustomMessageRegistry.REGISTRY.containsKey(id)) {
			ConfigGUI.LOGGER.warn("The message id {} has not been registered.", id);
		}
	}

	private static boolean clientClosable(ClickEvent event, CommonDialogData data, boolean isExit) {
		switch (event) {
			case ClickEvent.Custom custom -> {
				warnCustomMessageID(custom.id());
			}
			case ClickEvent.ShowDialog dialog -> {
				return clientClosable(dialog.dialog());
			}
			case ClickEvent.OpenUrl url -> {
				return clientClosable(data, isExit);
			}
			case ClickEvent.CopyToClipboard c -> {
				return clientClosable(data, isExit);
			}
			case ClickEvent.ChangePage c -> {
				return clientClosable(data, isExit);
			}
			case ClickEvent.OpenFile c -> {
				if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
					ConfigGUI.LOGGER.warn("The open file click event cannot be sent to the client!");
				}
				return clientClosable(data, isExit);
			}
			case ClickEvent.SuggestCommand c -> {
				if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
					ConfigGUI.LOGGER.warn("https://bugs.mojang.com/browse/MC/issues/MC-300896");
				}
				return clientClosable(data, isExit);
			}
			default -> {}
		}
		return false;
	}

	private static boolean clientClosable(
		@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<Action> action,
		CommonDialogData data, boolean isExit
	) {
		if (action.isEmpty()) {
			return clientClosable(data, isExit);
		}

		if (action.isPresent()) {
			switch (action.get()) {
				case CustomAll custom -> {
					warnCustomMessageID(custom.id());
				}
				case StaticAction(ClickEvent value) -> {
					return clientClosable(value, data, isExit);
				}
				default -> {}
			}
		}

		return false;
	}

	private static boolean clientClosable(Component text, CommonDialogData data) {
		return text.visit((style, contents) -> {
			var clickEvent = style.getClickEvent();
			if (clickEvent != null && clientClosable(clickEvent, data, false)) {
				return Optional.of(Unit.INSTANCE);
			}
			return Optional.empty();
		}, Style.EMPTY).isPresent();
	}

	public static boolean clientClosable(List<DialogBody> body, CommonDialogData commonData) {
		for (var b : body) {
			switch (b) {
				case PlainMessage(Component contents, _) -> {
					if (clientClosable(contents, commonData)) {
						return true;
					}
				}
				case ItemBody item -> {
					if (item.description().isPresent() && clientClosable(item.description().get().contents(), commonData)) {
						return true;
					}
				}
				default -> {}
			}
		}
		return false;
	}

	public static boolean clientClosable(Holder<Dialog> dialog) {
		var d = dialog.value();
		if (d instanceof ServerLinksDialog) {
			return true;
		}
		var common = d.common();
		if (clientClosable(d.common().body(), d.common())) {
			return true;
		}
		if (d.common().canCloseWithEscape() && clientClosable(d.onCancel(), common, true)) {
			return true;
		}
		if (d instanceof SimpleDialog simple) {
			for (var button : simple.mainActions()) {
				if (clientClosable(button.action(), common, false)) {
					return true;
				}
			}
		}
		if (d instanceof MultiActionDialog multi) {
			for (var button : multi.actions()) {
				if (clientClosable(button.action(), common, false)) {
					return true;
				}
			}
		}
		return false;
	}

}
