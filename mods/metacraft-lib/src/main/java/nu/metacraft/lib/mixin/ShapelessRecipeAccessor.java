package nu.metacraft.lib.mixin;

import net.minecraft.world.item.ItemStackTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;

@Mixin(ShapelessRecipe.class)
public interface ShapelessRecipeAccessor {

	@Accessor
	List<Ingredient> getIngredients();

	@Accessor
	ItemStackTemplate getResult();

}
