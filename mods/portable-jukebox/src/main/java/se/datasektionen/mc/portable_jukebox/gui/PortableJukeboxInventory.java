package se.datasektionen.mc.portable_jukebox.gui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import se.datasektionen.mc.portable_jukebox.item.components.Components;

public class PortableJukeboxInventory implements Inventory {

	private final ItemStack portableJukebox;
	private final Runnable onUpdate;

	public PortableJukeboxInventory(ItemStack portableJukebox, Runnable onUpdate) {
		this.portableJukebox = portableJukebox;
		this.onUpdate = onUpdate;
	}

	private ItemStack getStack() {
		return portableJukebox.getOrDefault(Components.PORTABLE_JUKEBOX, ItemStack.EMPTY);
	}

	private ItemStack remove() {
		return portableJukebox.remove(Components.PORTABLE_JUKEBOX);
	}

	@Override
	public int size() {
		return 1;
	}

	@Override
	public boolean isEmpty() {
		return getStack().isEmpty();
	}

	@Override
	public ItemStack getStack(int slot) {
		return getStack();
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		var stack = getStack().split(amount);
		if (getStack().isEmpty()) {
			remove();
		}
		return stack.isEmpty() ? ItemStack.EMPTY : stack;
	}

	@Override
	public ItemStack removeStack(int slot) {
		return remove();
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		if (!stack.isEmpty()) {
			portableJukebox.set(Components.PORTABLE_JUKEBOX, stack);
		} else {
			remove();
		}
	}

	@Override
	public void markDirty() {
		onUpdate.run();
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return true;
	}

	@Override
	public void clear() {
		remove();
	}

	@Override
	public int getMaxCountPerStack() {
		return 1;
	}

	public ItemStack getJukebox() {
		return portableJukebox;
	}
}
