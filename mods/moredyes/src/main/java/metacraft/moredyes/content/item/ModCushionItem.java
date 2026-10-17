package metacraft.moredyes.content.item;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CushionItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** A cushion in one of our colours: vanilla's, carrying our colour to the cushion it places. */
public final class ModCushionItem extends CushionItem implements PolymerItem {
	private final Identifier model;

	public ModCushionItem(Properties properties, Identifier id) {
		super(properties);
		this.model = id;
	}

	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		return Items.CUSHION.white();
	}

	@Override
	public @Nullable Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		return model;
	}
}
