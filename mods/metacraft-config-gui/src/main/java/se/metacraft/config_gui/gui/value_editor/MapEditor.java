package se.metacraft.config_gui.gui.value_editor;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import nu.metacraft.lib.util.helper.DataResultHelper;
import org.jspecify.annotations.NonNull;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import org.pcollections.PVector;
import org.pcollections.TreePVector;
import se.metacraft.config_gui.ClickHandlerGUI;
import se.metacraft.config_gui.CodecDialog;
import se.metacraft.config_gui.ConfigGUI;
import se.metacraft.config_gui.DialogGUI;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandler;
import se.metacraft.config_gui.gui.value_editor.click.handlers.RemoveObject;
import se.metacraft.config_gui.gui.value_editor.click.handlers.SubMenu;
import se.metacraft.config_gui.gui.value_editor.trait.RemovableByKey;
import se.metacraft.config_gui.gui.value_editor.trait.WithSubMenus;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public record MapEditor<T>(
	PMap<Object, Object> map,
	CodecDialog.Type keyType, Function<Object, CodecDialog.Type> valueTypeGetter,
	Codec<T> wrappingCodec, Optional<ParentInfo> parent
) implements ValueEditor<T>, RemovableByKey, WithSubMenus {

	public static <T> Optional<ClickHandlerGUI> create(
		T value, Codec<T> codec, HolderLookup.Provider lookup, Optional<ParentInfo> parent
	) {
		return CodecDialog.getEmptyMapSubTypes(codec, lookup).map(
			type -> new MapEditor<>(
				CodecDialog.getMapCodecElements(codec, value, lookup, type),
				type.getFirst(), type.getSecond(),
				codec, parent
			)
		);
	}

	private Object parseKey(Object k, HolderLookup.Provider lookup) {
		return keyType.element().codec().parse(
			lookup.createSerializationContext(JavaOps.INSTANCE), k
		).resultOrPartial().orElse(null);
	}

	public DataResult<CodecDialog.Type> getValueType(Object value) {
		try {
			return DataResult.success(valueTypeGetter.apply(value));
		} catch (IllegalStateException err) {
			return DataResult.error(err::getMessage); // Some item components cannot be serialized.
		}
	}

	private Optional<Update> performUpdate(
		Optional<?> newValue, SubMenu.SubMenuKey position, HolderLookup.Provider lookup
	) {
		if (position instanceof MapTarget(Object k, MapTarget.Target target)) {
			var key = parseKey(k, lookup);
			if (target == MapTarget.Target.KEY && newValue.isEmpty()) return Optional.empty();
			if (key == null) return Optional.empty();
			if (!map.containsKey(key)) return Optional.empty();
			return Optional.of(
				switch (target) {
					case KEY -> {
						var newKey = newValue.get();
						var oldValue = map.get(key);
						var oldValueType = getValueType(key);
						var newValueType = getValueType(newKey);
						var conversionResult = oldValueType.flatMap(
							o -> newValueType.flatMap(
								i -> o.element().convert(oldValue, i.element(), lookup)
							)
						);
						if (conversionResult.hasResultOrPartial()) {
							var valueResult = conversionResult.getPartialOrThrow();
							yield new SimpleUpdate(
								Optional.of(Pair.of(newKey, valueResult)),
								Optional.of(Pair.of(key, oldValue)),
								Update.getReplaced(newKey, map),
								conversionResult.error().map(DataResult.Error::message),
								map.minus(key).plus(newKey, valueResult)
							);
						} else {
							yield new UpdateWithDefaultValue(
								newValueType,
								Optional.of(Pair.of(newKey, valueResult -> valueResult)),
								Optional.of(Pair.of(key, oldValue)),
								Update.getReplaced(newKey, map),
								conversionResult.error().map(DataResult.Error::message),
								valueResult -> map.minus(key).plus(newKey, valueResult)
							);
						}
					}
					case VALUE -> newValue.map(
						v -> new SimpleUpdate(
							Optional.of(Pair.of(key, v)),
							Optional.of(Pair.of(key, map.get(key))),
							Optional.empty(),
							Optional.empty(),
							map.plus(key, v)
						)
					).orElse(
						new SimpleUpdate(
							Optional.empty(),
							Optional.of(Pair.of(key, map.get(key))),
							Optional.empty(),
							Optional.empty(),
							map.minus(key)
						)
					);
				}
			);
		} else if (position == New.INSTANCE) {
			if (newValue.isEmpty()) return Optional.empty();
			var newKey = newValue.get();
			return Optional.of(
				new UpdateWithDefaultValue(
					getValueType(newKey),
					Optional.of(Pair.of(newKey, value -> value)),
					Optional.empty(),
					Update.getReplaced(newKey, map),
					Optional.empty(),
					value -> map.plus(newKey, value)
				)
			);
		}
		return Optional.empty();
	}

	@Override
	public ClickHandlerGUI updateFromChild(HolderLookup.Provider lookup, ClickHandlerGUI child, SubMenu.SubMenuKey position) {
		if (child instanceof ValueEditor<?> e) {
			return PopupScreen.dataResult(
				e.getValue(lookup).map(v -> {
					var update = performUpdate(v, position, lookup);
					if (update.isEmpty()) return this;
					return update.get().nextGUI(this, position, lookup);
				}),
				this
			);
		}
		return this;
	}

	@Override
	public DataResult<Optional<T>> getValue(HolderLookup.Provider lookup) {
		DataResult<PMap<Object, Object>> map = DataResult.success(HashTreePMap.empty());
		var ctx = lookup.createSerializationContext(JavaOps.INSTANCE);
		for (var element : this.map.entrySet()) {
			//noinspection unchecked
			map = map.flatMap(
				m -> DataResultHelper.withPartialIfError(
					((Codec<Object>) keyType.element().codec()).encodeStart(
						ctx, element.getKey()
					).flatMap(
						key -> ((Codec<Object>) valueTypeGetter.apply(element.getKey()).element().codec()).encodeStart(
							ctx, element.getValue()
						).map(
							value -> m.plus(key, value)
						)
					),
					m
				)
			);
		}
		return map.flatMap(m -> wrappingCodec.parse(ctx, m)).map(Optional::of);
	}

	private static <T> Object forceEncode(Codec<T> codec, DynamicOps<?> ops, Object value) {
		//noinspection unchecked
		return codec.encodeStart(ops, (T) value).getOrThrow();
	}

	private static Component printEntry(Component key, Component value) {
		return Component.literal("").append(ValueEditor.highlight(key)).append(
			" -> "
		).append(ValueEditor.highlight(value));
	}

	@Override
	public Holder<Dialog> createDialog(ServerPlayer player) {
		List<ActionButton> buttons = new ArrayList<>();

		map.entrySet().stream().sorted(
			Comparator.comparing(e -> e.getKey().toString())
		).forEach(entry -> {
			var keyName = Component.literal(entry.getKey().toString());
			var valueName = Component.literal(entry.getValue().toString());

			var encodedKey = forceEncode(
				keyType.element().codec(),
				player.registryAccess().createSerializationContext(JavaOps.INSTANCE),
				entry.getKey()
			);

			// Key Editor
			buttons.add(
				new ActionButton(
					new CommonButtonData(
						keyName, CommonButtonData.DEFAULT_WIDTH
					),
					Optional.of(ClickHandler.click(
						player.registryAccess(),
						new SubMenu(new MapTarget(encodedKey, MapTarget.Target.KEY)),
						false
					))
				)
			);

			// Value Editor
			buttons.add(
				new ActionButton(
					new CommonButtonData(
						valueName, CommonButtonData.DEFAULT_WIDTH
					),
					Optional.of(ClickHandler.click(
						player.registryAccess(),
						new SubMenu(new MapTarget(encodedKey, MapTarget.Target.VALUE)),
						false
					))
				)
			);

			// Remove button
			buttons.add(
				new ActionButton(
					ValueEditor.removeButtonData(),
					Optional.of(ConfirmScreen.click(
						new RemoveObject(encodedKey),
						ValueEditor.removeMessage(printEntry(keyName, valueName)),
						player.registryAccess()
					))
				)
			);
		});

		buttons.add(
			new ActionButton(
				new CommonButtonData(
					Component.translatable("mco.create.world"), 50
				),
				Optional.of(ClickHandler.click(player.registryAccess(), new SubMenu(New.INSTANCE), false))
			)
		);

		return CodecDialog.template(
			List.of(), List.of(), buttons, player.registryAccess(), 3
		);
	}

	@Override
	public DialogGUI remove(ServerPlayer player, Object k) {
		var key = parseKey(k, player.registryAccess());
		if (!map.containsKey(key)) return this;
		return new MapEditor<>(
			map.minus(key), keyType, valueTypeGetter, wrappingCodec, parent
		);
	}

	@Override
	public CodecDialog.Type getType(SubMenu.SubMenuKey key, ServerPlayer player) {
		if (key instanceof MapTarget(Object k, MapTarget.Target target)) {
			return switch (target) {
				case KEY -> keyType;
				case VALUE -> {
					var realKey = parseKey(k, player.registryAccess());
					if (realKey == null) yield null;
					yield valueTypeGetter.apply(realKey);
				}
			};
		}
		if (key == New.INSTANCE) {
			return keyType;
		}
		return null;
	}

	@Override
	public Object getObject(SubMenu.SubMenuKey key, ServerPlayer player) {
		if (key instanceof MapTarget(Object k, MapTarget.Target target)) {
			return switch (target) {
				case KEY -> parseKey(k, player.registryAccess());
				case VALUE -> {
					var realKey = parseKey(k, player.registryAccess());
					if (realKey == null) yield null;
					yield map.get(realKey);
				}
			};
		}
		return null;
	}

	public static class New implements SubMenu.SubMenuKey {

		public static final New INSTANCE = new New();
		public static final MapCodec<New> CODEC = MapCodec.unit(INSTANCE);

		private New() {}

		@Override
		public MapCodec<? extends SubMenu.SubMenuKey> codec() {
			return CODEC;
		}
	}

	public record MapTarget(Object key, Target target) implements SubMenu.SubMenuKey {

		public static final MapCodec<MapTarget> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
				ExtraCodecs.JAVA.fieldOf("key").forGetter(MapTarget::key),
				Target.CODEC.fieldOf("target").forGetter(MapTarget::target)
			).apply(instance, MapTarget::new)
		);

		@Override
		public MapCodec<? extends SubMenu.SubMenuKey> codec() {
			return CODEC;
		}

		public enum Target implements StringRepresentable {
			KEY("key"), VALUE("value");

			public static final Codec<Target> CODEC = StringRepresentable.fromEnum(Target::values);

			final String id;

			Target(String id) {
				this.id = id;
			}

			@Override
			public @NonNull String getSerializedName() {
				return id;
			}
		}
	}

	public interface Update {
		ClickHandlerGUI nextGUI(MapEditor<?> source, SubMenu.SubMenuKey position, HolderLookup.Provider lookup);

		static Optional<Pair<Object, Object>> getReplaced(Object newKey, PMap<Object, Object> map) {
			return map.containsKey(newKey) ? Optional.of(Pair.of(newKey, map.get(newKey))): Optional.empty();
		}
	}

	public record SimpleUpdate(
		Optional<Pair<Object, Object>> newValue,
		Optional<Pair<Object, Object>> oldValue,
		Optional<Pair<Object, Object>> replaced,
		Optional<String> conversionError,
		PMap<Object, Object> resultMap
	) implements Update {

		private static final PlainMessage ARE_YOU_SURE = new PlainMessage(
			Component.translatable("mco.configure.world.reset.question.line2"),
			300
		);

		private PVector<DialogBody> getNewValueMessage() {
			PVector<DialogBody> messages = TreePVector.empty();
			if (newValue.isPresent()) {
				Component toAddKey = Component.literal(newValue.get().getFirst().toString());
				Component toAddValue = Component.literal(newValue.get().getSecond().toString());
				if (oldValue.isPresent()) {
					Component oldKeyName = Component.literal(oldValue.get().getFirst().toString());
					Component oldValueName = Component.literal(oldValue.get().getSecond().toString());
					messages = messages.plus(
						new PlainMessage(
							Component.literal("You are about to change ").append(
								printEntry(oldKeyName, oldValueName)
							).append(" to ").append(
								printEntry(toAddKey, toAddValue)
							),
							300
						)
					);
					if (conversionError.isPresent()) {
						messages = messages.plus(
							new PlainMessage(
								Component.literal(
									"Value conversion failed: " + conversionError.get()
								).withColor(TextColor.YELLOW),
								300
							)
						);
					}
				} else {
					messages = messages.plus(
						new PlainMessage(
							Component.literal("You are about to add ").append(
								printEntry(toAddKey, toAddValue)
							),
							300
						)
					);
				}
			}
			return messages;
		}

		private Optional<DialogBody> getReplacementMessage() {
			if (replaced.isPresent() && !replaced.equals(oldValue)) {
				Component toRemoveKey = Component.literal(replaced().get().getFirst().toString());
				Component toRemoveValue = Component.literal(replaced().get().getSecond().toString());
				return Optional.of(
					new PlainMessage(
						Component.literal("This will overwrite ").append(
							printEntry(toRemoveKey, toRemoveValue)
						).withColor(TextColor.RED),
						300
					)
				);
			}
			return Optional.empty();
		}

		private Optional<DialogBody> getRemovalMessage() {
			if (oldValue.isPresent() && newValue.isEmpty()) {
				Component oldKeyName = Component.literal(oldValue.get().getFirst().toString());
				Component oldValueName = Component.literal(oldValue.get().getSecond().toString());
				return Optional.of(
					new PlainMessage(
						Component.literal("You are about to remove ").append(
							printEntry(oldKeyName, oldValueName)
						).withColor(TextColor.RED),
						300
					)
				);
			}
			return Optional.empty();
		}

		private MapEditor<?> newEditor(MapEditor<?> source) {
			return new MapEditor<>(
				resultMap, source.keyType, source.valueTypeGetter, source.wrappingCodec, source.parent
			);
		}

		@Override
		public ClickHandlerGUI nextGUI(MapEditor<?> source, SubMenu.SubMenuKey position, HolderLookup.Provider lookup) {
			var newValue = getNewValueMessage();
			var removal = getRemovalMessage();
			var replacement = getReplacementMessage();

			if (removal.isPresent() || replacement.isPresent() || newValue.size() > 1) {
				List<DialogBody> fullMessage = Stream.concat(
					Stream.concat(newValue.stream(), removal.stream()), Stream.concat(replacement.stream(), Stream.of(ARE_YOU_SURE))
				).toList();
				return new ConfirmScreen.Simple(
					fullMessage,
					newEditor(source),
					source
				);
			} else {
				return newEditor(source);
			}
		}

	}

	public record UpdateWithDefaultValue(
		DataResult<CodecDialog.Type> valueType,
		Optional<Pair<Object, Function<Object, Object>>> newValue,
		Optional<Pair<Object, Object>> oldValue,
		Optional<Pair<Object, Object>> replaced,
		Optional<String> conversionError,
		Function<Object, PMap<Object, Object>> resultMap
	) implements Update {

		private SimpleUpdate toSimple(Object value, Optional<String> additionalError) {
			return new SimpleUpdate(
				newValue.map(pair -> Pair.of(pair.getFirst(), pair.getSecond().apply(value))),
				oldValue, replaced,
				conversionError.map(err -> err + additionalError.map(e -> "; " + e).orElse("")).or(() -> additionalError),
				resultMap.apply(value)
			);
		}

		@Override
		public ClickHandlerGUI nextGUI(MapEditor<?> source, SubMenu.SubMenuKey position, HolderLookup.Provider lookup) {
			if (!valueType.hasResultOrPartial()) {
				List<DialogBody> info = new ArrayList<>();
				newValue.ifPresent(
					v -> info.add(
						new PlainMessage(
							Component.literal(v.getFirst() + " does not appear to be supported."),
							PlainMessage.DEFAULT_WIDTH
						)
					)
				);
				valueType.error().ifPresent(error -> {
					info.add(
						new PlainMessage(
							Component.literal(error.message()),
							PlainMessage.DEFAULT_WIDTH
						)
					);
				});
				return new PopupScreen(
					PopupScreen.FAILED,
					info,
					new SubMenuParentInfo(source, position)
				);
			}
			var valueType = valueType().getPartialOrThrow();
			var defaultValue = valueType.createEmpty(lookup);
			if (defaultValue.hasResultOrPartial()) {
				return toSimple(
					defaultValue.getPartialOrThrow(), defaultValue.error().map(DataResult.Error::message)
				).nextGUI(source, position, lookup);
			}
			return new RawInputScreen.ScreenGenerator(
				valueType.element().codec(),
				value -> toSimple(
					value, defaultValue.error().map(DataResult.Error::message)
				).nextGUI(source, position, lookup),
				Optional.of(new SubMenuParentInfo(source, position)),
				defaultValue.error().map(DataResult.Error::message)
			);
		}
	}

}
