package se.datasektionen.mc.simplecustomfeatures.objects.items.simple;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.item.PolymerItem;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.MergedComponentMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;
import org.objectweb.asm.Type;
import se.datasektionen.mc.simplecustomfeatures.FeaturesConfig;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectRegistry;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectType;
import se.datasektionen.mc.simplecustomfeatures.objects.items.BaseItem;

import java.lang.reflect.AccessFlag;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public record SimpleItem(
		ItemSettingsWithBaseItem itemSettings, Optional<ItemStack> disguise, List<Object> args
) implements BaseItem {

	private static final Map<Class<?>, Function<Object, DataResult<Object>>> SUPPORTED_TYPES = new HashMap<>();

	private static void addPrimitive(Class<?> clazz, Class<?> primitiveClass, Codec<?> codec) {
		addSupportedArgumentType(primitiveClass, codec);
		addSupportedArgumentType(clazz, codec);
	}

	static {
		addPrimitive(Boolean.class, Boolean.TYPE, Codec.BOOL);
		addPrimitive(Byte.class, Byte.TYPE, Codec.BYTE);
		addPrimitive(
				Character.class, Character.TYPE,
				Codec.STRING.comapFlatMap(
						s -> s.length() == 1 ? DataResult.success(s.charAt(0)) :
								DataResult.error(() -> "Character string must be only one character"),
						String::valueOf
				)
		);
		addPrimitive(Short.class, Short.TYPE, Codec.SHORT);
		addPrimitive(Integer.class, Integer.TYPE, Codec.INT);
		addPrimitive(Long.class, Long.TYPE, Codec.LONG);
		addPrimitive(Float.class, Float.TYPE, Codec.FLOAT);
		addPrimitive(Double.class, Double.TYPE, Codec.DOUBLE);
		addSupportedArgumentType(Number.class, Codec.DOUBLE);
		addSupportedArgumentType(String.class, Codec.STRING);
		addSupportedArgumentType(Identifier.class, Identifier.CODEC);
		Registries.REGISTRIES.forEach(registry -> {
			registry.streamEntries().forEach(entry -> {
				if (!SUPPORTED_TYPES.containsKey(entry.value().getClass())) {
					addSupportedArgumentType(entry.value().getClass(), registry.getCodec());
				}
			});
			registry.getDefaultEntry().ifPresent(entry -> {
				var clazz = entry.value().getClass();
				while (clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class) {
					clazz = clazz.getSuperclass();
					if (!SUPPORTED_TYPES.containsKey(clazz)) {
						addSupportedArgumentType(clazz, registry.getCodec());
					}
				}
			});
		});
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

	private static DataResult<Object> getObjectForParam(Object object, Class<?> paramType) {
		if (paramType.isInstance(object) || object.getClass() == paramType) {
			return DataResult.success(object);
		}
		if (SUPPORTED_TYPES.containsKey(paramType)) {
			var converter = SUPPORTED_TYPES.get(paramType);
			return converter.apply(object).map(t -> t);
		}
		return DataResult.error(() -> paramType + " is not a supported type.");
	}

	private DataResult<Item> createItem(
			Class<? extends Item> clazz, Item.Settings settings
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
					results.add(getObjectForParam(objects.get(i), paramTypes[i]));
				}
				var parsedResults = FeaturesConfig.unwrapDataResults(results.stream());
				if (parsedResults.isError()) {
					errors.add(() -> "Arguments invalid for constructor " + c + ": " + parsedResults.error().orElseThrow().message());
					continue;
				}
				if (parsedResults.isSuccess()) {
					try {
						if (settingsIndex == -1) {
							return DataResult.error(
									() -> "The accepted constructor does not take settings as an argument, custom components have not been applied!",
									(Item) c.newInstance(parsedResults.getOrThrow().toArray())
							);
						} else {
							return DataResult.success((Item) c.newInstance(parsedResults.getOrThrow().toArray()));
						}
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
		return DataResult.error(
				() -> "Item could not be created: " +
				errors.stream().map(Supplier::get).reduce(DataResult::appendMessages).orElse("Unknown reason")
		);
	}

	private DataResult<Item> createNewItem(Item.Settings settings) {
		var baseClass = itemSettings.baseItem().value().getClass();
		var newType = createItemBuilder(baseClass);
		try {
			var newClass = newType.make().load(baseClass.getClassLoader()).getLoaded();

			/*Constructor<?> constructor;
			Item item;
			try {
				constructor = newClass.getDeclaredConstructor(Item.Settings.class);
			} catch (NoSuchMethodException e) {
				return DataResult.error(
						() -> "Item constructor of " + itemSettings.baseItem().getIdAsString() + " is too complex, this base item is not yet supported."
				);
			}

			item = (Item) constructor.newInstance(settings);*/
			DataResult<Item> itemR = createItem(newClass, settings);

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
	public DataResult<Item> createObject(RegistryKey<Item> id) {
		var components = MergedComponentMap.create(itemSettings.baseItem().value().getComponents(), this.itemSettings().components());
		var result = ItemStack.validateComponents(components);
		if (result.isError()) {
			return result.map(e -> Items.AIR);
		}
		return itemSettings.makeSettings(
				id, disguise.map(stack -> stack.get(DataComponentTypes.ITEM_MODEL)).orElse(null)
		).flatMap(this::createNewItem);
	}

	public static void addSupportedArgumentType(Class<?> clazz, Codec<?> codec) {
		SUPPORTED_TYPES.put(
				clazz,
				object -> (DataResult<Object>) codec.parse(JavaOps.INSTANCE, object)
		);
	}

	public static void addSupportedArgumentType(Class<?> clazz, Function<Object, DataResult<Object>> converter) {
		SUPPORTED_TYPES.put(clazz, converter);
	}
}
