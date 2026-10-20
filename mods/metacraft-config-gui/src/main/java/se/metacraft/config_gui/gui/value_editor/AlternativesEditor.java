package se.metacraft.config_gui.gui.value_editor;

import com.mojang.serialization.*;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerPlayer;
import org.pcollections.PVector;
import org.pcollections.TreePVector;
import se.metacraft.config.parser.metadata.ContainerType;
import se.metacraft.config.parser.result.AbstractCodecResult;
import se.metacraft.config_gui.ClickHandlerGUI;
import se.metacraft.config_gui.CodecDialog;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandler;
import se.metacraft.config_gui.gui.value_editor.click.handlers.SubMenu;
import se.metacraft.config_gui.gui.value_editor.trait.WithSubMenus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public record AlternativesEditor<T>(
	PVector<TypeWithValue> values, Codec<T> wrappingCodec, int currentCodec, Optional<ParentInfo> parent
) implements ValueEditor<T>, WithSubMenus {

	private static <T> DataResult<?> forceEncode(Encoder<T> codec, DynamicOps<?> ops, Object value) {
		//noinspection unchecked
		return codec.encodeStart(ops, (T) value);
	}

	public static ClickHandlerGUI create(
		Object value, CodecDialog.Type outerType,
		Collection<CodecDialog.Type> types, Function<CodecDialog.Type, Codec<?>> codecObtainer,
		HolderLookup.Provider lookup, Optional<ParentInfo> parent
	) {
		int selectedIndex = -1;
		PVector<TypeWithValue> potentialEntries = TreePVector.empty();
		for (var type : types) {
			var container = new TypeWithValue(
				type, null, codecObtainer.apply(type)
			);
			var conversion = outerType.element().convert(value, type.element(), lookup).resultOrPartial();
			if (conversion.isPresent()) {
				container = container.withValue(conversion.get());
				if (selectedIndex == -1) {
					selectedIndex = potentialEntries.size();
				}
			}
			potentialEntries = potentialEntries.plus(container);
		}

		final int si = selectedIndex;
		Function<PVector<TypeWithValue>, ClickHandlerGUI> openCallback = elements -> create(
			elements, outerType, si, parent
		);
		for (int i = 0; i < potentialEntries.size(); i++) {
			var entry = potentialEntries.get(i);
			if (entry.value() == null) {
				var prev = openCallback;
				final int currentIndex = i;

				BiFunction<PVector<TypeWithValue>, Object, ClickHandlerGUI> newCallback = (elements, newValue) -> prev.apply(
					elements.with(currentIndex, elements.get(currentIndex).withValue(newValue))
				);

				openCallback = entry.type().createEmpty(lookup).mapOrElse(
					newValue -> elements -> newCallback.apply(elements, newValue),
					error -> elements -> new RawInputScreen.ScreenGenerator(
						entry.codec(),
						newValue -> newCallback.apply(elements, newValue),
						parent,
						Optional.of(error.message())
					)
				);
			}
		}
		return openCallback.apply(potentialEntries);
	}

	private static <T> AlternativesEditor<T> create(
		PVector<TypeWithValue> values,
		CodecDialog.Type outerType,
		int selectedIndex,
		Optional<ParentInfo> parent
	) {
		//noinspection unchecked
		return new AlternativesEditor<>(
			values, (Codec<T>) outerType.element().codec(),
			selectedIndex, parent
		);
	}

	private static List<CodecDialog.Type> findTypes(AbstractCodecResult type) {
		List<CodecDialog.Type> types = new ArrayList<>();
		for (var child : type.thisAndAllUnderlying()) {
			if (child.isContainerType(ContainerType.EITHER)) {
				for (var direct : child.components()) {
					if (direct.hasContainerType(ContainerType.EITHER)) {
						types.addAll(findTypes(direct));
					} else {
						types.add(CodecDialog.Type.from(direct));
					}
				}
			}
		}
		return types;
	}

	public static <T> ClickHandlerGUI create(
		Object value, CodecDialog.Type type, HolderLookup.Provider lookup, Optional<ParentInfo> parent
	) {
		return create(value, type, findTypes(type.element()), c -> c.element().codec(), lookup, parent);
	}


	private static String codecName(String fullName) {
		int endIndex = fullName.indexOf("[");
		if (endIndex != -1) {
			return fullName.substring(0, endIndex);
		} else {
			return fullName;
		}
	}

	@Override
	public Holder<Dialog> createDialog(ServerPlayer player) {
		List<ActionButton> buttons = new ArrayList<>();
		for (int i = 0; i < values.size(); i++) {
			var name = Component.literal(
				codecName(values.get(i).type().element().codecName()) + ": "
			).append(CodecDialog.getGUIFriendlyName(values.get(i).value()));
			buttons.add(new ActionButton(
				new CommonButtonData(
					i == currentCodec ? ValueEditor.highlight(name) : name,
					300
				),
				Optional.of(ClickHandler.click(
					player.registryAccess(),
					new SubMenu(i),
					false
				))
			));
		}
		return CodecDialog.template(
			List.of(), List.of(), buttons, player.registryAccess(), 1
		);
	}

	@Override
	public ClickHandlerGUI updateFromChild(HolderLookup.Provider lookup, ClickHandlerGUI child, SubMenu.SubMenuKey position) {
		if (position instanceof SubMenu.I(int i) && child instanceof ValueEditor<?> e) {
			if (i < 0) return this;
			if (i >= values.size()) return this;
			return PopupScreen.dataResult(
				e.getValue(lookup),
				value -> value.map(object -> new AlternativesEditor<>(
					values.with(i, values.get(i).withValue(object)),
					wrappingCodec, i, parent
				)).orElse(this),
				this
			);
		}
		return this;
	}

	@Override
	public DataResult<Optional<T>> getValue(HolderLookup.Provider lookup) {
		var ctx = lookup.createSerializationContext(JavaOps.INSTANCE);
		return forceEncode(values.get(currentCodec).codec(), ctx,values.get(currentCodec).value()).flatMap(
			encoded -> wrappingCodec.parse(ctx, encoded)
		).map(Optional::of);
	}

	@Override
	public CodecDialog.Type getType(SubMenu.SubMenuKey key, ServerPlayer player) {
		if (key instanceof SubMenu.I(int i)) {
			return values.get(i).type();
		} else {
			return null;
		}
	}

	@Override
	public Object getObject(SubMenu.SubMenuKey key, ServerPlayer player) {
		if (key instanceof SubMenu.I(int i)) {
			return values.get(i).value();
		} else {
			return null;
		}
	}

	public record TypeWithValue(
		CodecDialog.Type type, Object value, Codec<?> codec
	) {
		public TypeWithValue withValue(Object value) {
			if (this.value == value) return this;
			return new TypeWithValue(type, value, codec);
		}
	}
}
