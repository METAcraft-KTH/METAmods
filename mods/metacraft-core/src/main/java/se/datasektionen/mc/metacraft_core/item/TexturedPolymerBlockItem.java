package se.datasektionen.mc.metacraft_core.item;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link BlockItem} that is rendered using custom model data.
 */
public class TexturedPolymerBlockItem extends BlockItem implements PolymerItem {
	private final Item polymerItem;
	private final int customModelData;

	public TexturedPolymerBlockItem(Block block, Settings settings, Item polymerItem, int customModelData) {
		super(block, settings);
		this.polymerItem = polymerItem;
		this.customModelData = customModelData;
	}

	@Override
	public Item getPolymerItem(ItemStack itemStack, @Nullable ServerPlayerEntity player) {
		return this.polymerItem;
	}

	@Override
	public int getPolymerCustomModelData(ItemStack itemStack, @Nullable ServerPlayerEntity player) {
		return this.customModelData;
	}
}
