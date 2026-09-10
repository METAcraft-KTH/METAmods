package metacraft.ovvar.content;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The ovve's trouser cuffs: a companion stack the mod places in the feet slot while an ovve is
 * worn and nothing else is there, so the boots render pass — which draws the whole legs — can
 * carry three more patches in its dye colour ({@link OvveFeet}). Like the top it is never a real
 * possession: outside that slot it deletes itself, it can't be dropped, and deaths clear it first.
 * Real boots go on over it as usual and are wrapped instead.
 */
public final class OvveFeetItem extends Item implements PolymerItem {
    public final Chapter chapter;
    private final Identifier id;

    public OvveFeetItem(Properties properties, Chapter chapter, Identifier id) {
        super(properties);
        this.chapter = chapter;
        this.id = id;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (slot != EquipmentSlot.FEET || !(entity instanceof LivingEntity wearer) || !(wearer.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof OvveItem)) {
            stack.setCount(0);
        }
    }

    @Override
    public void modifyClientTooltip(List<Component> tooltip, ItemStack stack, PacketContext context) {
        tooltip.add(Component.literal("The cuffs of your " + chapter.garmentWord()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Boots go on over them").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public Item getPolymerItem(ItemStack stack, PacketContext context) {
        return Items.LEATHER_BOOTS;
    }

    @Override
    public Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
        return id;
    }

    @Override
    public ItemStack getPolymerItemStack(ItemStack stack, TooltipFlag flag, PacketContext context, HolderLookup.Provider lookup) {
        ItemStack out = PolymerItem.super.getPolymerItemStack(stack, flag, context, lookup);
        OvveFeet.dress(out, stack, null, context);
        return out;
    }
}
