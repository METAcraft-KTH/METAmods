package se.metacraft.config_gui.gui.value_editor;

import com.mojang.serialization.DataResult;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
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
import se.metacraft.config_gui.gui.value_editor.click.handlers.Confirmation;
import se.metacraft.config_gui.gui.value_editor.click.handlers.MultiAction;
import se.metacraft.config_gui.gui.value_editor.click.handlers.Resubmit;
import se.metacraft.config_gui.gui.value_editor.click.handlers.SubMenu;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface ConfirmScreen extends ClickHandlerGUI {

	ObjectInfo CONFIRM = new AtlasSprite(
		Identifier.parse("gui"), Identifier.parse("pending_invite/accept")
	);

	ObjectInfo CANCEL = new AtlasSprite(
		Identifier.parse("gui"), Identifier.parse("pending_invite/reject")
	);

	static ClickEvent clickEvent(ClickHandler handler, List<DialogBody> body, HolderLookup.Provider lookup) {
		return new ClickEvent.ShowDialog(simpleConfirmation(handler, body, lookup));
	}

	static Action click(ClickHandler handler, List<DialogBody> body, HolderLookup.Provider lookup) {
		return new StaticAction(clickEvent(handler, body, lookup));
	}

	private static Holder<Dialog> createDialog(
		List<DialogBody> body, HolderLookup.Provider lookup,
		ClickHandler onConfirm, ClickHandler onCancel
	) {
		return Holder.direct(
			new ConfirmationDialog(
				new CommonDialogData(
					Component.translatable("mco.configure.world.reset.question.line2"), Optional.empty(),
					true, false,
					DialogAction.WAIT_FOR_RESPONSE,
					body, List.of()
				),
				new ActionButton(
					new CommonButtonData(Component.translatable("gui.yes").append(" ").append(Component.object(CONFIRM)), 50),
					Optional.of(ClickHandler.click(
						lookup,
						onConfirm,
						false
					))
				),
				new ActionButton(
					new CommonButtonData(Component.translatable("gui.no").append(" ").append(Component.object(CANCEL)), 50),
					Optional.of(ClickHandler.click(lookup, onCancel, false))
				)
			)
		);
	}

	// This dialog is simple enough so it can run entirely on the client side in some cases.
	static Holder<Dialog> simpleConfirmation(
		ClickHandler handler, List<DialogBody> body, HolderLookup.Provider lookup
	) {
		return createDialog(
			body, lookup,
			new MultiAction(List.of(handler, Resubmit.INSTANCE)),
			Resubmit.INSTANCE
		);
	}

	List<DialogBody> body(ServerPlayer player);

	@Override
	default Holder<Dialog> createDialog(ServerPlayer player) {
		return createDialog(
			body(player), player.registryAccess(),
			new Confirmation(true), new Confirmation(false)
		);
	}

	ClickHandlerGUI onConfirm(ServerPlayer player);

	record Simple(List<DialogBody> body, ClickHandlerGUI onConfirm, ParentInfo source) implements ConfirmScreen {

		public Simple(List<DialogBody> body, ClickHandlerGUI onConfirm, ClickHandlerGUI onCancel) {
			this(body, onConfirm, (childGUI, player) -> onCancel);
		}

		@Override
		public List<DialogBody> body(ServerPlayer player) {
			return body;
		}

		@Override
		public ClickHandlerGUI onConfirm(ServerPlayer player) {
			return onConfirm;
		}

		@Override
		public Optional<ParentInfo> parent() {
			return Optional.of(source);
		}
	}

	record Editor<T>(
		List<DialogBody> body, Optional<T> value, Function<Editor<?>, ClickHandlerGUI> onConfirm, Optional<ParentInfo> parent
	) implements ConfirmScreen, ValueEditor<T> {

		@Override
		public ClickHandlerGUI updateFromChild(HolderLookup.Provider lookup, ClickHandlerGUI child, SubMenu.SubMenuKey position) {
			if (child instanceof ValueEditor<?> editor) {
				return PopupScreen.dataResult(
					editor.getValue(lookup),
					v -> new Editor<>(
						body, v, onConfirm, parent
					),
					this
				);
			}
			return this;
		}

		@Override
		public List<DialogBody> body(ServerPlayer player) {
			return body;
		}

		@Override
		public ClickHandlerGUI onConfirm(ServerPlayer player) {
			return onConfirm.apply(this);
		}

		@Override
		public DataResult<Optional<T>> getValue(HolderLookup.Provider lookup) {
			return DataResult.success(value);
		}
	}
}
