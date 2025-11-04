package nu.metacraft.lib.extensions;

import java.util.function.UnaryOperator;
import net.minecraft.world.item.ItemStack;

public interface RecipeRemainderExtension {

	UnaryOperator<ItemStack> metacraft_lib$getRemainderFunction();

	void metacraft_lib$setRemainderFunction(UnaryOperator<ItemStack> remainderFunction);

}
