package nu.metacraft.simplecustomfeatures.objects.items;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import nu.metacraft.simplecustomfeatures.RegistryHelper;
import nu.metacraft.simplecustomfeatures.extension.ItemPropertiesExtension;
import nu.metacraft.simplecustomfeatures.mixin.ItemPropertiesAccessor;
import nu.metacraft.simplecustomfeatures.mixin.ItemStackAccessor;
import nu.metacraft.simplecustomfeatures.objects.BaseObject;

import java.util.Optional;
import java.util.function.Function;

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

		public static void addInitializer(Item.Properties properties, DataComponentInitializers.Initializer<Item> extraInitializer) {
			((ItemPropertiesAccessor) properties).setComponentInitializer(
					((ItemPropertiesAccessor) properties).getComponentInitializer().andThen(extraInitializer)
			);
		}

		public DataResult<Item.Properties> makeSettings(ResourceKey<Item> key, Function<HolderLookup.Provider, Identifier> displayModel) {
			return ItemStackAccessor.callValidateComponents(components).map(
					success -> {
						var settings = new Item.Properties();
						recipeRemainder.ifPresent(remainder -> settings.craftRemainder(remainder.value()));
						addInitializer(settings, (comp, context, k) -> {
							components.forEach(component -> addComponent(comp, component));
							if (displayModel != null) {
								comp.set(DataComponents.ITEM_MODEL, displayModel.apply(context));
							}
						});
						((ItemPropertiesExtension) settings).simple_custom_features$setIsCustom(true);
						settings.setId(key);
						return settings;
					}
			);
		}
	}

	private static <T> void addComponent(DataComponentMap.Builder builder, TypedDataComponent<T> c) {
		builder.set(c.type(), c.value());
	}

	record ItemSettingsWithBaseItem(Holder<Item> baseItem, DataComponentPatch components, Optional<Holder<Item>> recipeRemainder) {

		public DataResult<Item.Properties> makeSettings(ResourceKey<Item> key, Identifier displayModel) {
			var settings = new Item.Properties();
			recipeRemainder.ifPresent(remainder -> settings.craftRemainder(remainder.value()));

			ItemSettings.addInitializer(settings, (comp, context, k) -> {
				var defaultComponents = DataComponentMap.builder();
				var baseItemComponents = RegistryHelper.getComponentsFor(baseItem.unwrapKey().orElseThrow(), baseItem.value(), context);
				for (var c : baseItemComponents) {
					if (c.type() == DataComponents.ITEM_NAME) continue;
					addComponent(defaultComponents, c);
				}
				var allComponents = PatchedDataComponentMap.fromPatch(defaultComponents.build(), components);
				for (var c : allComponents) {
					addComponent(comp, c);
				}
				if (displayModel != null) {
					comp.set(DataComponents.ITEM_MODEL, displayModel);
				}
			});
			((ItemPropertiesExtension) settings).simple_custom_features$setIsCustom(true);
			settings.setId(key);

			return DataResult.success(settings);
		}
	}

	@Override
	default void onRegistrationFail(Identifier id, Item value) {
		RegistryHelper.removeIntrusiveEntry(BuiltInRegistries.ITEM, value);
		RegistryHelper.removeComponentInitializer(value);
	}

	@Override
	default void onUnregister(Holder<Item> entry) {
		Item.BY_BLOCK.values().remove(entry.value());
		RegistryHelper.removeComponentInitializer(entry.value());
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
