package se.datasektionen.mc.loot_containers.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(LootTable.class)
public interface AccessorLootTable {

	@Invoker
	void callSpreadStacks(ObjectArrayList<ItemStack> drops, int freeSlots, Random random);

	@Invoker
	List<Integer> callGetFreeSlots(Inventory inventory, Random random);

}
