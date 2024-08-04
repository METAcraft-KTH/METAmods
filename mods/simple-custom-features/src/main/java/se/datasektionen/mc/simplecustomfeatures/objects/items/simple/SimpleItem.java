package se.datasektionen.mc.simplecustomfeatures.objects.items.simple;

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
import net.minecraft.component.ComponentMapImpl;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryPair;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import org.objectweb.asm.Type;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectRegistry;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectType;
import se.datasektionen.mc.simplecustomfeatures.objects.items.BaseItem;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public record SimpleItem(
		ItemSettingsWithBaseItem itemSettings, Optional<ItemStack> disguise,
		ExtendedSettings settings
) implements BaseItem {

	static final String SETTINGS_FIELD_NAME = "simple_custom_features$settings";

	public static final MapCodec<SimpleItem> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ITEM_SETTINGS_WITH_BASE_ITEM_CODEC.forGetter(item -> item.itemSettings),
					ItemStack.UNCOUNTED_CODEC.optionalFieldOf("disguise").forGetter(item -> item.disguise),
					ExtendedSettings.CODEC.fieldOf("settings").orElse(new ExtendedSettings()).forGetter(item -> item.settings)
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

	private DataResult<Item> createNewItem(Item.Settings settings) {
		var baseClass = itemSettings.baseItem().value().getClass();
		var newType = createItemBuilder(baseClass);
		try {
			var newClass = newType.make().load(baseClass.getClassLoader()).getLoaded();

			Constructor<?> constructor;
			Item item;
			try {
				constructor = newClass.getDeclaredConstructor(Item.Settings.class);
			} catch (NoSuchMethodException e) {
				return DataResult.error(
						() -> "Item constructor of " + itemSettings.baseItem().getIdAsString() + " is too complex, this base item is not yet supported."
				);
			}

			item = (Item) constructor.newInstance(settings);

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
	public DataResult<Item> createObject() {
		var components = ComponentMapImpl.create(itemSettings.baseItem().value().getComponents(), this.itemSettings().components());
		var result = ItemStack.validateComponents(components);
		if (result.isError()) {
			return result.map(e -> Items.AIR);
		}
		return itemSettings.makeSettings().flatMap(this::createNewItem);
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
