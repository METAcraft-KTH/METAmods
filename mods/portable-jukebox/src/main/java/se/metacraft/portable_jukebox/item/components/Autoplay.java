package se.metacraft.portable_jukebox.item.components;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.NonNullList;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jspecify.annotations.NonNull;
import se.metacraft.lib.util.EntityRef;
import se.metacraft.portable_jukebox.item.PortableJukeboxItem;

import java.util.OptionalInt;

public enum Autoplay implements StringRepresentable {
	FORWARDS("forwards"),
	RANDOM("random");

	public static final Codec<Autoplay> CODEC = StringRepresentable.fromEnum(Autoplay::values);

	private final String id;

	Autoplay(String id) {
		this.id = id;
	}

	@Override
	public @NonNull String getSerializedName() {
		return id;
	}

	public static void turnOff(ItemStack jukebox) {
		jukebox.remove(Components.AUTOPLAY);
	}

	/** Turns shuffle on and plays a random disc. Does nothing below the shuffle tier or with no discs at all. */
	public void start(ItemStack jukebox, EntityRef playFrom, RandomSource random) {
		if (!pickNext(jukebox, random)) return;
		jukebox.set(Components.AUTOPLAY, this);
		PortableJukeboxItem.play(jukebox, playFrom);
	}

	/**
	 * A song has just ended: if shuffle is on, play the next random disc and return true.
	 * Returns false when shuffle is off (or can't go on), and the jukebox should stop as usual.
	 */
	public static boolean playNext(ItemStack jukebox, EntityRef playFrom, RandomSource random) {
		var autoplay = jukebox.get(Components.AUTOPLAY);
		if (autoplay == null) return false;
		if (!autoplay.pickNext(jukebox, random)) {
			turnOff(jukebox);
			return false;
		}
		PortableJukeboxItem.play(jukebox, playFrom);
		return true;
	}

	public boolean deterministic() {
		return this == FORWARDS;
	}

	public OptionalInt pickSlot(NonNullList<ItemStack> storage, OptionalInt currentSlot, RandomSource random) {
		switch (this) {
			case RANDOM -> {
				IntList filledSlots = new IntArrayList();
				for (int i = 0; i < storage.size(); i++) {
					if (!storage.get(i).isEmpty()) {
						filledSlots.add(i);
					}
				}
				if (filledSlots.isEmpty()) return OptionalInt.empty();
				return OptionalInt.of(filledSlots.getInt(random.nextInt(filledSlots.size())));
			}
			default -> {
				if (storage.isEmpty()) return OptionalInt.empty();
				if (currentSlot.isEmpty()) return OptionalInt.of(0);
				for (int i = 0; i < storage.size(); i++) {
					int chosen = (currentSlot.orElse(0) + i + 1) % storage.size();
					if (!storage.get(chosen).isEmpty()) {
						return OptionalInt.of(chosen);
					}
				}
				return currentSlot;
			}
		}
	}

	private boolean pickNext(ItemStack jukebox, RandomSource random) {
		var storage = PortableJukeboxItem.getStorageList(jukebox);
		var current = jukebox.getOrDefault(Components.PORTABLE_JUKEBOX, PortableJukeboxInvData.EMPTY).getItem();
		var currentSlot = jukebox.get(Components.CURRENT_SLOT);
		OptionalInt slot = pickSlot(storage, currentSlot != null ? OptionalInt.of(currentSlot) : OptionalInt.empty(), random);

		if (slot.isPresent()) {
			var next = storage.get(slot.getAsInt()).copy();
			storage.set(slot.getAsInt(), current.copy());

			PortableJukeboxItem.setStorage(jukebox, ItemContainerContents.fromItems(storage));
			jukebox.update(Components.PORTABLE_JUKEBOX, PortableJukeboxInvData.EMPTY, inv -> inv.withItem(next));
			jukebox.set(Components.CURRENT_SLOT, slot.getAsInt());
			return true;
		}

		return false;
	}
}
