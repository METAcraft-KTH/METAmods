package nu.metacraft.saved_items.loot_container;

import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.core.Registry;
import nu.metacraft.loot_containers.containers.LootContainer;
import nu.metacraft.loot_containers.containers.LootContainerRegistry;
import nu.metacraft.loot_containers.containers.LootContainerType;
import nu.metacraft.saved_items.SavedItems;
import nu.metacraft.saved_items.item_saving.SavedItemsData;

public class Containers {

	public static final LootContainerType<SavedItemsContainer> SAVED_ITEMS = register(
			"saved_items", new LootContainerType<>(SavedItemsContainer.CODEC, new SavedItemsContainer(
					SavedItemsData.ANY, ItemPredicate.Builder.item().build()
			))
	);

	public static void init() {

	}

	private static <T extends LootContainer> LootContainerType<T> register(String key, LootContainerType<T> object) {
		return Registry.register(LootContainerRegistry.REGISTRY, SavedItems.getID(key), object);
	}

}
