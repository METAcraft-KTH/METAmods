package se.metacraft.config_gui.gui.value_editor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JavaOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TextComponentTagVisitor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import se.metacraft.config.parser.MetadataKey;
import se.metacraft.config.parser.metadata.NamedField;
import se.metacraft.config.util.helper.CodecInternalsHelper;
import se.metacraft.config_gui.ClickHandlerGUI;
import se.metacraft.config_gui.CodecDialog;
import se.metacraft.config_gui.ConfigGUI;
import se.metacraft.config_gui.gui.value_editor.click.handlers.SubMenu;
import se.metacraft.config_gui.gui.value_editor.trait.WithSubMenus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public record DispatchedEitherEditor<T>(
	CodecDialog.Type keyType,
	Object key,
	Function<Object, DataResult<CodecDialog.Type>> valueTypeProvider,
	CodecDialog.Type prevValueType,
	Object value,
	Codec<T> wrappingCodec,
	Optional<ParentInfo> parent
) implements ValueEditor<T>, WithSubMenus {

	private static final String FALLBACK_KEY = "type";
	private static final String VALUE = "dispatch$value";

	public DispatchedEitherEditor(
		CodecDialog.Type keyType,
		Object key,
		Function<Object, DataResult<CodecDialog.Type>> valueTypeProvider,
		Object value,
		Codec<T> wrappingCodec,
		Optional<ParentInfo> parent
	) {
		this(keyType, key, valueTypeProvider, prevValueTypeFromCurrent(key, valueTypeProvider, keyType), value, wrappingCodec, parent);
	}

	@Override
	public Optional<T> getValue(HolderLookup.Provider lookup) {
		var ctx = lookup.createSerializationContext(JavaOps.INSTANCE);
		return keyType.encodeField(ctx, key).flatMap(
			keyResult -> prevValueType.encodeField(ctx, value).flatMap(
				valueResult -> wrappingCodec.parse(
					ctx, CodecInternalsHelper.mergeWithPrefix(valueResult, keyResult, ctx)
				)
			)
		).resultOrPartial();
	}

	private String keyField() {
		return keyType.fieldElement().nestedMetadata(MetadataKey.NAMED_FIELD).map(
			NamedField::name
		).orElse(FALLBACK_KEY);
	}

	@Override
	public ValueEditor<T> updateFromChild(HolderLookup.Provider lookup, ClickHandlerGUI child, SubMenu.SubMenuKey position) {
		if (position instanceof SubMenu.S(String s) && child instanceof ValueEditor<?> e) {
			var newValue = e.getValue(lookup);
			var keyField = keyField();
			return newValue.map(object -> {
				var newValueObject = s.equals(VALUE) ? object : (
					s.equals(keyField) ?
						valueTypeProvider.apply(object).flatMap(
							v -> v.createEmpty(lookup)
						).resultOrPartial().orElse(value) :
						value
				);
				return new DispatchedEitherEditor<>(
					keyType, s.equals(keyField) ? object : key,
					valueTypeProvider, prevValueTypeFromCurrent(key, valueTypeProvider, prevValueType), newValueObject,
					wrappingCodec, parent
				);
			}).orElse(this);
		}
		return this;
	}

	private static CodecDialog.Type prevValueTypeFromCurrent(
		Object key, Function<Object, DataResult<CodecDialog.Type>> valueTypeProvider,
		CodecDialog.Type fallback
	) {
		return valueTypeProvider.apply(key).resultOrPartial(
			ConfigGUI.LOGGER::error
		).orElse(fallback);
	}

	@Override
	public ValueEditor<T> updateFromMessage(ServerPlayer player, CompoundTag tag) {
		var keyField = keyField();
		if (!tag.contains(keyField)) return this;
		var result = keyType.parse(player.registryAccess(), tag.get(keyField)).resultOrPartial(
			err -> player.sendSystemMessage(Component.literal(err))
		);
		return result.map(
			o -> new DispatchedEitherEditor<>(
				keyType, o, valueTypeProvider,
				prevValueType,
				value, wrappingCodec, parent
			)
		).orElse(this);
	}

	@Override
	public Holder<Dialog> createDialog(ServerPlayer player) {
		List<ActionButton> buttons = new ArrayList<>();
		List<Input> inputs = new ArrayList<>();
		List<DialogBody> body = new ArrayList<>();
		var ctx = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		prevValueType.encodeField(ctx, value).resultOrPartial(err -> {
			body.add(new PlainMessage(
				Component.literal("Error: ").append(Component.literal(err).withColor(TextColor.RED)), 300
			));
		}).ifPresent(result -> {
			body.add(new PlainMessage(
				Component.literal("Current Value: \n").append(
					NbtUtils.toPrettyComponent(result)
				), 300
			));
		});
		CodecDialog.addInput(
			keyField(), Component.literal(keyField()), keyType, key, player.registryAccess(),
			body, buttons, inputs, Optional.empty()
		);
		buttons.add(
			CodecDialog.submenu(
				VALUE, player.registryAccess(),
				Component.translatable("selectWorld.edit"), Optional.empty()
			)
		);
		return CodecDialog.template(
			body, inputs, buttons, player.registryAccess(), 1
		);
	}

	@Override
	public CodecDialog.Type getType(SubMenu.SubMenuKey key, ServerPlayer player) {
		if (key instanceof SubMenu.S(String s)) {
			if (s.equals(keyField())) {
				return keyType;
			}
			if (s.equals(VALUE)) {
				return valueTypeProvider.apply(this.key).resultOrPartial().orElse(null);
			}
		}
		return null;
	}

	@Override
	public Object getObject(SubMenu.SubMenuKey key, ServerPlayer player) {
		if (key instanceof SubMenu.S(String s)) {
			if (s.equals(keyField())) {
				return this.key;
			}
			if (s.equals(VALUE)) {
				var newType = valueTypeProvider.apply(this.key).resultOrPartial().orElse(null);
				if (newType == null) return null;
				return prevValueType.element().convert(value, newType.element(), player.registryAccess()).resultOrPartial().orElse(null);
			}
		}
		return null;
	}
}
