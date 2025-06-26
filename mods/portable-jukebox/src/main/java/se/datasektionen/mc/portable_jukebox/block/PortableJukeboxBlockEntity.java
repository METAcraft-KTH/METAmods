package se.datasektionen.mc.portable_jukebox.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SingleStackInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import se.datasektionen.mc.metacraft_lib.util.EntityRef;
import se.datasektionen.mc.portable_jukebox.item.PortableJukeboxItem;
import se.datasektionen.mc.portable_jukebox.item.components.Components;

public class PortableJukeboxBlockEntity extends BlockEntity implements SingleStackInventory.SingleStackBlockEntityInventory {

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
	public void readData(ReadView nbt) {
		super.readData(nbt);
		jukebox = nbt.read(JUKEBOX, ItemStack.CODEC).orElse(ItemStack.EMPTY);
	}

	@Override
	public void writeData(WriteView nbt) {
		super.writeData(nbt);
		if (!jukebox.isEmpty()) {
			nbt.put(JUKEBOX, ItemStack.CODEC, jukebox);
		}
	}

	@Override
	public BlockEntity asBlockEntity() {
		return this;
	}

	@Override
	public ItemStack getStack() {
		return jukebox.getOrDefault(Components.PORTABLE_JUKEBOX, ItemStack.EMPTY);
	}

	@Override
	public ItemStack decreaseStack(int count) {
		var stack = getStack();
		if (count < 1) return ItemStack.EMPTY;
		setStack(ItemStack.EMPTY); //It will never be more than a count of 1 in this thing anyway.
		return stack;
	}

	@Override
	public void onBlockReplaced(BlockPos pos, BlockState oldState) {
		//Prevent the disc from being ejected when broken (the default behaviour of this function is to drop all items in the inventory).
	}

	@Override
	public void setStack(ItemStack stack) {
		var prev = stack.isEmpty() ? this.jukebox.remove(Components.PORTABLE_JUKEBOX) : this.jukebox.set(Components.PORTABLE_JUKEBOX, stack);
		PortableJukeboxItem.updateStackChange(EntityRef.fromBlock(this), prev, jukebox);
	}

	@Override
	public int getMaxCountPerStack() {
		return 1;
	}

	@Override
	public boolean isValid(int slot, ItemStack stack) {
		return stack.contains(DataComponentTypes.JUKEBOX_PLAYABLE) && this.getStack(slot).isEmpty();
	}

	@Override
	public boolean canTransferTo(Inventory hopperInventory, int slot, ItemStack stack) {
		return hopperInventory.containsAny(ItemStack::isEmpty);
	}
}
