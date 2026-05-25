package nu.metacraft.relay.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import nu.metacraft.relay.items.RelayComponents;
import nu.metacraft.relay.items.RelayItems;
import nu.metacraft.lib.util.helper.RecipeHelper;
import org.jspecify.annotations.NonNull;

public class RelayProgramRecipe extends ShapelessRecipe {

	public RelayProgramRecipe(ShapelessRecipe recipe) {
		super(
				RecipeHelper.getCommonInfo(recipe),
				RecipeHelper.getBookInfo(recipe),
				RecipeHelper.getResult(recipe),
				RecipeHelper.getIngredients(recipe)
		);
	}

	@Override
	public boolean matches(@NonNull CraftingInput craftingRecipeInput, @NonNull Level world) {
		int compass = getCompassSlot(craftingRecipeInput);
		int relay = getRelaySlot(craftingRecipeInput);
		if (compass == -1 || relay == -1) return false;
		var dimensions = craftingRecipeInput.getItem(relay).get(RelayComponents.VALID_DIMENSIONS);
		if (dimensions == null) return false;
		var target = craftingRecipeInput.getItem(compass).get(DataComponents.LODESTONE_TRACKER);
		if (target == null || target.target().isEmpty()) return false;
		var targetDim = target.target().get().dimension();
		for (var d : dimensions.values()) {
			if (d.contains(targetDim)) {
				return true;
			}
		}
		return false;
	}

	private int getCompassSlot(CraftingInput craftingRecipeInput) {
		for (int i = 0; i < craftingRecipeInput.size(); i++) {
			var stack = craftingRecipeInput.getItem(i);
			if (stack.has(DataComponents.LODESTONE_TRACKER) && !stack.is(RelayItems.RELAY)) {
				return i;
			}
		}
		return -1;
	}

	private int getRelaySlot(CraftingInput craftingRecipeInput) {
		for (int i = 0; i < craftingRecipeInput.size(); i++) {
			var stack = craftingRecipeInput.getItem(i);
			if (stack.is(RelayItems.RELAY)) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public @NonNull ItemStack assemble(@NonNull CraftingInput recipeInputInventory) {
		int relay = getRelaySlot(recipeInputInventory);
		int compass = getCompassSlot(recipeInputInventory);
		if (relay == -1 || compass == -1) return ItemStack.EMPTY;
		var relayItem = recipeInputInventory.getItem(relay).copy();
		var compassItem = recipeInputInventory.getItem(compass);
		relayItem.set(DataComponents.LODESTONE_TRACKER, compassItem.get(DataComponents.LODESTONE_TRACKER));
		return relayItem;
	}

	@Override
	public @NonNull NonNullList<ItemStack> getRemainingItems(@NonNull CraftingInput inventory) {
		int compass = getCompassSlot(inventory);
		var remainders = super.getRemainingItems(inventory);
		if (compass != -1) {
			remainders.set(compass, inventory.getItem(compass).copy());
		}
		return remainders;
	}
}
