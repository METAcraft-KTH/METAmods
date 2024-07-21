package se.datasektionen.mc.metacraft_lib.extensions;

import net.minecraft.item.ItemStack;

import java.util.function.Predicate;

public interface RecipeComponentCarryoverExtension {

	void metacraft_lib$setComponentCarryOver(Predicate<ItemStack> checker);

	Predicate<ItemStack> metacraft_lib$getComponentCarryOver();

	default void metacraft_lib$onCraft(ItemStack resultStack, Iterable<ItemStack> allInputs) {
		if (metacraft_lib$getComponentCarryOver() != null) {
			for (var input : allInputs) {
				if (metacraft_lib$getComponentCarryOver().test(input) && !input.getComponentChanges().isEmpty()) {
					//Make sure the components specified in the recipe have higher priority than the ones copied.
					var temp = input.copy();
					temp.applyChanges(resultStack.getComponentChanges());
					resultStack.applyChanges(temp.getComponentChanges());
				}
			}
		}
	}

}
