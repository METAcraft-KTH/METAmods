package metacraft.kultur.content;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A banner pattern item the loom asks for before it offers the pattern — vanilla's globe and
 * flower mechanic, for a chapter pattern that is meant to be found rather than free. A vanilla
 * client is shown the flower pattern item wearing our model; the component that matters,
 * {@code provides_banner_patterns}, is on the item's default components and needs no disguise,
 * since the loom reads it on the server.
 */
public final class PatternItem extends Item implements PolymerItem {
	private final Identifier model;

	public PatternItem(Properties properties, Identifier model) {
		super(properties);
		this.model = model;
	}

	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		return Items.FLOWER_BANNER_PATTERN;
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		return model;
	}
}
