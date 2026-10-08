package se.metacraft.config_gui;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import nu.metacraft.lib.util.helper.PCollectionsHelper;
import org.jetbrains.annotations.Nullable;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import org.pcollections.PVector;
import org.pcollections.TreePVector;
import se.metacraft.config.parser.CodecParser;
import se.metacraft.config.parser.MetadataKey;
import se.metacraft.config.parser.metadata.*;
import se.metacraft.config.parser.result.AbstractCodecResult;
import se.metacraft.config.util.CommentCodec;
import se.metacraft.config.util.helper.CodecInternalsHelper;
import se.metacraft.config_gui.gui.value_editor.MapCodecEditor;
import se.metacraft.config_gui.gui.value_editor.click.handlers.Back;
import se.metacraft.config_gui.gui.value_editor.click.ClickHandler;
import se.metacraft.config_gui.gui.value_editor.click.handlers.SubMenu;
import se.metacraft.config_gui.message.DialogGUIHandler;
import se.metacraft.config_gui.mixin.IntegerPropertyAccessor;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public class CodecDialog {

	private static float getStepFromRange(Range range) {
		double diff = range.difference();
		return (float) diff / 10000;
	}

	private static boolean isPracticallyUnboundedMin(Number number) {
		return switch (number) {
			case Byte i -> i == Byte.MIN_VALUE;
			case Short i -> i == Short.MIN_VALUE;
			case Integer i -> i == Integer.MIN_VALUE;
			case Long i -> i == Long.MIN_VALUE;
			case Float i -> i == -Float.MAX_VALUE || i == Float.NEGATIVE_INFINITY || Float.isNaN(i);
			case Double i -> i == -Double.MAX_VALUE || i == Double.NEGATIVE_INFINITY || Double.isNaN(i);
			default -> false;
		};
	}

	private static boolean isPracticallyUnboundedMax(Number number) {
		return switch (number) {
			case Byte i -> i == Byte.MAX_VALUE;
			case Short i -> i == Short.MAX_VALUE;
			case Integer i -> i == Integer.MAX_VALUE;
			case Long i -> i == Long.MAX_VALUE;
			case Float i -> i == Float.MAX_VALUE || i == Float.POSITIVE_INFINITY || Float.isNaN(i);
			case Double i -> i == Double.MAX_VALUE || i == Double.POSITIVE_INFINITY || Double.isNaN(i);
			default -> false;
		};
	}

	private static boolean isRangeTooLarge(Range range) {
		return isPracticallyUnboundedMin(range.min()) || isPracticallyUnboundedMax(range.max());
	}

	private static InputControl forNumericRange(
		Component name, int width, @Nullable Range range,
		boolean onlyWholeNumbers, @Nullable Number currentValue
	) {
		if (range == null || isRangeTooLarge(range)) {
			return new TextInput(
				width, name, true,
				currentValue != null ? currentValue.toString() : "", 200, Optional.empty()
			);
		} else {
			return new NumberRangeInput(
				width, name, "options.generic_value",
				new NumberRangeInput.RangeInfo(
					range.min().floatValue(), range.max().floatValue(),
					Optional.ofNullable(currentValue).map(Number::floatValue),
					Optional.of(onlyWholeNumbers ? 1.0f : getStepFromRange(range))
				)
			);
		}
	}

	private static boolean isInt(Codec<?> codec, @Nullable Remainder remainder, HolderLookup.Provider lookup) {
		if (remainder != null) {
			if (remainder.parameters().stream().anyMatch(param -> param instanceof IntegerProperty)) return true;
		}
		return CodecInternalsHelper.isOneOfPrimitiveCodecs(codec, lookup, Codec.INT, Codec.SHORT, Codec.BYTE, Codec.LONG);
	}

	private static Range getRange(@Nullable Range range, @Nullable Remainder remainder) {
		if (range == null && remainder != null) {
			var prop = remainder.parameters().stream().filter(param -> param instanceof IntegerPropertyAccessor).map(param -> (IntegerPropertyAccessor) param).findAny();
			if (prop.isPresent()) {
				return new Range(prop.get().getMin(), prop.get().getMax());
			}
		}
		return range;
	}

	private static boolean isBool(Codec<?> codec, @Nullable Remainder remainder, HolderLookup.Provider lookup) {
		if (remainder != null) {
			if (remainder.parameters().stream().anyMatch(param -> param instanceof BooleanProperty)) return true;
		}
		return CodecInternalsHelper.isPrimitiveCodec(codec, Codec.BOOL, lookup);
	}

	private static List<SingleOptionInput.Entry> getEntries(Codec<?> codec, @Nullable Remainder remainder, @Nullable Object currentValue, HolderLookup.Provider lookup) {
		var entries = Entries.getEntries(codec, lookup);
		if (entries.isEmpty() && remainder != null) {
			var property = remainder.parameters().stream().filter(
				param -> param instanceof EnumProperty<?>
			).map(param -> (EnumProperty<?>) param).findAny();
			if (property.isPresent()) {
				return property.get().getPossibleValues().stream().map(
					e -> new SingleOptionInput.Entry(
						e.getSerializedName(), Optional.empty(),
						e.getSerializedName().equals(currentValue)
					)
				).toList();
			}
		}
		return entries.stream().map(
			e -> new SingleOptionInput.Entry(
				e.key(), Optional.empty(),
				e.key().equals(currentValue)
			)
		).toList();
	}

	private static boolean asBoolean(Object value) {
		if (value instanceof Boolean b) return b;
		if (value instanceof String s) return Boolean.parseBoolean(s);
		return false;
	}

	private static Number asNumber(Object value) {
		if (value instanceof Number n) return n;
		if (value instanceof String s) {
			if (s.contains(".")) {
				try {
					return Double.parseDouble(s);
				} catch (NumberFormatException err) {
					return null;
				}
			} else {
				try {
					return Long.parseLong(s);
				} catch (NumberFormatException err) {
					return null;
				}
			}
		}
		return null;
	}

	private static Optional<InputControl> createInput(
		Component label, Codec<?> codec,
		@Nullable Range range, @Nullable Remainder remainder, @Nullable Object currentValue, HolderLookup.Provider lookup
	) {
		if (currentValue != null) {
			currentValue = CodecInternalsHelper.forceEncode(codec, lookup.createSerializationContext(JavaOps.INSTANCE), currentValue).getOrThrow();
		}
		int width = 200;
		if (isInt(codec, remainder, lookup)) {
			return Optional.of(forNumericRange(label, width, getRange(range, remainder), true, asNumber(currentValue)));
		}
		if (CodecInternalsHelper.isOneOfPrimitiveCodecs(codec, lookup, Codec.FLOAT, Codec.DOUBLE)) {
			return Optional.of(forNumericRange(label, width, range, false, asNumber(currentValue)));
		}
		if (isBool(codec, remainder, lookup)) {
			return Optional.of(
				new BooleanInput(
					label, asBoolean(currentValue), "true", "false"
				)
			);
		}

		var entries = getEntries(codec, remainder, currentValue, lookup);
		if (!entries.isEmpty()) {
			return Optional.of(
				new SingleOptionInput(
					width, entries, label, true
				)
			);
		}
		// Codecs with a set of entries which we want as a SingleOptionInput often use the STRING codec as the base, which means we would ignore the entries if we check this any earlier.
		if (CodecInternalsHelper.isPrimitiveCodec(codec, Codec.STRING, lookup)) {
			return Optional.of(
				new TextInput(
					width, label, true,
					currentValue instanceof String s ? s : "", 200, Optional.empty()
				)
			);
		}
		return Optional.empty();
	}

	public static Holder<Dialog> template(
		List<DialogBody> body,
		List<Input> inputs, List<ActionButton> buttons, HolderLookup.Provider provider, int columns
	) {
		return template(
			body, inputs, buttons, provider, columns,
			new ActionButton(
				new CommonButtonData(Component.translatable("gui.back"), CommonButtonData.DEFAULT_WIDTH),
				Optional.of(ClickHandler.click(provider, Back.INSTANCE, !inputs.isEmpty()))
			)
		);
	}

	public static Holder<Dialog> template(
		List<DialogBody> body,
		List<Input> inputs, List<ActionButton> buttons, HolderLookup.Provider provider, int columns,
		ActionButton backButton
	) {
		var commonData = new CommonDialogData(
			Component.literal("Test"),
			Optional.empty(),
			true,
			true,
			DialogAction.WAIT_FOR_RESPONSE,
			body,
			inputs
		);
		if (buttons.isEmpty()) {
			return Holder.direct(
				new NoticeDialog(commonData, backButton)
			);
		} else {
			return Holder.direct(
				new MultiActionDialog(
					commonData,
					buttons,
					Optional.of(backButton),
					columns
				)
			);
		}
	}

	public record Dummy(
		int i,
		double d,
		Optional<String> f,
		double u,
		DataComponentMap item,
		Direction direction,
		Sub p
	) {
		public static final Codec<Dummy> DUMMY = RecordCodecBuilder.create(
			instance -> instance.group(
				CommentCodec.comment(
					ExtraCodecs.intRange(-2, 2).fieldOf("i"),
					"Another more random comment"
				).forGetter(Dummy::i),
				CommentCodec.comment(
					Codec.doubleRange(-100, 100),
					Component.literal("Test comment ").append(Component.object(new PlayerSprite(ResolvableProfile.createUnresolved("Acuadragon100"), true)))
				).fieldOf("d").forGetter(Dummy::d),
				CommentCodec.comment(
					Codec.STRING.optionalFieldOf("f"),
					"Random comment"
				).forGetter(Dummy::f),
				Codec.DOUBLE.optionalFieldOf("u", 2.0).forGetter(Dummy::u),
				DataComponentMap.CODEC.fieldOf("item").forGetter(Dummy::item),
				Direction.CODEC.fieldOf("dir").forGetter(Dummy::direction),
				Sub.CODEC.fieldOf("p").forGetter(Dummy::p)
			).apply(instance, Dummy::new)
		);

		public record Sub(
			int i, List<Holder<BlockStateProvider>> providers,
			Map<String, Integer> map,
			Holder<SoundEvent> sound
		) {
			public static final Codec<Sub> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
					Codec.INT.fieldOf("f").forGetter(Sub::i),
					BlockStateProvider.CODEC.listOf().fieldOf("providers").forGetter(Sub::providers),
					Codec.simpleMap(Codec.STRING, Codec.intRange(1, 100), Keyable.forStrings(Stream::empty)).fieldOf("map").forGetter(Sub::map),
					SoundEvent.CODEC.fieldOf("sound").forGetter(Sub::sound)
				).apply(instance, Sub::new)
			);
		}
	}

	public static Component getGUIFriendlyName(Object object) {
		if (object instanceof Holder.Reference<?> holder) {
			return Component.literal(holder.key().identifier().toString());
		}
		if (object instanceof Holder.Direct<?> direct) {
			return Component.literal(direct.value().toString());
		}
		if (object == null) {
			return Component.translatable("item.minecraft.bundle.empty");
		}
		return Component.literal(Objects.toString(object));
	}

	public static void test(ServerPlayer sp) {
		DialogGUIHandler.openGUI(
			sp, MapCodecEditor.create(
				new Dummy(1, 0.123, Optional.empty(), 2.0, DataComponentMap.EMPTY, Direction.WEST,
					new Dummy.Sub(
						54, List.of(Holder.direct(BlockStateProvider.of(Blocks.GRANITE.defaultBlockState()))),
						Map.of("123", 23),
						SoundEvents.AMBIENT_BASALT_DELTAS_ADDITIONS
					)
				),
				Dummy.DUMMY, sp.registryAccess(), Optional.empty()
			)
		);
	}

	public static Tag unbox(Tag tag, AbstractCodecResult element, HolderLookup.Provider lookup) {
		var codec = element.codec();
		if (tag instanceof NumericTag num && CodecInternalsHelper.isPrimitiveCodec(codec, Codec.STRING, lookup)) {
			if (
				element.nestedMetadata(MetadataKey.REMAINDER).map(
					rem -> rem.parameters().stream().anyMatch(e -> e instanceof BooleanProperty)
				).orElse(false)
			) {
				return StringTag.valueOf(Boolean.toString(num.byteValue() > 0));
			}
			if (
				element.nestedMetadata(MetadataKey.REMAINDER).map(
					rem -> rem.parameters().stream().anyMatch(e -> e instanceof IntegerProperty)
				).orElse(false)
			) {
				return StringTag.valueOf(num.asInt().orElse(0).toString());
			}
		}
		if (tag instanceof StringTag(String value) && !CodecInternalsHelper.isPrimitiveCodec(codec, Codec.STRING, lookup)) {
			// Numbers might be encoded as a string if we use text fields. We must parse.
			if (CodecInternalsHelper.isOneOfPrimitiveCodecs(codec, lookup, Codec.BYTE, Codec.BOOL)) {
				try {
					return ByteTag.valueOf(Byte.parseByte(value));
				} catch (NumberFormatException ignored) {}
			}
			if (CodecInternalsHelper.isPrimitiveCodec(codec, Codec.SHORT, lookup)) {
				try {
					return ShortTag.valueOf(Short.parseShort(value));
				} catch (NumberFormatException ignored) {}
			}
			if (CodecInternalsHelper.isPrimitiveCodec(codec, Codec.INT, lookup)) {
				try {
					return IntTag.valueOf(Integer.parseInt(value));
				} catch (NumberFormatException ignored) {}
			}
			if (CodecInternalsHelper.isPrimitiveCodec(codec, Codec.LONG, lookup)) {
				try {
					return LongTag.valueOf(Long.parseLong(value));
				} catch (NumberFormatException ignored) {}
			}
			if (CodecInternalsHelper.isPrimitiveCodec(codec, Codec.FLOAT, lookup)) {
				try {
					return FloatTag.valueOf(Float.parseFloat(value));
				} catch (NumberFormatException ignored) {}
			}
			if (CodecInternalsHelper.isPrimitiveCodec(codec, Codec.DOUBLE, lookup)) {
				try {
					return DoubleTag.valueOf(Double.parseDouble(value));
				} catch (NumberFormatException ignored) {}
			}
		}
		return tag;
	}

	public record Type(
		AbstractCodecResult element,
		Optional<AbstractCodecResult> field
	) {

		public static Type from(AbstractCodecResult element) {
			var unnamed = CodecInternalsHelper.getUnnamed(element);
			if (unnamed == element) {
				return new Type(element, Optional.empty());
			} else {
				return new Type(unnamed, Optional.of(element));
			}
		}

		public boolean optional() {
			var element = field.orElse(this.element);
			if (element.nestedMetadata(MetadataKey.DEFAULT_VALUE).isPresent()) return false;
			return !element.nestedMetadata(MetadataKey.NAMED_FIELD).map(
				NamedField::required
			).orElse(true);
		}

		public Type asRequired() {
			return new Type(element, Optional.empty());
		}

		public DataResult<?> parse(HolderLookup.Provider lookup, Tag tag) {
			return element.codec().parse(
				lookup.createSerializationContext(NbtOps.INSTANCE),
				unbox(tag, element, lookup)
			);
		}

		public Optional<Object> getDefaultValue() {
			return field.orElse(element).nestedMetadata(MetadataKey.DEFAULT_VALUE).map(
				DefaultValue::value
			);
		}

		public DataResult<Object> createEmpty(HolderLookup.Provider lookup) {
			return CodecInternalsHelper.defaultValue(element, lookup);
		}

		public Object parseRaw(Object rawValue, HolderLookup.Provider lookup) {
			return parseValue(element.codec(), lookup, rawValue);
		}

		public <T> DataResult<T> encode(DynamicOps<T> ops, Object value) {
			return CodecInternalsHelper.forceEncode(element.codec(), ops, value);
		}

		public <T> DataResult<T> encodeField(DynamicOps<T> ops, Object value) {
			return CodecInternalsHelper.forceEncode(field.orElse(element).codec(), ops, value);
		}

		public AbstractCodecResult fieldElement() {
			return field.orElse(element);
		}

	}

	private static <T> Object parseValue(Codec<T> codec, HolderLookup.Provider lookup, Object object) {
		return codec.parse(
			lookup.createSerializationContext(JavaOps.INSTANCE), object
		).resultOrPartial().orElseGet(
			() -> {
				try {
					//noinspection unchecked
					return (T) object;
				} catch (ClassCastException err) {
					return null;
				}
			}
		);
	}

	public static <T> Optional<Type> getEmptyListSubType(
		Codec<T> codec, HolderLookup.Provider lookup
	) {
		var element = CodecParser.parse(codec, lookup);
		for (var child : element.thisAndAllUnderlying()) {
			if (child.isContainerType(ContainerType.LIST)) {
				return Optional.of(Type.from(child.components().getFirst()));
			}
		}
		return Optional.empty();
	}

	public static <T> PVector<Object> getListCodecElements(
		Codec<T> codec, T value, HolderLookup.Provider lookup, Type subtype
	) {
		var ctx = lookup.createSerializationContext(JavaOps.INSTANCE);
		PVector<Object> list = TreePVector.empty();
		//noinspection unchecked
		List<Object> currentRawValues = (List<Object>) codec.encodeStart(
			ctx, value
		).getOrThrow();
		for (var v : currentRawValues) {
			list = list.plus(subtype.parseRaw(v, lookup));
		}
		return list;
	}

	public static <T> Optional<Pair<Type, Function<Object, Type>>> getEmptyMapSubTypes(
		Codec<T> codec, HolderLookup.Provider lookup
	) {
		var element = CodecParser.parse(codec, lookup);
		for (var child : element.thisAndAllUnderlying()) {
			if (child.isContainerType(ContainerType.MAP)) {
				var valueType = Type.from(child.components().getLast());
				return Optional.of(
					Pair.of(Type.from(child.components().getFirst()), o -> valueType)
				);
			}
			if (child.isContainerType(ContainerType.DISPATCHED_MAP)) {
				var containerData = child.getContainerData(ContainerType.DISPATCHED_MAP).orElseThrow();
				return Optional.of(
					Pair.of(
						Type.from(child.components().getFirst()),
						containerData.valueCodecFunction().andThen(c -> Type.from(CodecParser.parse(c, lookup)))
					)
				);
			}
		}
		return Optional.empty();
	}

	public static <T> PMap<Object, Object> getMapCodecElements(
		Codec<T> codec, T value, HolderLookup.Provider lookup, Pair<Type, Function<Object, Type>> subTypes
	) {
		var ctx = lookup.createSerializationContext(JavaOps.INSTANCE);
		//noinspection unchecked
		Map<Object, Object> currentRawValues = (Map<Object, Object>) codec.encodeStart(
			ctx, value
		).getOrThrow();
		return PCollectionsHelper.collectToMap(
			currentRawValues.entrySet().stream().map(
				entry -> {
					var key = subTypes.getFirst().parseRaw(entry.getKey(), lookup);
					return Pair.of(key, subTypes.getSecond().apply(key).parseRaw(entry.getValue(), lookup));
				}
			),
			Pair::getFirst, Pair::getSecond
		);
	}

	public static boolean isMapInlined(CodecDialog.Type type) {
		return type.element().hasContainerType(ContainerType.DISPATCHED_EITHER) && type.fieldElement().nestedMetadata(MetadataKey.NAMED_FIELD).isEmpty();
	}

	public static <T> Pair<PMap<String, Type>, Optional<Comments>> getMapCodecTypes(
		Codec<T> codec,HolderLookup.Provider lookup
	) {
		PMap<String, Type> types = HashTreePMap.empty();
		var parsed = CodecParser.parse(codec, lookup);
		for (var element : CodecInternalsHelper.getNamedElements(parsed)) {
			var name = element.nestedMetadata(MetadataKey.NAMED_FIELD).map(NamedField::name).orElse(null);
			if (name == null) {
				var dispatchedEither = element.getContainer(ContainerType.DISPATCHED_EITHER);
				if (dispatchedEither.isPresent()) {
					name = dispatchedEither.get().components().getFirst().nestedMetadata(
						MetadataKey.NAMED_FIELD
					).map(NamedField::name).orElse(null);
				}
			}
			if (name != null) {
				types = types.plus(name, Type.from(element));
			}
		}
		return Pair.of(types, parsed.nestedMetadata(MetadataKey.COMMENTS));
	}

	public static <T> PMap<String, Object> getMapCodecElements(
		Codec<T> codec, T value, HolderLookup.Provider lookup, PMap<String, Type> types
	) {
		var ctx = lookup.createSerializationContext(JavaOps.INSTANCE);
		//noinspection unchecked
		Map<Object, Object> currentRawValues = (Map<Object, Object>) codec.encodeStart(
			ctx, value
		).getOrThrow();

		PMap<String, Object> results = HashTreePMap.empty();

		for (var entry : types.entrySet()) {
			if (isMapInlined(entry.getValue())) {
				var parsed = entry.getValue().parseRaw(currentRawValues, lookup);
				if (parsed != null) {
					results = results.plus(
						entry.getKey(), parsed
					);
				} else if (entry.getValue().getDefaultValue().isPresent()) {
					results = results.plus(
						entry.getKey(), entry.getValue().getDefaultValue().get()
					);
				}
			} else {
				if (currentRawValues.containsKey(entry.getKey())) {
					results = results.plus(
						entry.getKey(),
						entry.getValue().parseRaw(currentRawValues.get(entry.getKey()), lookup)
					);
				} else if (entry.getValue().getDefaultValue().isPresent()) {
					results = results.plus(
						entry.getKey(),
						entry.getValue().getDefaultValue().get()
					);
				}
			}

		}

		return results;
	}

	private static final int USE_DROPDOWN_THRESHOLD = 10;
	public static final int DROPDOWN_USE_SEARCH_BAR_THRESHOLD = 40;

	public static ActionButton submenu(
		String key, HolderLookup.Provider registries, Component title, Optional<Component> tooltip
	) {
		return new ActionButton(
			new CommonButtonData(title, tooltip, CommonButtonData.DEFAULT_WIDTH),
			Optional.of(
				ClickHandler.click(
					registries, new SubMenu(key), true
				)
			)
		);
	}

	public static void addInput(
		String key, Component label, Type type, Object value,
		HolderLookup.Provider registries,
		List<DialogBody> body,
		List<ActionButton> buttons,
		List<Input> inputs,
		Optional<Component> tooltip
	) {
		var recursion = type.element().getContainerData(ContainerType.RECURSIVE);
		if (recursion.isPresent()) {
			addInput(key, label, Type.from(recursion.get().wrapped().get()), value, registries, body, buttons, inputs, tooltip);
			return;
		}
		type.element().mapCodec().ifPresentOrElse(
			codec -> buttons.add(submenu(key, registries, label, tooltip)),
			() -> {
				if (type.element().nestedMetadata(MetadataKey.ENTRIES).map(entries -> entries.strings().size() > USE_DROPDOWN_THRESHOLD).orElse(false)) {
					var selectedValueName = type.element().metadata(MetadataKey.ENTRIES).get().strings().stream().filter(
						e -> e.value() == value
					).map(Entries.Entry::key).findAny().orElse(null);
					buttons.add(submenu(key, registries, label.copy().append(": " + selectedValueName), tooltip));
				} else if (type.optional()) {
					buttons.add(submenu(key, registries, label.copy().append(": ").append(CodecDialog.getGUIFriendlyName(value)), tooltip));
				} else if (type.element().nestedMetadata(MetadataKey.CONTAINER).isPresent()) {
					buttons.add(submenu(key, registries, label, tooltip));
				} else {
					createInput(
						label,
						type.element().codec(),
						type.element().nestedMetadata(MetadataKey.RANGE).orElse(null),
						type.element().nestedMetadata(MetadataKey.REMAINDER).orElse(null),
						value, registries
					).ifPresent(input -> {
						inputs.add(new Input(key, input));
						tooltip.ifPresent(t -> body.add(new PlainMessage(label.copy().append(": ").append(t), 200)));
					});
				}
			}
		);
	}

	public static <T> Holder<Dialog> createDialog(
		Map<String, Object> object,
		Map<String, Type> types,
		Map<String, Component> tooltips,
		HolderLookup.Provider registries
	) {
		List<DialogBody> body = new ArrayList<>();
		List<ActionButton> buttons = new ArrayList<>();
		List<Input> inputs = new ArrayList<>();
		for (var value : types.entrySet()) {
			addInput(
				value.getKey(), Component.literal(value.getKey()),
				value.getValue(), object.get(value.getKey()), registries, body, buttons, inputs,
				Optional.ofNullable(tooltips.get(value.getKey()))
			);
		}

		return template(body, inputs, buttons, registries, 1);
	}

}
