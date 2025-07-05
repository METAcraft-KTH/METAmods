package nu.metacraft.saved_items.loot_container;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.predicate.item.ItemPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.loot_containers.containers.LootContainer;
import nu.metacraft.loot_containers.containers.LootContainerType;
import nu.metacraft.saved_items.SavedItemsConfig;
import nu.metacraft.saved_items.item_saving.SavedItemsData;

public class SavedItemsContainer extends LootContainer {

	public static final MapCodec<SavedItemsContainer> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					SavedItemsConfig.SavingType.CODEC.fieldOf("savingType").forGetter(c -> c.savingType),
					ItemPredicate.CODEC.fieldOf("validItems").forGetter(c -> c.validItems)
			).apply(instance, SavedItemsContainer::new)
	);

	private final SavedItemsConfig.SavingType savingType;
	private final ItemPredicate validItems;

	public SavedItemsContainer(
			SavedItemsConfig.SavingType savingType, ItemPredicate validItems
	) {
		this.savingType = savingType;
		this.validItems = validItems;
	}

	@Override
	public void onOpen(@Nullable ServerPlayerEntity player) {
		access.get().ifPresent(access -> {
			SavedItemsData.getInstance(world.getServer()).extractItems(
				savingType, ConstantIntProvider.create(Integer.MAX_VALUE),
				ConstantIntProvider.create(Integer.MAX_VALUE), validItems, stack -> {
					return access.insertStack(stack, world.getRandom()).getCount();
				}
			);
		});
	}

	@Override
	public LootContainerType<? extends LootContainer> getType() {
		return Containers.SAVED_ITEMS;
	}
}
