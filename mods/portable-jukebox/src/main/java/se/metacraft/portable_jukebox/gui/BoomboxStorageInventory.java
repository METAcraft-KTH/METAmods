package se.metacraft.portable_jukebox.gui;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import se.metacraft.portable_jukebox.item.BoomboxItem;
import se.metacraft.portable_jukebox.item.components.BoomboxComponents;

public class BoomboxStorageInventory implements Container {

	private final ItemStack boombox;
	private final Runnable onUpdate;

	public BoomboxStorageInventory(ItemStack boombox, Runnable onUpdate) {
		this.boombox = boombox;
		this.onUpdate = onUpdate;
	}

	@Override
	public int getContainerSize() {
		return boombox.getOrDefault(BoomboxComponents.STORAGE_SIZE, 0);
	}

	@Override
	public boolean isEmpty() {
		return BoomboxItem.reStream(BoomboxItem.getStorage(boombox)).findAny().isEmpty();
	}

	@Override
	public ItemStack getItem(int slot) {
		var items = BoomboxItem.getStorageList(boombox);
		if (slot >= items.size()) return ItemStack.EMPTY;
		return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		var items = BoomboxItem.getStorageList(boombox);
		if (slot >= items.size()) return ItemStack.EMPTY;
		var stack = items.get(slot).split(amount);
		BoomboxItem.setStorage(boombox, ItemContainerContents.fromItems(items));
		return stack;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		var items = BoomboxItem.getStorageList(boombox);
		if (slot >= items.size()) return ItemStack.EMPTY;
		var stack = items.set(slot, ItemStack.EMPTY);
		BoomboxItem.setStorage(boombox, ItemContainerContents.fromItems(items));
		return stack;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		var items = BoomboxItem.getStorageList(boombox);
		if (slot >= items.size()) return;
		items.set(slot, stack);
		BoomboxItem.setStorage(boombox, ItemContainerContents.fromItems(items));
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return stack.has(DataComponents.JUKEBOX_PLAYABLE);
	}

	@Override
	public void setChanged() {
		onUpdate.run();
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void clearContent() {
		boombox.remove(BoomboxComponents.BOOMBOX_STORAGE);
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}
}
