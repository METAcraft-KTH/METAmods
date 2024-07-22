package se.datasektionen.mc.simplecustomfeatures.objects.items;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.item.PolymerItem;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.Component;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.ComponentMapImpl;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryPair;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import org.objectweb.asm.Type;
import se.datasektionen.mc.simplecustomfeatures.RegistryExtensions;
import se.datasektionen.mc.simplecustomfeatures.mixin.AccessorItem;
import se.datasektionen.mc.simplecustomfeatures.objects.BaseObject;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectRegistry;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectType;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public record SimpleItem(
		RegistryEntry<Item> baseItem, Optional<ItemStack> disguise,
		Optional<RegistryEntry<Item>> recipeRemainder,
		ComponentChanges components,
		ExtendedSettings settings
) implements BaseObject<Item> {

	static final String SETTINGS_FIELD_NAME = "simple_custom_features$settings";

	private static final Multimap<RegistryKey<Item>, Item> OLD_ITEMS = HashMultimap.create();

	public static final MapCodec<SimpleItem> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ItemStack.ITEM_CODEC.fieldOf("base_item").forGetter(item -> item.baseItem),
					ItemStack.UNCOUNTED_CODEC.optionalFieldOf("disguise").forGetter(item -> item.disguise),
					ItemStack.ITEM_CODEC.optionalFieldOf("recipe_remainder").forGetter(item -> item.recipeRemainder),
					ComponentChanges.CODEC.optionalFieldOf("components", ComponentChanges.EMPTY).forGetter(item -> item.components),
					ExtendedSettings.CODEC.fieldOf("settings").orElse(new ExtendedSettings()).forGetter(item -> item.settings)
			).apply(instance, SimpleItem::new)
	);

	private static <T> void addComponent(Item.Settings settings, Component<T> component) {
		settings.component(component.type(), component.value());
	}

	private Item.Settings createSettings(ComponentMap map) {
		var settings = new Item.Settings();
		for (var entry : map) {
			addComponent(settings, entry);
		}
		recipeRemainder.ifPresent(remainder -> {
			settings.recipeRemainder(remainder.value());
		});
		return settings;
	}

	@Override
	public ObjectType<? extends SimpleItem, Item> getType() {
		return ObjectRegistry.SIMPLE_ITEM;
	}

	@Override
	public DataResult<Item> createObject() {
		var components = ComponentMapImpl.create(baseItem.value().getComponents(), this.components);
		var result = ItemStack.validateComponents(components);
		if (result.isError()) {
			return result.map(e -> Items.AIR);
		}

		var baseClass = baseItem.value().getClass();
		var mappingResolver = FabricLoader.getInstance().getMappingResolver();
		DynamicType.Builder<? extends Item> newType = new ByteBuddy()
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
		try {
			var newClass = newType.make().load(baseClass.getClassLoader()).getLoaded();

			Constructor<?> constructor;
			Item item;
			try {
				constructor = newClass.getDeclaredConstructor(Item.Settings.class);
			} catch (NoSuchMethodException e) {
				return DataResult.error(
						() -> "Item constructor of " + baseItem.getIdAsString() + " is too complex, this base item is not yet supported."
				);
			}

			item = (Item) constructor.newInstance(createSettings(components));

			var field = item.getClass().getDeclaredField(SETTINGS_FIELD_NAME);
			field.setAccessible(true);
			field.set(item, this);
			field.setAccessible(false);

			return DataResult.success(item);

		} catch (
				NoClassDefFoundError | InstantiationException | NoSuchFieldException |
				IllegalAccessException | InvocationTargetException e
		) {
			e.printStackTrace();
			return DataResult.error(e::getMessage);
		}
	}

	@Override
	public void onUnregister(RegistryEntry<Item> entry) {
		entry.getKey().ifPresent(key -> {
			OLD_ITEMS.put(key, entry.value());
		});
	}

	@Override
	public void onRegistrationFail(Item value) {
		((RegistryExtensions<Item>) Registries.ITEM).simpleCustomFeatures$removeIntrusiveEntry(value);
	}

	@Override
	public void onRegistrationSuccess(RegistryEntry.Reference<Item> entry) {
		entry.getKey().ifPresent(key -> {
			OLD_ITEMS.get(key).forEach(oldItem -> {
				((AccessorItem) oldItem).setRegistryEntry(entry);
				((RegistryExtensions<Item>) Registries.ITEM).simpleCustomFeatures$addLegacyRef(key, oldItem);
			});
		});
	}

	public record ExtendedSettings(
			boolean isDrink, List<RegistryPair<StatusEffect>> effectsToRemove,
			Optional<RegistryEntry<SoundEvent>> consumeSound
	) {

		public ExtendedSettings() {
			this(
					false, new ArrayList<>(),
					Optional.empty()
			);
		}
		public static final Codec<ExtendedSettings> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.BOOL.fieldOf("isDrink").orElse(false).forGetter(item -> item.isDrink),
						RegistryPair.createCodec(RegistryKeys.STATUS_EFFECT, StatusEffect.ENTRY_CODEC).listOf().fieldOf("effectsToRemove").orElse(new ArrayList<>()).forGetter(item -> item.effectsToRemove),
						SoundEvent.ENTRY_CODEC.optionalFieldOf("consumeSound").forGetter(item -> item.consumeSound)
				).apply(instance, ExtendedSettings::new)
		);
	}
}
