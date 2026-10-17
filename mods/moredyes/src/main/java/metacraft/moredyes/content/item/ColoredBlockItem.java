package metacraft.moredyes.content.item;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * BlockItem for a coloured block. The client is told it holds a vanilla equivalent (so its placement
 * prediction, shape and sound, is right: the white one, or for stairs and slabs the one in the nearest
 * colour, which is what the server then shows) with an item-model override pointing at our pack asset
 * {@code moredyes:items/<color>_<family>.json}, whose id equals this item's registry id.
 */
public final class ColoredBlockItem extends BlockItem implements PolymerItem {
	private final Item clientItem;
	private final Identifier model;

	public ColoredBlockItem(Block block, Properties properties, Identifier id, Item clientItem) {
		super(block, properties);
		this.clientItem = clientItem;
		this.model = id;
	}

	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		return clientItem;
	}

	@Override
	public @Nullable Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		return model;
	}
}
