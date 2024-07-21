package se.datasektionen.mc.metacraft_core.item;

import com.google.common.collect.ImmutableMap;
import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

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
			var modelData = polymerItem.getPolymerCustomModelData(oldStack, null);
			if (modelData != -1 && !oldStack.contains(DataComponentTypes.CUSTOM_MODEL_DATA)) {
				newStack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(modelData));
			}
		}
	}

}
