package se.datasektionen.mc.simplecustomfeatures.objects.items.simple;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.item.PolymerItem;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.DispenserBlock;
import net.minecraft.component.*;
import net.minecraft.item.*;
import net.minecraft.item.equipment.ArmorMaterial;
import net.minecraft.item.equipment.EquipmentType;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import se.datasektionen.mc.simplecustomfeatures.Features;
import se.datasektionen.mc.simplecustomfeatures.FeaturesConfig;
import se.datasektionen.mc.simplecustomfeatures.mixin.AccessorItem;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectRegistry;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectType;
import se.datasektionen.mc.simplecustomfeatures.objects.items.BaseItem;

import java.lang.reflect.AccessFlag;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Supplier;

public record SimpleItem(
		ItemSettingsWithBaseItem itemSettings, Optional<ItemStack> disguise, List<Object> args
) implements BaseItem {

	public record TypeObject(
			Codec<?> codec,
			boolean requiresLookup,
			RegistryKey<TypeObject> name
	) {}

	private static final RegistryKey<? extends Registry<TypeObject>> KEY = RegistryKey.ofRegistry(Features.getID("type_object"));

	private static final Map<Class<?>, TypeObject> SUPPORTED_TYPES = new HashMap<>();
	private static final Map<RegistryKey<TypeObject>, TypeObject> NAMED_TYPES = new HashMap<>();

	private static final Codec<TagKey<?>> ARBITRARY_TAG_CODEC = fixTagDispatch(RegistryKey.createCodec(Registries.REGISTRIES.getKey()));

	private static <R extends RegistryKey<? extends Registry<?>>> Codec<TagKey<?>> fixTagDispatch(Codec<R> codec) {
		return codec.dispatch(key -> (R) key.registryRef(), t -> fixTagCodec(t).fieldOf("tag"));
	}

	private static <T> Codec<TagKey<T>> fixTagCodec(RegistryKey<? extends Registry<?>> registryRef) {
		return TagKey.codec((RegistryKey<? extends Registry<T>>) registryRef);
	}

	private static final Codec<RegistryKey<?>> ARBITRARY_KEY_CODEC = fixKeyDispatch(RegistryKey.createCodec(Registries.REGISTRIES.getKey()));

	private static <R extends RegistryKey<? extends Registry<?>>> Codec<RegistryKey<?>> fixKeyDispatch(Codec<R> codec) {
		return codec.dispatch(key -> (R) key.getRegistryRef(), t -> fixKeyCodec(t).fieldOf("key"));
	}

	private static <T> Codec<RegistryKey<T>> fixKeyCodec(RegistryKey<? extends Registry<?>> registryRef) {
		return RegistryKey.createCodec((RegistryKey<? extends Registry<T>>) registryRef);
	}

	private static final Codec<? extends RegistryEntry<?>> ARBITRARY_ENTRY_CODEC = new Codec<>() {

		private static <T, V> DataResult<Pair<RegistryEntry<? extends V>, T>> handleKey(Pair<? extends RegistryKey<? extends V>, T> key, RegistryOps<T> r) {
			Optional<RegistryEntryLookup<V>> lookup = r.getEntryLookup(key.getFirst().getRegistryRef());
			if (lookup.isEmpty()) {
				return DataResult.error(() -> "Registry " + key.getFirst().getRegistryRef().getValue() + " could not be found.");
			}
			var entry = lookup.get().getOptional((RegistryKey<V>) key.getFirst());
			return entry.<DataResult<Pair<RegistryEntry<? extends V>, T>>>map(
					vReference -> DataResult.success(Pair.of(vReference, key.getSecond()))
			).orElseGet(
					() -> DataResult.error(() -> key.getFirst().getValue() + " was not present in " + key.getFirst().getRegistryRef().getValue())
			);
		}

		@Override
		public <T> DataResult<Pair<RegistryEntry<?>, T>> decode(DynamicOps<T> ops, T input) {
			if (ops instanceof RegistryOps<T> r) {
				return ARBITRARY_KEY_CODEC.decode(ops, input).flatMap(key -> handleKey(key, r));
			} else {
				return DataResult.error(() -> "Registry ops not present!");
			}
		}

		@Override
		public <T> DataResult<T> encode(RegistryEntry<?> input, DynamicOps<T> ops, T prefix) {
			if (input.getKey().isPresent()) {
				return DataResult.error(() -> "Cannot serialize non-registered entry " + input);
			}
			return ARBITRARY_KEY_CODEC.encode(input.getKey().get(), ops, prefix);
		}
	};

	private static final Codec<?> ARBITRARY_CODEC = RegistryKey.createCodec(KEY).dispatch(
			object -> SUPPORTED_TYPES.get(object.getClass()).name,
			type -> NAMED_TYPES.get(type).codec.fieldOf("object")
	);

	private static final Codec<? extends List<?>> ARBITRARY_LIST_CODEC = ARBITRARY_CODEC.listOf();

	private static RegistryKey<TypeObject> keyOf(Identifier id) {
		return RegistryKey.of(KEY, id);
	}

	static {
		addPrimitive(Boolean.class, Boolean.TYPE, "boolean", Codec.BOOL);
		addPrimitive(Byte.class, Byte.TYPE, "byte", Codec.BYTE);
		addPrimitive(
				Character.class, Character.TYPE, "char",
				Codec.STRING.comapFlatMap(
						s -> s.length() == 1 ? DataResult.success(s.charAt(0)) :
								DataResult.error(() -> "Character string must be only one character"),
						String::valueOf
				)
		);
		addPrimitive(Short.class, Short.TYPE, "short", Codec.SHORT);
		addPrimitive(Integer.class, Integer.TYPE, "int", Codec.INT);
		addPrimitive(Long.class, Long.TYPE, "long", Codec.LONG);
		addPrimitive(Float.class, Float.TYPE, "float", Codec.FLOAT);
		addPrimitive(Double.class, Double.TYPE, "double", Codec.DOUBLE);
		addSupportedArgumentType(Number.class, Identifier.of("java", "number"), Codec.DOUBLE);
		addSupportedArgumentType(String.class, Identifier.of("java", "string"), Codec.STRING);
		addSupportedArgumentType(Identifier.class, Identifier.ofVanilla("identifier"), Identifier.CODEC);
		addSupportedArgumentType(EquipmentType.class, Identifier.ofVanilla("equipment_type"), EquipmentType.CODEC);
		addSupportedArgumentType(Text.class, Identifier.ofVanilla("text"), TextCodecs.CODEC);
		Registries.REGISTRIES.forEach(registry -> {
			registry.streamEntries().forEach(entry -> {
				if (!SUPPORTED_TYPES.containsKey(entry.value().getClass())) {
					addSupportedArgumentType(entry.value().getClass(), registry.getKey().getValue(), registry.getCodec());
				}
			});
			registry.getDefaultEntry().ifPresent(entry -> {
				var clazz = entry.value().getClass();
				while (clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class) {
					clazz = clazz.getSuperclass();
					if (!SUPPORTED_TYPES.containsKey(clazz)) {
						addSupportedArgumentType(clazz, registry.getKey().getValue(), registry.getCodec());
					}
				}
			});
		});
		addSupportedArgumentType(ToolMaterial.class, Identifier.ofVanilla("tool_material"), ToolMaterialRegistry.CODEC);
		addSupportedArgumentType(ArmorMaterial.class, Identifier.ofVanilla("armor_material"), ArmorMaterialRegistry.CODEC);

		addSupportedArgumentType(TagKey.class, Identifier.ofVanilla("tag"), ARBITRARY_TAG_CODEC);
		addSupportedArgumentType(RegistryKey.class, Identifier.ofVanilla("registry_key"), ARBITRARY_KEY_CODEC);
		addSupportedArgumentType(RegistryEntry.class, Identifier.ofVanilla("registry_entry"), ARBITRARY_ENTRY_CODEC);
		addSupportedArgumentType(List.class, Identifier.of("java", "list"), ARBITRARY_LIST_CODEC);
	}

	static final String SETTINGS_FIELD_NAME = "simple_custom_features$settings";

	public static final MapCodec<SimpleItem> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ITEM_SETTINGS_WITH_BASE_ITEM_CODEC.forGetter(item -> item.itemSettings),
					ItemStack.UNCOUNTED_CODEC.optionalFieldOf("disguise").forGetter(item -> item.disguise),
					Codecs.BASIC_OBJECT.listOf().optionalFieldOf("args", new ArrayList<>()).forGetter(item -> item.args)
			).apply(instance, SimpleItem::new)
	);

	@Override
	public ObjectType<? extends SimpleItem, Item> getType() {
		return ObjectRegistry.SIMPLE_ITEM;
	}

	private static DynamicType.Builder<? extends Item> createItemBuilder(Class<? extends Item> baseClass) {
		var mappingResolver = FabricLoader.getInstance().getMappingResolver();
		return new ByteBuddy()
				.subclass(baseClass).implement(PolymerItem.class).implement(CustomisedItem.class)
				.defineField(SETTINGS_FIELD_NAME, SimpleItem.class, Visibility.PRIVATE)
				.method(
						ElementMatchers.isDeclaredBy(Item.class).and(
								ElementMatchers.namedOneOf(
										Arrays.stream(ItemProxy.class.getDeclaredMethods()).map(
												method -> mappingResolver.mapMethodName(
														"named", method.getDeclaringClass().getCanonicalName(),
														method.getName(), Type.getMethodDescriptor(method)
												)
										).toArray(String[]::new)
								)
						).or(
								ElementMatchers.isDeclaredBy(PolymerItem.class).or(ElementMatchers.isDeclaredBy(
										CustomisedItem.class
								)).and(ElementMatchers.namedOneOf(
										Arrays.stream(ItemProxy.class.getDeclaredMethods()).map(Method::getName).toArray(String[]::new)
								))
						)
				).intercept(
						MethodDelegation.to(ItemProxy.class)
				);
	}

	private static boolean hasItemSettings(Class<?>[] paramTypes) {
		return Arrays.stream(paramTypes).anyMatch(c -> c == Item.Settings.class);
	}

	private static DataResult<Object> getObjectForParam(Object object, Class<?> paramType, @Nullable RegistryWrapper.WrapperLookup lookup) {
		if (paramType.isInstance(object) || object.getClass() == paramType) {
			return DataResult.success(object);
		}
		if (SUPPORTED_TYPES.containsKey(paramType)) {
			var type = SUPPORTED_TYPES.get(paramType);
			return type.codec.parse(
					lookup != null ? lookup.getOps(JavaOps.INSTANCE) : JavaOps.INSTANCE, object
			).map(t -> t);
		}
		return DataResult.error(() -> paramType + " is not a supported type.");
	}

	private static <T> void setComponentFromChanges(ComponentMap.Builder builder, ComponentType<T> c, ComponentChanges changes) {
		builder.add(c, changes.get(c).orElse(null));
	}

	private Item fixItem(Item item) {
		List<ComponentType<?>> componentsToFix = new ArrayList<>();
		for (var c : itemSettings.components().entrySet()) {
			if (!Objects.equals(item.getComponents().get(c.getKey()), c.getValue().orElse(null))) {
				componentsToFix.add(c.getKey());
			}
		}
		if (!componentsToFix.isEmpty()) {
			var builder = ComponentMap.builder();
			builder.addAll(item.getComponents());
			for (var c : componentsToFix) {
				setComponentFromChanges(builder, c, itemSettings.components());
			}
			((AccessorItem) item).setComponents(builder.build());
		}
		return item;
	}

	private DataResult<Item> createItem(
			Class<? extends Item> clazz, Item.Settings settings, @Nullable RegistryWrapper.WrapperLookup lookup
			) {
		Constructor<?>[] constructors = clazz.getDeclaredConstructors();
		Arrays.sort(constructors, (lhs, rhs) -> {
			if (lhs.accessFlags().contains(AccessFlag.PUBLIC) && !rhs.accessFlags().contains(AccessFlag.PUBLIC)) {
				return -1;
			}
			if (rhs.accessFlags().contains(AccessFlag.PUBLIC) && !lhs.accessFlags().contains(AccessFlag.PUBLIC)) {
				return 1;
			}
			if (lhs.accessFlags().contains(AccessFlag.PROTECTED) && !rhs.accessFlags().contains(AccessFlag.PROTECTED)) {
				return -1;
			}
			if (rhs.accessFlags().contains(AccessFlag.PROTECTED) && !lhs.accessFlags().contains(AccessFlag.PROTECTED)) {
				return 1;
			}
			if (lhs.accessFlags().contains(AccessFlag.PRIVATE) && !rhs.accessFlags().contains(AccessFlag.PRIVATE)) {
				return 1;
			}
			if (rhs.accessFlags().contains(AccessFlag.PRIVATE) && !lhs.accessFlags().contains(AccessFlag.PRIVATE)) {
				return -1;
			}

			var lhsParams = lhs.getParameterTypes();
			var rhsParams = rhs.getParameterTypes();

			if (hasItemSettings(lhsParams) && !hasItemSettings(rhsParams)) {
				return -1;
			}
			if (!hasItemSettings(lhsParams) && hasItemSettings(rhsParams)) {
				return 1;
			}

			return Integer.compare(lhsParams.length, rhsParams.length);
		});
		List<Supplier<String>> errors = new ArrayList<>();
		boolean requiresLookup = false;
		for (var c : constructors) {
			int settingsIndex = Arrays.asList(c.getParameterTypes()).indexOf(Item.Settings.class);
			List<Object> objects = new ArrayList<>(args);
			if (settingsIndex != -1 && settingsIndex <= objects.size()) {
				objects.add(settingsIndex, settings);
			}
			var paramTypes = c.getParameterTypes();
			if (objects.size() == paramTypes.length) {
				List<DataResult<Object>> results = new ArrayList<>(objects.size());
				for (int i = 0; i < objects.size(); i++) {
					if (SUPPORTED_TYPES.containsKey(paramTypes[i]) && SUPPORTED_TYPES.get(paramTypes[i]).requiresLookup) {
						requiresLookup = true;
					}
					results.add(getObjectForParam(objects.get(i), paramTypes[i], lookup));
				}
				var parsedResults = FeaturesConfig.unwrapDataResults(results.stream());
				if (parsedResults.isError()) {
					errors.add(() -> "Arguments invalid for constructor " + c + ": " + parsedResults.error().orElseThrow().message());
					continue;
				}
				if (parsedResults.isSuccess()) {
					try {
						return DataResult.success(fixItem((Item) c.newInstance(parsedResults.getOrThrow().toArray())));
					} catch (
							InstantiationException | IllegalAccessException | InvocationTargetException |
							ClassCastException | ExceptionInInitializerError | IllegalArgumentException e
					) {
						errors.add(() -> "Arguments invalid for constructor " + c + ": " + e.getMessage());
					}
				}
			} else {
				errors.add(() -> "Constructor " + c + " skipped.");
			}
		}
		String prefix = requiresLookup ? NO_ERROR_PREFIX : "";
		return DataResult.error(
				() -> prefix + "Item could not be created: " +
				errors.stream().map(Supplier::get).reduce(DataResult::appendMessages).orElse("Unknown reason")
		);
	}

	private DataResult<Item> createNewItem(Item.Settings settings, @Nullable RegistryWrapper.WrapperLookup lookup) {
		var baseClass = itemSettings.baseItem().value().getClass();
		var newType = createItemBuilder(baseClass);
		try {
			var newClass = newType.make().load(baseClass.getClassLoader()).getLoaded();

			DataResult<Item> itemR = createItem(newClass, settings, lookup);

			if (itemR.hasResultOrPartial()) {
				var item = itemR.getOrThrow();
				var field = item.getClass().getDeclaredField(SETTINGS_FIELD_NAME);
				field.setAccessible(true);
				field.set(item, this);
				field.setAccessible(false);
			}

			return itemR;

		} catch (
				NoClassDefFoundError | NoSuchFieldException |
				IllegalAccessException e
		) {
			e.printStackTrace();
			return DataResult.error(e::getMessage);
		}
	}

	@Override
	public DataResult<Item> createObject(RegistryKey<Item> id, @Nullable RegistryWrapper.WrapperLookup lookup) {
		var components = MergedComponentMap.create(itemSettings.baseItem().value().getComponents(), this.itemSettings().components());
		var result = ItemStack.validateComponents(components);
		if (result.isError()) {
			return result.map(e -> Items.AIR);
		}
		return itemSettings.makeSettings(
				id, disguise.map(stack -> stack.get(DataComponentTypes.ITEM_MODEL)).orElse(null)
		).flatMap(settings -> this.createNewItem(settings, lookup));
	}

	@Override
	public void onRegistrationSuccess(RegistryEntry.Reference<Item> entry) {
		BaseItem.super.onRegistrationSuccess(entry);
		if (DispenserBlock.BEHAVIORS.containsKey(itemSettings.baseItem().value())) {
			DispenserBlock.BEHAVIORS.put(entry.value(), DispenserBlock.BEHAVIORS.get(itemSettings.baseItem().value()));
		}
	}

	@Override
	public void onUnregister(RegistryEntry<Item> entry) {
		BaseItem.super.onUnregister(entry);
		DispenserBlock.BEHAVIORS.remove(entry.value());
	}

	private static void addPrimitive(Class<?> clazz, Class<?> primitiveClass, String name, Codec<?> codec) {
		var type = new TypeObject(codec, false, keyOf(Identifier.of("java", name)));
		addSupportedArgumentType(clazz, type);
		addSupportedArgumentType(primitiveClass, type);
	}

	public static void addSupportedArgumentType(Class<?> clazz, TypeObject object) {
		SUPPORTED_TYPES.put(clazz, object);
		NAMED_TYPES.put(object.name, object);
	}

	public static void addSupportedArgumentType(Class<?> clazz, Identifier name, Codec<?> codec) {
		addSupportedArgumentType(clazz, new TypeObject(codec, false, keyOf(name)));
	}

	public static void addRegistryLookupType(Class<?> clazz, Identifier name, Codec<?> codec) {
		addSupportedArgumentType(clazz, new TypeObject(codec, true, keyOf(name)));
	}
}
