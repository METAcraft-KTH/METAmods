package nu.metacraft.core.item;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ItemTransmutation {

	private static final Map<Item, Item> toTransmute = new HashMap<>();

	public static void addTransmutation(Item from, Item to) {
		toTransmute.put(from, to);
	}

	public static ItemStack transmute(ItemStack stack) {
		if (!toTransmute.containsKey(stack.getItem())) return stack;
		var newStack = new ItemStack(
				toTransmute.get(stack.getItem()).builtInRegistryHolder(), stack.getCount()
		);
		newStack.applyComponents(stack.getComponents());
		newStack.set(DataComponents.ITEM_NAME, stack.getHoverName());
		if (stack.hasFoil() && stack.getEnchantments().isEmpty() && !newStack.hasFoil()) {
			newStack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return newStack;
	}

}
