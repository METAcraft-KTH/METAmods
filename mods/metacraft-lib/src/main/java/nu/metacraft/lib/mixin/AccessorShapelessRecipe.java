package nu.metacraft.lib.mixin;

import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.ShapelessRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ShapelessRecipe.class)
public interface AccessorShapelessRecipe {

	@Accessor
	List<Ingredient> getIngredients();

}
