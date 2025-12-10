package nu.metacraft.portable_jukebox.item;

import eu.pb4.polymer.core.api.block.PolymerHeadBlock;
import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import nu.metacraft.lib.util.helper.GameProfileHelper;
import xyz.nucleoid.packettweaker.PacketContext;

public class FixedPolymerHeadBlockItem extends BlockItem implements PolymerItem {
	private final PolymerHeadBlock polymerBlock;

	public <T extends Block & PolymerHeadBlock> FixedPolymerHeadBlockItem(T block, Properties settings) {
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

	public ItemStack getPolymerItemStack(ItemStack itemStack, TooltipFlag tooltipType, PacketContext context) {
		ItemStack out = PolymerItem.super.getPolymerItemStack(itemStack, tooltipType, context);

		out.set(
				DataComponents.PROFILE,
				GameProfileHelper.staticComponentBuilder().withServersideSkin(
						this.polymerBlock.getPolymerSkinValue(this.getBlock().defaultBlockState(), BlockPos.ZERO, context),
						this.polymerBlock.getPolymerSkinSignature(this.getBlock().defaultBlockState(), BlockPos.ZERO, context)
				).build()
		);
		return out;
	}
}
