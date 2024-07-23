package se.datasektionen.mc.saved_items.loot_container;

import net.minecraft.predicate.item.ItemPredicate;
import net.minecraft.registry.Registry;
import se.datasektionen.mc.loot_containers.containers.LootContainer;
import se.datasektionen.mc.loot_containers.containers.LootContainerRegistry;
import se.datasektionen.mc.loot_containers.containers.LootContainerType;
import se.datasektionen.mc.saved_items.SavedItems;
import se.datasektionen.mc.saved_items.item_saving.SavedItemsData;

public class Containers {

	public static final LootContainerType<SavedItemsContainer> SAVED_ITEMS = register(
			"saved_items", new LootContainerType<>(SavedItemsContainer.CODEC, new SavedItemsContainer(
					SavedItemsData.ANY, ItemPredicate.Builder.create().build()
			))
	);

	public static void init() {

	}

	private static <T extends LootContainer> LootContainerType<T> register(String key, LootContainerType<T> object) {
		return Registry.register(LootContainerRegistry.REGISTRY, SavedItems.getID(key), object);
	}

}
