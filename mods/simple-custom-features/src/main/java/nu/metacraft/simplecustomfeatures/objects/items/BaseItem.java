package nu.metacraft.simplecustomfeatures.objects.items;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.DependantName;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import nu.metacraft.simplecustomfeatures.RegistryHelper;
import nu.metacraft.simplecustomfeatures.extension.ItemSettingsExtension;
import nu.metacraft.simplecustomfeatures.mixin.ItemPropertiesAccessor;
import nu.metacraft.simplecustomfeatures.objects.BaseObject;

import java.util.Optional;

public interface BaseItem extends BaseObject<Item> {

	private static <T> void addComponent(Item.Properties settings, TypedDataComponent<T> component) {
		settings.component(component.type(), component.value());
	}

	MapCodec<ItemSettings> ITEM_SETTINGS_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					DataComponentMap.CODEC.fieldOf("components").forGetter(ItemSettings::components),
					Item.CODEC.optionalFieldOf("recipe_remainder").forGetter(ItemSettings::recipeRemainder)
			).apply(instance, ItemSettings::new)
	);

	MapCodec<ItemSettingsWithBaseItem> ITEM_SETTINGS_WITH_BASE_ITEM_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Item.CODEC.fieldOf("base_item").forGetter(ItemSettingsWithBaseItem::baseItem),
					DataComponentPatch.CODEC.fieldOf("components").forGetter(ItemSettingsWithBaseItem::components),
					Item.CODEC.optionalFieldOf("recipe_remainder").forGetter(ItemSettingsWithBaseItem::recipeRemainder)
			).apply(instance, ItemSettingsWithBaseItem::new)
	);

	record ItemSettings(DataComponentMap components, Optional<Holder<Item>> recipeRemainder) {
		public DataResult<Item.Properties> makeSettings(ResourceKey<Item> key, ResourceLocation displayModel) {
			return ItemStack.validateComponents(components).map(
					success -> {
						var settings = new Item.Properties();
						recipeRemainder.ifPresent(remainder -> settings.craftRemainder(remainder.value()));
						components.forEach(component -> addComponent(settings, component));
						var model = components.get(DataComponents.ITEM_MODEL);
						if (model == null && displayModel != null) {
							model = displayModel;
						}
						if (model != null) {
							((ItemPropertiesAccessor) settings).setModel(DependantName.fixed(model));
						}
						var name = components.get(DataComponents.ITEM_NAME);
						if (name != null) {
							((ItemSettingsExtension) settings).simple_custom_features$setCustomName(name);
						}
						return settings.setId(key);
					}
			);
		}
	}

	private static <T> void addComponent(DataComponentMap.Builder builder, TypedDataComponent<T> c) {
		builder.set(c.type(), c.value());
	}

	record ItemSettingsWithBaseItem(Holder<Item> baseItem, DataComponentPatch components, Optional<Holder<Item>> recipeRemainder) {
		public DataResult<Item.Properties> makeSettings(ResourceKey<Item> key, ResourceLocation displayModel) {
			var defaultComponents = DataComponentMap.builder();
			for (var c : baseItem.value().components()) {
				if (c.type() == DataComponents.ITEM_MODEL || c.type() == DataComponents.ITEM_NAME) {
					continue;
				}
				addComponent(defaultComponents, c);
			}
			return new ItemSettings(
					PatchedDataComponentMap.fromPatch(defaultComponents.build(), components), recipeRemainder
			).makeSettings(key, displayModel == null ? getModel(baseItem) : displayModel);
		}
	}

	static ResourceLocation getModel(Item item) {
		return item.components().get(DataComponents.ITEM_MODEL);
	}

	static ResourceLocation getModel(Holder<Item> item) {
		return getModel(item.value());
	}

	@Override
	default void onRegistrationFail(ResourceLocation id, Item value) {
		RegistryHelper.removeIntrusiveEntry(BuiltInRegistries.ITEM, value);
	}

	@Override
	default void onUnregister(Holder<Item> entry) {
		Item.BY_BLOCK.values().remove(entry.value());
	}

	@Override
	default void onRegistrationSuccess(Holder.Reference<Item> entry) {
		if (entry.value() instanceof BlockItem blockItem) {
			if (!Item.BY_BLOCK.containsKey(blockItem.getBlock())) {
				blockItem.registerBlocks(Item.BY_BLOCK, entry.value());
			}
		}
	}

}
