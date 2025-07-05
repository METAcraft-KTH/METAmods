package nu.metacraft.core.item;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class ItemTransmutation {

	private static final Map<Item, Item> toTransmute = new HashMap<>();

	public static void addTransmutation(Item from, Item to) {
		toTransmute.put(from, to);
	}

	public static ItemStack transmute(ItemStack stack) {
		if (!toTransmute.containsKey(stack.getItem())) return stack;
		var newStack = new ItemStack(
				toTransmute.get(stack.getItem()).getRegistryEntry(), stack.getCount()
		);
		newStack.applyComponentsFrom(stack.getComponents());
		newStack.set(DataComponentTypes.ITEM_NAME, stack.getName());
		encodeCustomModelData(stack, newStack);
		if (stack.hasGlint() && stack.getEnchantments().isEmpty() && !newStack.hasGlint()) {
			newStack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return newStack;
	}

	protected static void encodeCustomModelData(ItemStack oldStack, ItemStack newStack) {
		if (oldStack.getItem() instanceof PolymerItem polymerItem) {
			var modelData = polymerItem.getPolymerItemModel(oldStack, null);
			if (modelData != null && !oldStack.contains(DataComponentTypes.ITEM_MODEL)) {
				newStack.set(DataComponentTypes.ITEM_MODEL, modelData);
			}
		}
	}

}
