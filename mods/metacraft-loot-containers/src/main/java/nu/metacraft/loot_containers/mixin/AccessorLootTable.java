package nu.metacraft.loot_containers.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;

@Mixin(LootTable.class)
public interface AccessorLootTable {

	@Invoker
	void callShuffleAndSplitItems(ObjectArrayList<ItemStack> drops, int freeSlots, RandomSource random);

	@Invoker
	List<Integer> callGetAvailableSlots(Container inventory, RandomSource random);

}
