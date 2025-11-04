package nu.metacraft.core.item;

import eu.pb4.polymer.core.api.item.PolymerItem;
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
		encodeCustomModelData(stack, newStack);
		if (stack.hasFoil() && stack.getEnchantments().isEmpty() && !newStack.hasFoil()) {
			newStack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return newStack;
	}

	protected static void encodeCustomModelData(ItemStack oldStack, ItemStack newStack) {
		if (oldStack.getItem() instanceof PolymerItem polymerItem) {
			var modelData = polymerItem.getPolymerItemModel(oldStack, null);
			if (modelData != null && !oldStack.has(DataComponents.ITEM_MODEL)) {
				newStack.set(DataComponents.ITEM_MODEL, modelData);
			}
		}
	}

}
