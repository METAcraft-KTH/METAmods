package se.datasektionen.mc.metacraft_lib.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.AbstractCookingRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import se.datasektionen.mc.metacraft_lib.extensions.RecipeRemainderExtension;

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
