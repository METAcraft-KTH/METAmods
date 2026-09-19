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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The ovve's trouser cuffs: a virtual stack that exists only in the equipment packets other
 * players get for an ovve wearer with an empty feet slot, so the boots render pass — which
 * draws the whole legs — can carry three more patches in its dye colour ({@link OvveFeet}). It
 * is never in an inventory; should one ever get there it deletes itself.
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
		stack.setCount(0);
	}

	@Override
	public void modifyClientTooltip(List<Component> tooltip, ItemStack stack, PacketContext context) {
		tooltip.add(Component.literal("The cuffs of a " + chapter.garmentWord()).withStyle(ChatFormatting.GRAY));
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
