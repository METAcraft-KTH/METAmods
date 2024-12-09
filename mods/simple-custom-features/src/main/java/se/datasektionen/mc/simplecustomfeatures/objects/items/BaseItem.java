package se.datasektionen.mc.simplecustomfeatures.objects.items;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.component.*;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeyedValue;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import se.datasektionen.mc.simplecustomfeatures.RegistryHelper;
import se.datasektionen.mc.simplecustomfeatures.extension.ItemSettingsExtension;
import se.datasektionen.mc.simplecustomfeatures.mixin.AccessorItemSettings;
import se.datasektionen.mc.simplecustomfeatures.objects.BaseObject;

import java.util.Optional;

public interface BaseItem extends BaseObject<Item> {

	private static <T> void addComponent(Item.Settings settings, Component<T> component) {
		settings.component(component.type(), component.value());
	}

	MapCodec<ItemSettings> ITEM_SETTINGS_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ComponentMap.CODEC.fieldOf("components").forGetter(ItemSettings::components),
					Item.ENTRY_CODEC.optionalFieldOf("recipe_remainder").forGetter(ItemSettings::recipeRemainder)
			).apply(instance, ItemSettings::new)
	);

	MapCodec<ItemSettingsWithBaseItem> ITEM_SETTINGS_WITH_BASE_ITEM_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Item.ENTRY_CODEC.fieldOf("base_item").forGetter(ItemSettingsWithBaseItem::baseItem),
					ComponentChanges.CODEC.fieldOf("components").forGetter(ItemSettingsWithBaseItem::components),
					Item.ENTRY_CODEC.optionalFieldOf("recipe_remainder").forGetter(ItemSettingsWithBaseItem::recipeRemainder)
			).apply(instance, ItemSettingsWithBaseItem::new)
	);

	record ItemSettings(ComponentMap components, Optional<RegistryEntry<Item>> recipeRemainder) {
		public DataResult<Item.Settings> makeSettings(RegistryKey<Item> key, Identifier displayModel) {
			return ItemStack.validateComponents(components).map(
					success -> {
						var settings = new Item.Settings();
						recipeRemainder.ifPresent(remainder -> settings.recipeRemainder(remainder.value()));
						components.forEach(component -> addComponent(settings, component));
						var model = components.get(DataComponentTypes.ITEM_MODEL);
						if (model == null && displayModel != null) {
							model = displayModel;
						}
						if (model != null) {
							((AccessorItemSettings) settings).setModelId(RegistryKeyedValue.fixed(model));
						}
						var name = components.get(DataComponentTypes.ITEM_NAME);
						if (name != null) {
							((ItemSettingsExtension) settings).simple_custom_features$setCustomName(name);
						}
						return settings.registryKey(key);
					}
			);
		}
	}

	private static <T> void addComponent(ComponentMap.Builder builder, Component<T> c) {
		builder.add(c.type(), c.value());
	}

	record ItemSettingsWithBaseItem(RegistryEntry<Item> baseItem, ComponentChanges components, Optional<RegistryEntry<Item>> recipeRemainder) {
		public DataResult<Item.Settings> makeSettings(RegistryKey<Item> key, Identifier displayModel) {
			var defaultComponents = ComponentMap.builder();
			for (var c : baseItem.value().getComponents()) {
				if (c.type() == DataComponentTypes.ITEM_MODEL || c.type() == DataComponentTypes.ITEM_NAME) {
					continue;
				}
				addComponent(defaultComponents, c);
			}
			return new ItemSettings(
					MergedComponentMap.create(defaultComponents.build(), components), recipeRemainder
			).makeSettings(key, displayModel == null ? getModel(baseItem) : displayModel);
		}
	}

	static Identifier getModel(Item item) {
		return item.getComponents().get(DataComponentTypes.ITEM_MODEL);
	}

	static Identifier getModel(RegistryEntry<Item> item) {
		return getModel(item.value());
	}

	@Override
	default void onRegistrationFail(Identifier id, Item value) {
		RegistryHelper.removeIntrusiveEntry(Registries.ITEM, value);
	}

	@Override
	default void onUnregister(RegistryEntry<Item> entry) {
		Item.BLOCK_ITEMS.values().remove(entry.value());
	}

	@Override
	default void onRegistrationSuccess(RegistryEntry.Reference<Item> entry) {
		if (entry.value() instanceof BlockItem blockItem) {
			blockItem.appendBlocks(Item.BLOCK_ITEMS, entry.value());
		}
	}

}
