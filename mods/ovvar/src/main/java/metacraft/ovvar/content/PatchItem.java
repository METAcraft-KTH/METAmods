package metacraft.ovvar.content;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

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
    public void modifyClientTooltip(List<Component> tooltip, ItemStack stack, PacketContext context) {
        tooltip.add(Component.literal("Goes on the " + patch.spot().label()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Smithing table: ovve + this patch").withStyle(ChatFormatting.DARK_GRAY));
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
