package nu.metacraft.nether_rifts.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeInput;
import nu.metacraft.nether_rifts.EyeLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The crafting table and the inventory's 2×2 grid both fill their result slot here. */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {

	@WrapOperation(
			method = "slotChangedCraftingGrid",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/CraftingRecipe;assemble(Lnet/minecraft/world/item/crafting/RecipeInput;)Lnet/minecraft/world/item/ItemStack;")
	)
	private static ItemStack netherRifts$lockEyes(
			CraftingRecipe recipe, RecipeInput input, Operation<ItemStack> original, @Local(argsOnly = true) ServerLevel level
	) {
		return EyeLock.filter(original.call(recipe, input), level);
	}
}
