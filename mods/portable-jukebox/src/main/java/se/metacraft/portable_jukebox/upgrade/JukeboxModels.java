package se.metacraft.portable_jukebox.upgrade;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import se.metacraft.portable_jukebox.PortableJukebox;

/**
 * The upgraded jukeboxes' looks. Tier 0 stays the jukebox player head; tiers 1 to 3 have their own
 * model ({@code assets/portable_jukebox/models/block/jukebox_tier_<n>.json}), served in the resource
 * pack. Held, the item wears the model; placed, a display entity shows it over an invisible block.
 */
public final class JukeboxModels {

	private JukeboxModels() {}

	public static boolean hasModel(int tier) {
		return tier > 0 && tier <= JukeboxUpgrades.MAX_TIER;
	}

	/** The item model ({@code assets/portable_jukebox/items/jukebox_tier_<n>.json}) for this tier. */
	public static Identifier itemModel(int tier) {
		return PortableJukebox.getID("jukebox_tier_" + tier);
	}

	/** What the display entity on a placed jukebox of this tier holds. */
	public static ItemStack displayStack(int tier) {
		return new ItemStack(
			Items.PAPER.builtInRegistryHolder(), 1,
			DataComponentPatch.builder().set(DataComponents.ITEM_MODEL, itemModel(tier)).build()
		);
	}

	/** Shows a placed jukebox's model, centred in its block, at the model's own size. */
	public static ElementHolder blockHolder(int tier) {
		var holder = new ElementHolder();
		var display = new ItemDisplayElement(displayStack(tier));
		display.setItemDisplayContext(ItemDisplayContext.NONE);
		holder.addElement(display);
		return holder;
	}
}
