package se.metacraft.portable_jukebox.upgrade;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import se.metacraft.lib.util.EntityRef;
import se.metacraft.portable_jukebox.item.PortableJukeboxItem;
import se.metacraft.portable_jukebox.item.components.Components;
import se.metacraft.portable_jukebox.item.components.PortableJukeboxInvData;

import java.util.ArrayList;

/**
 * Shuffle, from tier {@link JukeboxUpgrades#SHUFFLE_TIER}: plays a random stored disc, and when it
 * ends another, until Stop (or Play) is pressed. The next disc is picked from the storage slots and
 * swapped into the disc slot, so the song that just played is never picked twice in a row. With
 * nothing stored, the disc in the disc slot plays again.
 */
public final class JukeboxShuffle {

	private JukeboxShuffle() {}

	public static boolean isOn(ItemStack jukebox) {
		return jukebox.getOrDefault(Components.PORTABLE_JUKEBOX_SHUFFLE, false);
	}

	public static void turnOff(ItemStack jukebox) {
		jukebox.remove(Components.PORTABLE_JUKEBOX_SHUFFLE);
	}

	/** Turns shuffle on and plays a random disc. Does nothing below the shuffle tier or with no discs at all. */
	public static void start(ItemStack jukebox, EntityRef playFrom, RandomSource random) {
		if (JukeboxUpgrades.getTier(jukebox) < JukeboxUpgrades.SHUFFLE_TIER) return;
		if (!pickNext(jukebox, random)) return;
		jukebox.set(Components.PORTABLE_JUKEBOX_SHUFFLE, true);
		PortableJukeboxItem.play(jukebox, playFrom);
	}

	/**
	 * A song has just ended: if shuffle is on, play the next random disc and return true.
	 * Returns false when shuffle is off (or can't go on), and the jukebox should stop as usual.
	 */
	public static boolean playNext(ItemStack jukebox, EntityRef playFrom, RandomSource random) {
		if (!isOn(jukebox) || JukeboxUpgrades.getTier(jukebox) < JukeboxUpgrades.SHUFFLE_TIER) return false;
		if (!pickNext(jukebox, random)) {
			turnOff(jukebox);
			return false;
		}
		PortableJukeboxItem.play(jukebox, playFrom);
		return true;
	}

	/** Swaps a random stored disc into the disc slot. False if there is nothing to play at all. */
	private static boolean pickNext(ItemStack jukebox, RandomSource random) {
		var storage = JukeboxUpgrades.getStorage(jukebox);
		var candidates = new ArrayList<Integer>();
		int unlocked = JukeboxUpgrades.unlockedSlots(JukeboxUpgrades.getTier(jukebox));
		for (int i = 0; i < unlocked; i++) {
			if (!storage.get(i).isEmpty()) candidates.add(i);
		}
		var current = jukebox.getOrDefault(Components.PORTABLE_JUKEBOX, PortableJukeboxInvData.EMPTY).getItem();
		if (candidates.isEmpty()) {
			return !current.isEmpty();
		}
		int slot = candidates.get(random.nextInt(candidates.size()));
		var next = storage.set(slot, current);
		JukeboxUpgrades.setStorage(jukebox, storage);
		jukebox.update(Components.PORTABLE_JUKEBOX, PortableJukeboxInvData.EMPTY, inv -> inv.withItem(next));
		return true;
	}
}
