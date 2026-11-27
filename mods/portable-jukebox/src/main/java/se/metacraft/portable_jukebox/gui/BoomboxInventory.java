package se.metacraft.portable_jukebox.gui;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import se.metacraft.portable_jukebox.item.components.BoomboxComponents;
import se.metacraft.portable_jukebox.item.components.BoomboxInvData;

public class BoomboxInventory implements Container {

	private final ItemStack boombox;
	private final Runnable onUpdate;

	public BoomboxInventory(ItemStack boombox, Runnable onUpdate) {
		this.boombox = boombox;
		this.onUpdate = onUpdate;
	}

	private ItemStack getStack() {
		return boombox.getOrDefault(BoomboxComponents.BOOMBOX, BoomboxInvData.EMPTY).getItem();
	}

	private ItemStack remove() {
		var prev = boombox.update(
			BoomboxComponents.BOOMBOX, BoomboxInvData.EMPTY, boombox -> boombox.withItem(ItemStack.EMPTY)
		);
		return prev != null ? prev.getItem() : ItemStack.EMPTY;
	}

	@Override
	public int getContainerSize() {
		return 1;
	}

	@Override
	public boolean isEmpty() {
		return getStack().isEmpty();
	}

	@Override
	public ItemStack getItem(int slot) {
		return getStack();
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		var innerStack = getStack();
		var stack = innerStack.split(amount);
		if (innerStack.isEmpty()) {
			remove();
		}
		return stack.isEmpty() ? ItemStack.EMPTY : stack;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return remove();
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (!stack.isEmpty()) {
			boombox.update(BoomboxComponents.BOOMBOX, BoomboxInvData.EMPTY, boombox -> boombox.withItem(stack));
		} else {
			remove();
		}
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
		remove();
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	public ItemStack getBoombox() {
		return boombox;
	}
}
