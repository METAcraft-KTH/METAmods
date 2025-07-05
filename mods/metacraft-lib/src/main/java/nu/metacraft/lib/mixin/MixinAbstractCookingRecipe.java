package nu.metacraft.lib.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.AbstractCookingRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import nu.metacraft.lib.extensions.RecipeRemainderExtension;

import java.util.function.UnaryOperator;

@Mixin(AbstractCookingRecipe.class)
public class MixinAbstractCookingRecipe implements RecipeRemainderExtension {

	@Unique
	private UnaryOperator<ItemStack> remainderFunction = stack -> ItemStack.EMPTY;

	@Override
	public UnaryOperator<ItemStack> metacraft_lib$getRemainderFunction() {
		return remainderFunction;
	}

	@Override
	public void metacraft_lib$setRemainderFunction(UnaryOperator<ItemStack> remainderFunction) {
		this.remainderFunction = remainderFunction;
	}
}
