package nu.metacraft.lib.mixin;

import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.NormalCraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NormalCraftingRecipe.class)
public interface NormalCraftingRecipeAccessor {

	@Accessor
	Recipe.CommonInfo getCommonInfo();

	@Accessor
	CraftingRecipe.CraftingBookInfo getBookInfo();

}
