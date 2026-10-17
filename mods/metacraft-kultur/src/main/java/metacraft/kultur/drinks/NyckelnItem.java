package metacraft.kultur.drinks;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Nyckeln Patch Drink, the sober one: apple and a water bottle in a crafting grid, as PolymITer had
 * it (but a water bottle only; PolymITer's took any potion). A drink with no alcohol; a vanilla client
 * is shown a potion wearing PolymITer's bottle.
 */
public final class NyckelnItem extends Item implements PolymerItem {
	public NyckelnItem(Properties properties) {
		super(properties);
	}

	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		return Items.POTION;
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		return DrinkKind.NYCKELN.model();
	}
}
