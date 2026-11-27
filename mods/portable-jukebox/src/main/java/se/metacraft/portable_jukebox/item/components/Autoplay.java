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
import se.metacraft.portable_jukebox.item.BoomboxItem;

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

	public static void turnOff(ItemStack boombox) {
		boombox.remove(Components.AUTOPLAY);
	}

	/** Turns shuffle on and plays a random disc. Does nothing below the shuffle tier or with no discs at all. */
	public void start(ItemStack boombox, EntityRef playFrom, RandomSource random) {
		if (!pickNext(boombox, random)) return;
		boombox.set(Components.AUTOPLAY, this);
		BoomboxItem.play(boombox, playFrom);
	}

	/**
	 * A song has just ended: if shuffle is on, play the next random disc and return true.
	 * Returns false when shuffle is off (or can't go on), and the boombox should stop as usual.
	 */
	public static boolean playNext(ItemStack boombox, EntityRef playFrom, RandomSource random) {
		var autoplay = boombox.get(Components.AUTOPLAY);
		if (autoplay == null) return false;
		if (!autoplay.pickNext(boombox, random)) {
			turnOff(boombox);
			return false;
		}
		BoomboxItem.play(boombox, playFrom);
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

	private boolean pickNext(ItemStack boombox, RandomSource random) {
		var storage = BoomboxItem.getStorageList(boombox);
		var current = boombox.getOrDefault(Components.BOOMBOX, BoomboxInvData.EMPTY).getItem();
		var currentSlot = boombox.get(Components.CURRENT_SLOT);
		OptionalInt slot = pickSlot(storage, currentSlot != null ? OptionalInt.of(currentSlot) : OptionalInt.empty(), random);

		if (slot.isPresent()) {
			var next = storage.get(slot.getAsInt()).copy();
			storage.set(slot.getAsInt(), current.copy());

			BoomboxItem.setStorage(boombox, ItemContainerContents.fromItems(storage));
			boombox.update(Components.BOOMBOX, BoomboxInvData.EMPTY, inv -> inv.withItem(next));
			boombox.set(Components.CURRENT_SLOT, slot.getAsInt());
			return true;
		}

		return false;
	}
}
