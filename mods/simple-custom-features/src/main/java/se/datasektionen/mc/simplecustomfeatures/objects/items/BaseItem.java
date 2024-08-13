package se.datasektionen.mc.simplecustomfeatures.objects.items;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.component.Component;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.ComponentMapImpl;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import se.datasektionen.mc.simplecustomfeatures.RegistryHelper;
import se.datasektionen.mc.simplecustomfeatures.objects.BaseObject;

import java.util.Optional;

public interface BaseItem extends BaseObject<Item> {

	private static <T> void addComponent(Item.Settings settings, Component<T> component) {
		settings.component(component.type(), component.value());
	}

	MapCodec<ItemSettings> ITEM_SETTINGS_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ComponentMap.CODEC.fieldOf("components").forGetter(ItemSettings::components),
					ItemStack.ITEM_CODEC.optionalFieldOf("recipe_remainder").forGetter(ItemSettings::recipeRemainder)
			).apply(instance, ItemSettings::new)
	);

	MapCodec<ItemSettingsWithBaseItem> ITEM_SETTINGS_WITH_BASE_ITEM_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ItemStack.ITEM_CODEC.fieldOf("base_item").forGetter(ItemSettingsWithBaseItem::baseItem),
					ComponentChanges.CODEC.fieldOf("components").forGetter(ItemSettingsWithBaseItem::components),
					ItemStack.ITEM_CODEC.optionalFieldOf("recipe_remainder").forGetter(ItemSettingsWithBaseItem::recipeRemainder)
			).apply(instance, ItemSettingsWithBaseItem::new)
	);

	record ItemSettings(ComponentMap components, Optional<RegistryEntry<Item>> recipeRemainder) {
		public DataResult<Item.Settings> makeSettings() {
			return ItemStack.validateComponents(components).map(
					success -> {
						var settings = new Item.Settings();
						recipeRemainder.ifPresent(remainder -> settings.recipeRemainder(remainder.value()));
						components.forEach(component -> addComponent(settings, component));
						return settings;
					}
			);
		}
	}
	record ItemSettingsWithBaseItem(RegistryEntry<Item> baseItem, ComponentChanges components, Optional<RegistryEntry<Item>> recipeRemainder) {
		public DataResult<Item.Settings> makeSettings() {
			return new ItemSettings(
					ComponentMapImpl.create(baseItem.value().getComponents(), components), recipeRemainder
			).makeSettings();
		}
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
