package se.metacraft.portable_jukebox.gui;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import se.metacraft.portable_jukebox.item.components.Components;
import se.metacraft.portable_jukebox.upgrade.JukeboxUpgrades;

/**
 * The storage rows of a portable jukebox, read from and written straight to the jukebox's
 * storage component. Holds music discs only, one per slot. Locked slots are not shown as slots
 * at all, so this container does not check the tier itself.
 */
public class PortableJukeboxStorageInventory implements Container {

	private final ItemStack portableJukebox;
	private final Runnable onUpdate;

	public PortableJukeboxStorageInventory(ItemStack portableJukebox, Runnable onUpdate) {
		this.portableJukebox = portableJukebox;
		this.onUpdate = onUpdate;
	}

	@Override
	public int getContainerSize() {
		return JukeboxUpgrades.STORAGE_SIZE;
	}

	@Override
	public boolean isEmpty() {
		return JukeboxUpgrades.getStorage(portableJukebox).stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getItem(int slot) {
		return JukeboxUpgrades.getStorage(portableJukebox).get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		var items = JukeboxUpgrades.getStorage(portableJukebox);
		var stack = items.get(slot).split(amount);
		JukeboxUpgrades.setStorage(portableJukebox, items);
		return stack;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		var items = JukeboxUpgrades.getStorage(portableJukebox);
		var stack = items.set(slot, ItemStack.EMPTY);
		JukeboxUpgrades.setStorage(portableJukebox, items);
		return stack;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		var items = JukeboxUpgrades.getStorage(portableJukebox);
		items.set(slot, stack);
		JukeboxUpgrades.setStorage(portableJukebox, items);
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
