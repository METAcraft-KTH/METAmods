package se.metacraft.config_gui.gui.value_editor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JavaOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerPlayer;
import se.metacraft.lib.util.helper.DataResultHelper;
import org.pcollections.PVector;
import org.pcollections.TreePVector;
import se.metacraft.config_gui.ClickHandlerGUI;
import se.metacraft.config_gui.CodecDialog;
import se.metacraft.config_gui.DialogGUI;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandler;
import se.metacraft.config_gui.gui.value_editor.click.handlers.MoveEntry;
import se.metacraft.config_gui.gui.value_editor.click.handlers.RemoveIndex;
import se.metacraft.config_gui.gui.value_editor.click.handlers.SubMenu;
import se.metacraft.config_gui.gui.value_editor.trait.MovableEntries;
import se.metacraft.config_gui.gui.value_editor.trait.RemovableByIndex;
import se.metacraft.config_gui.gui.value_editor.trait.WithSubMenus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public record ListEditor<T>(
	PVector<Object> list,
	CodecDialog.Type type, OptionalInt minSize, OptionalInt maxSize,
	Codec<T> wrappingCodec, Optional<ValueEditor.ParentInfo> parent
) implements ValueEditor<T>, RemovableByIndex, WithSubMenus, MovableEntries {

	public static <T> Optional<ClickHandlerGUI> create(
		T value, Codec<T> codec, OptionalInt minSize, OptionalInt maxSize, HolderLookup.Provider lookup, Optional<ParentInfo> parent
	) {
		return CodecDialog.getEmptyListSubType(codec, lookup).map(
			type -> new ListEditor<>(
				CodecDialog.getListCodecElements(codec, value, lookup, type), type,
				minSize, maxSize, codec, parent
			)
		);
	}

	@Override
	public ClickHandlerGUI updateFromChild(HolderLookup.Provider lookup, ClickHandlerGUI child, SubMenu.SubMenuKey position) {
		if (position instanceof SubMenu.I(int i) && child instanceof ValueEditor<?> e) {
			if (i < 0) return this;
			if (i > list.size()) return this;
			return PopupScreen.dataResult(
				e.getValue(lookup),
				opt -> {
					if (i == list.size()) {
						return new ListEditor<>(
							opt.map(list::plus).orElse(list),
							type, minSize, maxSize, wrappingCodec, parent
						);
					}
					return new ListEditor<>(
						opt.map(v -> list.with(i, v)).orElseGet(() -> list.minus(i)),
						type, minSize, maxSize, wrappingCodec, parent
					);
				},
				this
			);
		}
		return this;
	}

	@Override
	public DataResult<Optional<T>> getValue(HolderLookup.Provider lookup) {
		DataResult<PVector<Object>> list = DataResult.success(TreePVector.empty());
		var ctx = lookup.createSerializationContext(JavaOps.INSTANCE);
		for (var element : this.list) {
			//noinspection unchecked
			list = list.flatMap(
				l -> DataResultHelper.withPartialIfError(
					((Codec<Object>) type.element().codec()).encodeStart(
						ctx, element
					).map(l::plus),
					l
				)
			);
		}
		return list.flatMap(l -> wrappingCodec.parse(ctx, l).map(Optional::of));
	}

	@Override
	public Holder<Dialog> createDialog(ServerPlayer player) {
		List<ActionButton> buttons = new ArrayList<>();

		boolean hasRemoveButton = minSize.isEmpty() || minSize.getAsInt() < list.size();

		for (int i = 0; i < list.size(); i++) {
			var entry = list.get(i);
			var name = Component.literal(entry.toString());
			buttons.add(
				new ActionButton(
					new CommonButtonData(
						name, CommonButtonData.DEFAULT_WIDTH
					),
					Optional.of(ClickHandler.click(player.registryAccess(), new SubMenu(i), false))
				)
			);

			// Move up
			buttons.add(
				new ActionButton(
					new CommonButtonData(
						Component.literal("↑"), 20
					),
					Optional.of(ClickHandler.click(player.registryAccess(), new MoveEntry(i, MoveEntry.MoveDirection.UP), false))
				)
			);

			// Move down
			buttons.add(
				new ActionButton(
					new CommonButtonData(
						Component.literal("↓"), 20
					),
					Optional.of(ClickHandler.click(player.registryAccess(), new MoveEntry(i, MoveEntry.MoveDirection.DOWN), false))
				)
			);

			// Remove button
			if (hasRemoveButton) {
				buttons.add(
					new ActionButton(
						ValueEditor.removeButtonData(),
						Optional.of(ConfirmScreen.click(
							new RemoveIndex(i),
							ValueEditor.removeMessage(ValueEditor.highlight(name)),
							player.registryAccess()
						))
					)
				);
			}
		}

		if (maxSize.isEmpty() || maxSize.getAsInt() > list.size()) {
			buttons.add(
				new ActionButton(
					new CommonButtonData(
						Component.translatable("mco.create.world"), 50
					),
					Optional.of(ClickHandler.click(player.registryAccess(), new SubMenu(list.size()), false))
				)
			);
		}

		return CodecDialog.template(
			List.of(), List.of(), buttons, player.registryAccess(), hasRemoveButton ? 4 : 3
		);
	}

	@Override
	public DialogGUI remove(ServerPlayer player, int index) {
		if (index < 0 || index >= list.size()) return this;
		return new ListEditor<>(
			list.minus(index), type, minSize, maxSize, wrappingCodec, parent
		);
	}

	@Override
	public CodecDialog.Type getType(SubMenu.SubMenuKey key, ServerPlayer player) {
		if (!(key instanceof SubMenu.I(int index))) return null;
		if (index <= list.size() && index >= 0) {
			return type;
		}
		return null;
	}

	@Override
	public Object getObject(SubMenu.SubMenuKey key, ServerPlayer player) {
		if (!(key instanceof SubMenu.I(int index))) return null;
		if (index < list.size() && index >= 0) {
			return list.get(index);
		}
		return null;
	}

	@Override
	public DialogGUI moveEntry(int index, MoveEntry.MoveDirection direction) {
		if (index >= 0 && index < list.size()) {
			if (index == 0 && direction == MoveEntry.MoveDirection.UP) return this;
			if (index == list.size()-1 && direction == MoveEntry.MoveDirection.DOWN) return this;
			var entryToMove = list.get(index);
			int targetSlot = switch (direction) {
				case UP -> index-1;
				case DOWN -> index+1;
			};
			var entryAtTargetPos = list.get(targetSlot);
			var newList = list.with(index, entryAtTargetPos).with(targetSlot, entryToMove);
			return new ListEditor<>(newList, type, minSize, maxSize, wrappingCodec, parent);
		}
		return this;
	}
}
