package se.metacraft.portable_jukebox.upgrade;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import se.metacraft.portable_jukebox.item.components.Components;

/**
 * A portable jukebox's upgrade tier and its disc storage. Tier 1 unlocks 9 storage slots, tier 2
 * 27 more (36 in all), and tier 3 the pitch and volume controls and Shuffle. Tiers go on in order,
 * one at a time, by crafting the jukebox with the next upgrade ({@link JukeboxUpgradeRecipe}).
 */
public final class JukeboxUpgrades {

	public static final int MAX_TIER = 3;
	/** Storage slots unlocked at each tier, 0 to {@link #MAX_TIER}. */
	private static final int[] UNLOCKED_SLOTS = {0, 9, 36, 36};
	public static final int STORAGE_SIZE = UNLOCKED_SLOTS[MAX_TIER];
	/** The tier that unlocks the pitch and volume controls. */
	public static final int CONFIGURATION_TIER = MAX_TIER;
	/** The tier that unlocks the Shuffle button. */
	public static final int SHUFFLE_TIER = MAX_TIER;

	private JukeboxUpgrades() {}

	public static int getTier(ItemInstance jukebox) {
		return jukebox.getOrDefault(Components.PORTABLE_JUKEBOX_TIER, 0);
	}

	public static int unlockedSlots(int tier) {
		return UNLOCKED_SLOTS[Math.clamp(tier, 0, MAX_TIER)];
	}

	/** How many storage slots this tier adds to the one before it. */
	public static int slotsAdded(int tier) {
		return unlockedSlots(tier) - unlockedSlots(tier - 1);
	}

	/** The jukebox's storage, always {@link #STORAGE_SIZE} long, empty slots as {@link ItemStack#EMPTY}. */
	public static NonNullList<ItemStack> getStorage(ItemStack jukebox) {
		var items = NonNullList.withSize(STORAGE_SIZE, ItemStack.EMPTY);
		jukebox.getOrDefault(Components.PORTABLE_JUKEBOX_STORAGE, ItemContainerContents.EMPTY).copyInto(items);
		return items;
	}

	public static void setStorage(ItemStack jukebox, NonNullList<ItemStack> items) {
		if (items.stream().allMatch(ItemStack::isEmpty)) {
			jukebox.remove(Components.PORTABLE_JUKEBOX_STORAGE);
		} else {
			jukebox.set(Components.PORTABLE_JUKEBOX_STORAGE, ItemContainerContents.fromItems(items));
		}
	}
}
