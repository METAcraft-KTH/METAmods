package se.datasektionen.mc.metacraft_lib.extensions;

import net.minecraft.item.ItemStack;

import java.util.function.UnaryOperator;

public interface RecipeRemainderExtension {

	UnaryOperator<ItemStack> metacraft_lib$getRemainderFunction();

	void metacraft_lib$setRemainderFunction(UnaryOperator<ItemStack> remainderFunction);

}
