package nu.metacraft.portable_jukebox.item;

import eu.pb4.polymer.core.api.block.PolymerHeadBlock;
import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.block.Block;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import nu.metacraft.lib.util.helper.GameProfileHelper;
import xyz.nucleoid.packettweaker.PacketContext;

public class FixedPolymerHeadBlockItem extends BlockItem implements PolymerItem {
	private final PolymerHeadBlock polymerBlock;

	public <T extends Block & PolymerHeadBlock> FixedPolymerHeadBlockItem(T block, Settings settings) {
		super(block, settings);
		this.polymerBlock = block;
	}

	@Override
	public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
		return Items.PLAYER_HEAD;
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context) {
		return null;
	}

	public ItemStack getPolymerItemStack(ItemStack itemStack, TooltipType tooltipType, PacketContext context) {
		ItemStack out = PolymerItem.super.getPolymerItemStack(itemStack, tooltipType, context);

		out.set(
				DataComponentTypes.PROFILE,
				GameProfileHelper.staticComponentBuilder().withServersideSkin(
						this.polymerBlock.getPolymerSkinValue(this.getBlock().getDefaultState(), BlockPos.ORIGIN, context),
						this.polymerBlock.getPolymerSkinSignature(this.getBlock().getDefaultState(), BlockPos.ORIGIN, context)
				).build()
		);
		return out;
	}
}
