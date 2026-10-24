package se.metacraft.moredyes.content.item;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * A happy ghast harness in one of our colours. All behaviour is the vanilla harness's, which lives
 * in its {@code equippable} component; ours names our equipment asset, so a vanilla client draws the
 * harness on the ghast from our pack. In hand the client sees the white harness with our item model.
 */
public final class ModHarnessItem extends Item implements PolymerItem {
	private final Identifier model;

	public ModHarnessItem(Properties properties, Identifier id) {
		super(properties);
		this.model = id;
	}

	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		return Items.HARNESS.white();
	}

	@Override
	public @Nullable Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		return model;
	}
}
