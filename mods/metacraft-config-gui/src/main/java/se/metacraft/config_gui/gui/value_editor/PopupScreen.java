package se.metacraft.config_gui.gui.value_editor;

import com.mojang.serialization.DataResult;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.network.chat.contents.objects.ObjectInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import se.metacraft.config_gui.ClickHandlerGUI;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandler;
import se.metacraft.config_gui.gui.value_editor.click.handlers.Back;
import se.metacraft.config_gui.gui.value_editor.click.handlers.Confirmation;
import se.metacraft.config_gui.gui.value_editor.click.handlers.MultiAction;
import se.metacraft.config_gui.gui.value_editor.click.handlers.Resubmit;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public record PopupScreen(Component title, List<DialogBody> body, ParentInfo source) implements ClickHandlerGUI {

	public static final Component INFO = Component.translatable("mco.info");
	public static final Component FAILED = Component.translatable("optimizeWorld.stage.failed").withColor(TextColor.RED);

	private static Holder<Dialog> createDialog(
		Component title,
		List<DialogBody> body, HolderLookup.Provider lookup,
		ClickHandler onClose
	) {
		return Holder.direct(
			new NoticeDialog(
				new CommonDialogData(
					title, Optional.empty(),
					true, false,
					DialogAction.WAIT_FOR_RESPONSE,
					body, List.of()
				),
				new ActionButton(
					new CommonButtonData(Component.translatable("gui.ok"), 50),
					Optional.of(ClickHandler.click(
						lookup,
						onClose,
						false
					))
				)
			)
		);
	}

	public static <T extends ClickHandlerGUI> ClickHandlerGUI dataResult(
		DataResult<T> result, ClickHandlerGUI fallback
	) {
		return dataResult(result, g -> g, fallback);
	}

	public static <T> ClickHandlerGUI dataResult(
		DataResult<T> result, Function<T, ClickHandlerGUI> onSuccess, ClickHandlerGUI fallback
	) {
		return result.mapOrElse(
			onSuccess,
			err -> new PopupScreen(
				PopupScreen.FAILED, List.of(new PlainMessage(Component.literal(err.message()), PlainMessage.DEFAULT_WIDTH)),
				new SimpleSubMenuParentInfo(err.partialValue().map(onSuccess).orElse(fallback))
			)
		);
	}

	// This dialog is simple enough so it can run entirely on the client side in some cases.
	public static Holder<Dialog> simpleNotice(
		Component title, List<DialogBody> body, HolderLookup.Provider lookup
	) {
		return createDialog(
			title, body, lookup,
			Resubmit.INSTANCE
		);
	}

	@Override
	public Holder<Dialog> createDialog(ServerPlayer player) {
		return createDialog(
			title, body, player.registryAccess(),
			Back.INSTANCE
		);
	}

	@Override
	public Optional<ParentInfo> parent() {
		return Optional.of(source);
	}
}
