package se.metacraft.config_gui.gui.value_editor;

import com.mojang.serialization.*;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerPlayer;
import se.metacraft.config_gui.ClickHandlerGUI;
import se.metacraft.config_gui.CodecDialog;
import se.metacraft.config_gui.DialogGUI;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandler;
import se.metacraft.config_gui.gui.value_editor.click.handlers.Select;
import se.metacraft.config_gui.gui.value_editor.click.handlers.SubMenu;
import se.metacraft.config_gui.gui.value_editor.trait.Selectable;
import se.metacraft.config_gui.gui.value_editor.trait.WithSubMenus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record OptionalValueEditor<T>(
	Object value, CodecDialog.Type type, Codec<T> codec, boolean present, Optional<ParentInfo> parent
) implements ValueEditor<T>, WithSubMenus, Selectable {

	private static final String EMPTY = "empty";

	private static <T> DataResult<?> forceEncode(Codec<T> codec, DynamicOps<?> ops, Object value) {
		//noinspection unchecked
		return codec.encodeStart(ops, (T) value);
	}

	public static <T> OptionalValueEditor<T> create(
		Object value, CodecDialog.Type type, boolean present, Optional<ParentInfo> parent
	) {
		//noinspection unchecked
		return new OptionalValueEditor<>(
			value, type, (Codec<T>) type.element().codec(), present, parent
		);
	}

	@Override
	public Holder<Dialog> createDialog(ServerPlayer player) {
		List<ActionButton> buttons = new ArrayList<>();
		var name = CodecDialog.getGUIFriendlyName(value);
		buttons.add(new ActionButton(
			new CommonButtonData(
				present ? ValueEditor.highlight(name) : name,
				300
			),
			Optional.of(ClickHandler.click(
				player.registryAccess(),
				new SubMenu(OptionalKey.INSTANCE),
				false
			))
		));
		Component absentName = Component.translatable("item.minecraft.bundle.empty");
		buttons.add(new ActionButton(
			new CommonButtonData(
				!present ? ValueEditor.highlight(absentName) : absentName,
				300
			),
			Optional.of(ClickHandler.click(
				player.registryAccess(),
				new Select(EMPTY),
				false
			))
		));
		return CodecDialog.template(
			List.of(), List.of(), buttons, player.registryAccess(), 1
		);
	}

	@Override
	public DialogGUI onSelect(String key, ServerPlayer player) {
		if (key.equals(EMPTY)) {
			return new OptionalValueEditor<>(value, type, codec, false, parent);
		}
		return this;
	}

	public static final class OptionalKey implements SubMenu.SubMenuKey {

		public static final OptionalKey INSTANCE = new OptionalKey();
		private OptionalKey() {}

		public static final MapCodec<OptionalKey> CODEC = MapCodec.unit(INSTANCE);

		@Override
		public MapCodec<? extends SubMenu.SubMenuKey> codec() {
			return CODEC;
		}
	}

	@Override
	public ClickHandlerGUI updateFromChild(HolderLookup.Provider lookup, ClickHandlerGUI child, SubMenu.SubMenuKey position) {
		if (position == OptionalKey.INSTANCE && child instanceof ValueEditor<?> e) {
			return PopupScreen.dataResult(
				e.getValue(lookup),
				v -> new OptionalValueEditor<>(
					v.map(o -> (Object) o).orElse(value), type,
					codec, v.isPresent(), parent
				),
				this
			);
		}
		return this;
	}

	@Override
	public DataResult<Optional<T>> getValue(HolderLookup.Provider lookup) {
		if (!present) return DataResult.success(Optional.empty());
		var ctx = lookup.createSerializationContext(JavaOps.INSTANCE);
		return forceEncode(type.element().codec(), ctx, value).flatMap(
			encoded -> codec.parse(ctx, encoded)
		).map(Optional::of);
	}

	@Override
	public CodecDialog.Type getType(SubMenu.SubMenuKey key, ServerPlayer player) {
		if (key == OptionalKey.INSTANCE) {
			return type;
		} else {
			return null;
		}
	}

	@Override
	public Object getObject(SubMenu.SubMenuKey key, ServerPlayer player) {
		if (key == OptionalKey.INSTANCE) {
			return value;
		} else {
			return null;
		}
	}
}
