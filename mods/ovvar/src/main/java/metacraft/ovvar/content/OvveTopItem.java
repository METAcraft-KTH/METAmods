package metacraft.ovvar.content;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The rolled-up top of an ovve: a companion stack the mod places in the chest slot while the
 * ovve in the legs slot has its top up and nothing else is worn there. It is never a real
 * possession — outside that slot it deletes itself, it can't be dropped, and deaths clear it first.
 * Real chest armour goes on over it as usual (right-click or swap in the inventory; the top yields).
 */
public final class OvveTopItem extends Item implements PolymerItem {
    public final Chapter chapter;
    private final Identifier id;

    public OvveTopItem(Properties properties, Chapter chapter, Identifier id) {
        super(properties);
        this.chapter = chapter;
        this.id = id;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (slot != EquipmentSlot.CHEST || !(entity instanceof LivingEntity wearer) || !OvveTop.wantsTop(wearer.getItemBySlot(EquipmentSlot.LEGS))) {
            stack.setCount(0);
        }
    }

    @Override
    public void modifyClientTooltip(List<Component> tooltip, ItemStack stack, PacketContext context) {
        tooltip.add(Component.literal("The zipped-up top of your " + chapter.garmentWord()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Sneak + right-click the " + chapter.garmentWord() + " to zip it down").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public Item getPolymerItem(ItemStack stack, PacketContext context) {
        return Items.LEATHER_CHESTPLATE;
    }

    @Override
    public Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
        return id;
    }

    @Override
    public ItemStack getPolymerItemStack(ItemStack stack, TooltipFlag flag, PacketContext context, HolderLookup.Provider lookup) {
        ItemStack out = PolymerItem.super.getPolymerItemStack(stack, flag, context, lookup);
        out.set(DataComponents.EQUIPPABLE, OvveTop.equippable(stack.get(DataComponents.EQUIPPABLE),
                Looks.asset(chapter, Piece.TOP, false, Looks.key(Piece.TOP, Looks.patches(stack)))));
        return out;
    }
}
