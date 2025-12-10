package nu.metacraft.portable_jukebox.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.ticks.ContainerSingleItem;
import nu.metacraft.lib.util.EntityRef;
import nu.metacraft.portable_jukebox.item.PortableJukeboxItem;
import nu.metacraft.portable_jukebox.item.components.Components;

public class PortableJukeboxBlockEntity extends BlockEntity implements ContainerSingleItem.BlockContainerSingleItem {

	private static final String JUKEBOX = "Jukebox"; //Careful, this is used by a datafixer!

	private ItemStack jukebox = ItemStack.EMPTY;

	public PortableJukeboxBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public PortableJukeboxBlockEntity(BlockPos pos, BlockState state) {
		super(Blocks.BlockEntities.PORTABLE_JUKEBOX, pos, state);
	}

	public void setJukebox(ItemStack jukebox) {
		this.jukebox = jukebox;
	}

	public ItemStack getJukebox() {
		return jukebox;
	}

	@Override
	public void loadAdditional(ValueInput nbt) {
		super.loadAdditional(nbt);
		jukebox = nbt.read(JUKEBOX, ItemStack.CODEC).orElse(ItemStack.EMPTY);
	}

	@Override
	public void saveAdditional(ValueOutput nbt) {
		super.saveAdditional(nbt);
		if (!jukebox.isEmpty()) {
			nbt.store(JUKEBOX, ItemStack.CODEC, jukebox);
		}
	}

	@Override
	public BlockEntity getContainerBlockEntity() {
		return this;
	}

	@Override
	public ItemStack getTheItem() {
		return jukebox.getOrDefault(Components.PORTABLE_JUKEBOX, ItemStack.EMPTY);
	}

	@Override
	public ItemStack splitTheItem(int count) {
		var stack = getTheItem();
		if (count < 1) return ItemStack.EMPTY;
		setTheItem(ItemStack.EMPTY); //It will never be more than a count of 1 in this thing anyway.
		return stack;
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState oldState) {
		//Prevent the disc from being ejected when broken (the default behaviour of this function is to drop all items in the inventory).
	}

	@Override
	public void setTheItem(ItemStack stack) {
		var prev = stack.isEmpty() ? this.jukebox.remove(Components.PORTABLE_JUKEBOX) : this.jukebox.set(Components.PORTABLE_JUKEBOX, stack);
		PortableJukeboxItem.updateStackChange(EntityRef.fromBlock(this), prev, jukebox);
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return stack.has(DataComponents.JUKEBOX_PLAYABLE) && this.getItem(slot).isEmpty();
	}

	@Override
	public boolean canTakeItem(Container hopperInventory, int slot, ItemStack stack) {
		return hopperInventory.hasAnyMatching(ItemStack::isEmpty);
	}
}
