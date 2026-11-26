package se.metacraft.portable_jukebox.gui;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import se.metacraft.portable_jukebox.item.PortableJukeboxItem;
import se.metacraft.portable_jukebox.item.components.Components;

public class PortableJukeboxStorageInventory implements Container {

	private final ItemStack portableJukebox;
	private final Runnable onUpdate;

	public PortableJukeboxStorageInventory(ItemStack portableJukebox, Runnable onUpdate) {
		this.portableJukebox = portableJukebox;
		this.onUpdate = onUpdate;
	}

	@Override
	public int getContainerSize() {
		return portableJukebox.getOrDefault(Components.STORAGE_SIZE, 0);
	}

	@Override
	public boolean isEmpty() {
		return PortableJukeboxItem.reStream(PortableJukeboxItem.getStorage(portableJukebox)).findAny().isEmpty();
	}

	@Override
	public ItemStack getItem(int slot) {
		var items = PortableJukeboxItem.getStorageList(portableJukebox);
		if (slot >= items.size()) return ItemStack.EMPTY;
		return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		var items = PortableJukeboxItem.getStorageList(portableJukebox);
		if (slot >= items.size()) return ItemStack.EMPTY;
		var stack = items.get(slot).split(amount);
		PortableJukeboxItem.setStorage(portableJukebox, ItemContainerContents.fromItems(items));
		return stack;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		var items = PortableJukeboxItem.getStorageList(portableJukebox);
		if (slot >= items.size()) return ItemStack.EMPTY;
		var stack = items.set(slot, ItemStack.EMPTY);
		PortableJukeboxItem.setStorage(portableJukebox, ItemContainerContents.fromItems(items));
		return stack;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		var items = PortableJukeboxItem.getStorageList(portableJukebox);
		if (slot >= items.size()) return;
		items.set(slot, stack);
		PortableJukeboxItem.setStorage(portableJukebox, ItemContainerContents.fromItems(items));
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
		portableJukebox.remove(Components.PORTABLE_JUKEBOX_STORAGE);
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}
}
