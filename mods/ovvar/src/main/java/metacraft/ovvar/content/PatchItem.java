package metacraft.ovvar.content;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A patch in the hand: a stackable item whose icon is its sewn-on art, shown to clients as paper. */
public final class PatchItem extends Item implements PolymerItem {
    public final Patches.Patch patch;
    private final Identifier id;

    public PatchItem(Properties properties, Patches.Patch patch, Identifier id) {
        super(properties);
        this.patch = patch;
        this.id = id;
    }

    @Override
    public Item getPolymerItem(ItemStack stack, PacketContext context) {
        return Items.PAPER;
    }

    @Override
    public Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
        return id;
    }
}
