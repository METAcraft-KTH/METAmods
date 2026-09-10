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
    private final Identifier id, flatId;

    public PatchItem(Properties properties, Patches.Patch patch, Identifier id) {
        super(properties);
        this.patch = patch;
        this.id = id;
        this.flatId = id.withSuffix(FLAT_SUFFIX);
    }

    /** The second model of every patch: its art 1:1 on the 16×16 sprite, for display entities ({@link ModComponents#FLAT}). */
    public static final String FLAT_SUFFIX = "_flat";

    @Override
    public void modifyClientTooltip(List<Component> tooltip, ItemStack stack, PacketContext context) {
        tooltip.add(Component.literal(patch.seat() ? "Goes across the seat" : "Goes anywhere on an ovve").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Aim it at an armour stand wearing an ovve, right-click to sew").withStyle(ChatFormatting.DARK_GRAY));
        if (patch.seat()) tooltip.add(Component.literal("Or: smithing table, ovve + patch").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public Item getPolymerItem(ItemStack stack, PacketContext context) {
        return Items.PAPER;
    }

    @Override
    public Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
        return Boolean.TRUE.equals(stack.get(ModComponents.FLAT)) ? flatId : id;
    }
}
