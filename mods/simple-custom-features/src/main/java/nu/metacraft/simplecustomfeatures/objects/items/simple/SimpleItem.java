package nu.metacraft.simplecustomfeatures.objects.items.simple;

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
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.DispenserBlock;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import nu.metacraft.simplecustomfeatures.Features;
import nu.metacraft.simplecustomfeatures.FeaturesConfig;
import nu.metacraft.simplecustomfeatures.mixin.AccessorItem;
import nu.metacraft.simplecustomfeatures.objects.ObjectRegistry;
import nu.metacraft.simplecustomfeatures.objects.ObjectType;
import nu.metacraft.simplecustomfeatures.objects.items.BaseItem;

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
			ResourceKey<TypeObject> name
	) {}

	private static final ResourceKey<? extends Registry<TypeObject>> KEY = ResourceKey.createRegistryKey(Features.getID("type_object"));

	private static final Map<Class<?>, TypeObject> SUPPORTED_TYPES = new HashMap<>();
	private static final Map<ResourceKey<TypeObject>, TypeObject> NAMED_TYPES = new HashMap<>();

	private static final Codec<TagKey<?>> ARBITRARY_TAG_CODEC = fixTagDispatch(ResourceKey.codec(BuiltInRegistries.REGISTRY.key()));

	private static <R extends ResourceKey<? extends Registry<?>>> Codec<TagKey<?>> fixTagDispatch(Codec<R> codec) {
		return codec.dispatch(key -> (R) key.registry(), t -> fixTagCodec(t).fieldOf("tag"));
	}

	private static <T> Codec<TagKey<T>> fixTagCodec(ResourceKey<? extends Registry<?>> registryRef) {
		return TagKey.hashedCodec((ResourceKey<? extends Registry<T>>) registryRef);
	}

	private static final Codec<ResourceKey<?>> ARBITRARY_KEY_CODEC = fixKeyDispatch(ResourceKey.codec(BuiltInRegistries.REGISTRY.key()));

	private static <R extends ResourceKey<? extends Registry<?>>> Codec<ResourceKey<?>> fixKeyDispatch(Codec<R> codec) {
		return codec.dispatch(key -> (R) key.registryKey(), t -> fixKeyCodec(t).fieldOf("key"));
	}

	private static <T> Codec<ResourceKey<T>> fixKeyCodec(ResourceKey<? extends Registry<?>> registryRef) {
		return ResourceKey.codec((ResourceKey<? extends Registry<T>>) registryRef);
	}

	private static final Codec<? extends Holder<?>> ARBITRARY_ENTRY_CODEC = new Codec<>() {

		private static <T, V> DataResult<Pair<Holder<? extends V>, T>> handleKey(Pair<? extends ResourceKey<? extends V>, T> key, RegistryOps<T> r) {
			Optional<HolderGetter<V>> lookup = r.getter(key.getFirst().registryKey());
			if (lookup.isEmpty()) {
				return DataResult.error(() -> "Registry " + key.getFirst().registryKey().location() + " could not be found.");
			}
			var entry = lookup.get().get((ResourceKey<V>) key.getFirst());
			return entry.<DataResult<Pair<Holder<? extends V>, T>>>map(
					vReference -> DataResult.success(Pair.of(vReference, key.getSecond()))
			).orElseGet(
					() -> DataResult.error(() -> key.getFirst().location() + " was not present in " + key.getFirst().registryKey().location())
			);
		}

		@Override
		public <T> DataResult<Pair<Holder<?>, T>> decode(DynamicOps<T> ops, T input) {
			if (ops instanceof RegistryOps<T> r) {
				return ARBITRARY_KEY_CODEC.decode(ops, input).flatMap(key -> handleKey(key, r));
			} else {
				return DataResult.error(() -> "Registry ops not present!");
			}
		}

		@Override
		public <T> DataResult<T> encode(Holder<?> input, DynamicOps<T> ops, T prefix) {
			if (input.unwrapKey().isPresent()) {
				return DataResult.error(() -> "Cannot serialize non-registered entry " + input);
			}
			return ARBITRARY_KEY_CODEC.encode(input.unwrapKey().get(), ops, prefix);
		}
	};

	private static final Codec<?> ARBITRARY_CODEC = ResourceKey.codec(KEY).dispatch(
			object -> SUPPORTED_TYPES.get(object.getClass()).name,
			type -> NAMED_TYPES.get(type).codec.fieldOf("object")
	);

	private static final Codec<? extends List<?>> ARBITRARY_LIST_CODEC = ARBITRARY_CODEC.listOf();

	private static ResourceKey<TypeObject> keyOf(ResourceLocation id) {
		return ResourceKey.create(KEY, id);
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
		addSupportedArgumentType(Number.class, ResourceLocation.fromNamespaceAndPath("java", "number"), Codec.DOUBLE);
		addSupportedArgumentType(String.class, ResourceLocation.fromNamespaceAndPath("java", "string"), Codec.STRING);
		addSupportedArgumentType(ResourceLocation.class, ResourceLocation.withDefaultNamespace("identifier"), ResourceLocation.CODEC);
		addSupportedArgumentType(ArmorType.class, ResourceLocation.withDefaultNamespace("equipment_type"), ArmorType.CODEC);
		addSupportedArgumentType(Component.class, ResourceLocation.withDefaultNamespace("text"), ComponentSerialization.CODEC);
		BuiltInRegistries.REGISTRY.forEach(registry -> {
			registry.listElements().forEach(entry -> {
				if (!SUPPORTED_TYPES.containsKey(entry.value().getClass())) {
					addSupportedArgumentType(entry.value().getClass(), registry.key().location(), registry.byNameCodec());
				}
			});
			registry.getAny().ifPresent(entry -> {
				var clazz = entry.value().getClass();
				while (clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class) {
					clazz = clazz.getSuperclass();
					if (!SUPPORTED_TYPES.containsKey(clazz)) {
						addSupportedArgumentType(clazz, registry.key().location(), registry.byNameCodec());
					}
				}
			});
		});
		addSupportedArgumentType(ToolMaterial.class, ResourceLocation.withDefaultNamespace("tool_material"), ToolMaterialRegistry.CODEC);
		addSupportedArgumentType(ArmorMaterial.class, ResourceLocation.withDefaultNamespace("armor_material"), ArmorMaterialRegistry.CODEC);

		addSupportedArgumentType(TagKey.class, ResourceLocation.withDefaultNamespace("tag"), ARBITRARY_TAG_CODEC);
		addSupportedArgumentType(ResourceKey.class, ResourceLocation.withDefaultNamespace("registry_key"), ARBITRARY_KEY_CODEC);
		addSupportedArgumentType(Holder.class, ResourceLocation.withDefaultNamespace("registry_entry"), ARBITRARY_ENTRY_CODEC);
		addSupportedArgumentType(List.class, ResourceLocation.fromNamespaceAndPath("java", "list"), ARBITRARY_LIST_CODEC);
	}

	static final String SETTINGS_FIELD_NAME = "simple_custom_features$settings";

	public static final MapCodec<SimpleItem> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ITEM_SETTINGS_WITH_BASE_ITEM_CODEC.forGetter(item -> item.itemSettings),
					ItemStack.SINGLE_ITEM_CODEC.optionalFieldOf("disguise").forGetter(item -> item.disguise),
					ExtraCodecs.JAVA.listOf().optionalFieldOf("args", new ArrayList<>()).forGetter(item -> item.args)
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
		return Arrays.stream(paramTypes).anyMatch(c -> c == Item.Properties.class);
	}

	private static DataResult<Object> getObjectForParam(Object object, Class<?> paramType, @Nullable HolderLookup.Provider lookup) {
		if (paramType.isInstance(object) || object.getClass() == paramType) {
			return DataResult.success(object);
		}
		if (SUPPORTED_TYPES.containsKey(paramType)) {
			var type = SUPPORTED_TYPES.get(paramType);
			return type.codec.parse(
					lookup != null ? lookup.createSerializationContext(JavaOps.INSTANCE) : JavaOps.INSTANCE, object
			).map(t -> t);
		}
		return DataResult.error(() -> paramType + " is not a supported type.");
	}

	private static <T> void setComponentFromChanges(DataComponentMap.Builder builder, DataComponentType<T> c, DataComponentPatch changes) {
		builder.set(c, changes.get(c).orElse(null));
	}

	private Item fixItem(Item item) {
		List<DataComponentType<?>> componentsToFix = new ArrayList<>();
		for (var c : itemSettings.components().entrySet()) {
			if (!Objects.equals(item.components().get(c.getKey()), c.getValue().orElse(null))) {
				componentsToFix.add(c.getKey());
			}
		}
		if (!componentsToFix.isEmpty()) {
			var builder = DataComponentMap.builder();
			builder.addAll(item.components());
			for (var c : componentsToFix) {
				setComponentFromChanges(builder, c, itemSettings.components());
			}
			((AccessorItem) item).setComponents(builder.build());
		}
		return item;
	}

	private DataResult<Item> createItem(
			Class<? extends Item> clazz, Item.Properties settings, @Nullable HolderLookup.Provider lookup
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
			int settingsIndex = Arrays.asList(c.getParameterTypes()).indexOf(Item.Properties.class);
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

	private DataResult<Item> createNewItem(Item.Properties settings, @Nullable HolderLookup.Provider lookup) {
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
	public DataResult<Item> createObject(ResourceKey<Item> id, @Nullable HolderLookup.Provider lookup) {
		var components = PatchedDataComponentMap.fromPatch(itemSettings.baseItem().value().components(), this.itemSettings().components());
		var result = ItemStack.validateComponents(components);
		if (result.isError()) {
			return result.map(e -> Items.AIR);
		}
		return itemSettings.makeSettings(
				id, disguise.map(stack -> stack.get(DataComponents.ITEM_MODEL)).orElse(null)
		).flatMap(settings -> this.createNewItem(settings, lookup));
	}

	@Override
	public void onRegistrationSuccess(Holder.Reference<Item> entry) {
		BaseItem.super.onRegistrationSuccess(entry);
		if (DispenserBlock.DISPENSER_REGISTRY.containsKey(itemSettings.baseItem().value())) {
			DispenserBlock.DISPENSER_REGISTRY.put(entry.value(), DispenserBlock.DISPENSER_REGISTRY.get(itemSettings.baseItem().value()));
		}
	}

	@Override
	public void onUnregister(Holder<Item> entry) {
		BaseItem.super.onUnregister(entry);
		DispenserBlock.DISPENSER_REGISTRY.remove(entry.value());
	}

	private static void addPrimitive(Class<?> clazz, Class<?> primitiveClass, String name, Codec<?> codec) {
		var type = new TypeObject(codec, false, keyOf(ResourceLocation.fromNamespaceAndPath("java", name)));
		addSupportedArgumentType(clazz, type);
		addSupportedArgumentType(primitiveClass, type);
	}

	public static void addSupportedArgumentType(Class<?> clazz, TypeObject object) {
		SUPPORTED_TYPES.put(clazz, object);
		NAMED_TYPES.put(object.name, object);
	}

	public static void addSupportedArgumentType(Class<?> clazz, ResourceLocation name, Codec<?> codec) {
		addSupportedArgumentType(clazz, new TypeObject(codec, false, keyOf(name)));
	}

	public static void addRegistryLookupType(Class<?> clazz, ResourceLocation name, Codec<?> codec) {
		addSupportedArgumentType(clazz, new TypeObject(codec, true, keyOf(name)));
	}
}
