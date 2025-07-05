package nu.metacraft.relay.recipe;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.*;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;
import nu.metacraft.relay.items.RelayComponents;
import nu.metacraft.relay.items.RelayItems;
import nu.metacraft.lib.util.helper.RecipeHelper;

public class RelayProgramRecipe extends ShapelessRecipe {

	public RelayProgramRecipe(ShapelessRecipe recipe) {
		super(
				recipe.getGroup(), recipe.getCategory(),
				recipe.craft(null, null),
				RecipeHelper.getIngredients(recipe)
		);
	}

	@Override
	public boolean matches(CraftingRecipeInput craftingRecipeInput, World world) {
		int compass = getCompassSlot(craftingRecipeInput);
		int relay = getRelaySlot(craftingRecipeInput);
		if (compass == -1 || relay == -1) return false;
		var dimensions = craftingRecipeInput.getStackInSlot(relay).get(RelayComponents.VALID_DIMENSIONS);
		if (dimensions == null) return false;
		var target = craftingRecipeInput.getStackInSlot(compass).get(DataComponentTypes.LODESTONE_TRACKER);
		if (target == null || target.target().isEmpty()) return false;
		var targetDim = target.target().get().dimension();
		for (var d : dimensions.values()) {
			if (d.contains(targetDim)) {
				return true;
			}
		}
		return false;
	}

	private int getCompassSlot(CraftingRecipeInput craftingRecipeInput) {
		for (int i = 0; i < craftingRecipeInput.size(); i++) {
			var stack = craftingRecipeInput.getStackInSlot(i);
			if (stack.contains(DataComponentTypes.LODESTONE_TRACKER) && !stack.isOf(RelayItems.RELAY)) {
				return i;
			}
		}
		return -1;
	}

	private int getRelaySlot(CraftingRecipeInput craftingRecipeInput) {
		for (int i = 0; i < craftingRecipeInput.size(); i++) {
			var stack = craftingRecipeInput.getStackInSlot(i);
			if (stack.isOf(RelayItems.RELAY)) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public ItemStack craft(CraftingRecipeInput recipeInputInventory, RegistryWrapper.WrapperLookup lookup) {
		int relay = getRelaySlot(recipeInputInventory);
		int compass = getCompassSlot(recipeInputInventory);
		if (relay == -1 || compass == -1) return ItemStack.EMPTY;
		var relayItem = recipeInputInventory.getStackInSlot(relay).copy();
		var compassItem = recipeInputInventory.getStackInSlot(compass);
		relayItem.set(DataComponentTypes.LODESTONE_TRACKER, compassItem.get(DataComponentTypes.LODESTONE_TRACKER));
		return relayItem;
	}

	@Override
	public DefaultedList<ItemStack> getRecipeRemainders(CraftingRecipeInput inventory) {
		int compass = getCompassSlot(inventory);
		var remainders = super.getRecipeRemainders(inventory);
		if (compass != -1) {
			remainders.set(compass, inventory.getStackInSlot(compass).copy());
		}
		return remainders;
	}
}
