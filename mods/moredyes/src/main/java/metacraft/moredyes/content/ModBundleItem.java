package metacraft.moredyes.content;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * A bundle in one of our colours. All behaviour (contents, selection scroll, GUI hover models) is
 * the vanilla bundle's, keyed on the {@code BUNDLE_CONTENTS} component; the client is told it holds
 * the white bundle with our item definition (a copy of vanilla's, pointing at our textures).
 */
public final class ModBundleItem extends BundleItem implements PolymerItem {
	private final Identifier model;

	public ModBundleItem(Properties properties, Identifier id) {
		super(properties);
		this.model = id;
	}

	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		return Items.DYED_BUNDLE.white();
	}

	@Override
	public @Nullable Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		return model;
	}
}
