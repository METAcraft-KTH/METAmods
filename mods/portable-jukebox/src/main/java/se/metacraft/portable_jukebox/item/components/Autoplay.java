package se.metacraft.portable_jukebox.item.components;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.NonNullList;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Unit;
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
		boombox.remove(BoomboxComponents.AUTOPLAY);
	}

	public static Autoplay order(ItemStack boombox) {
		return boombox.has(BoomboxComponents.SHUFFLE) ? RANDOM : FORWARDS;
	}

	public static void togglePlaylist(ItemStack boombox) {
		if (boombox.has(BoomboxComponents.AUTOPLAY)) {
			turnOff(boombox);
		} else {
			boombox.set(BoomboxComponents.AUTOPLAY, Unit.INSTANCE);
		}
	}

	public static void toggleShuffle(ItemStack boombox) {
		if (boombox.has(BoomboxComponents.SHUFFLE)) {
			boombox.remove(BoomboxComponents.SHUFFLE);
		} else {
			boombox.set(BoomboxComponents.SHUFFLE, Unit.INSTANCE);
		}
	}

	public static boolean playFromStorage(ItemStack boombox, EntityRef playFrom, RandomSource random) {
		if (!order(boombox).pickNext(boombox, random)) return false;
		BoomboxItem.play(boombox, playFrom);
		return true;
	}

	/** Plays the next disc if the playlist is on; false means stop as usual. */
	public static boolean playNext(ItemStack boombox, EntityRef playFrom, RandomSource random) {
		if (!boombox.has(BoomboxComponents.AUTOPLAY) || !order(boombox).pickNext(boombox, random)) return false;
		BoomboxItem.play(boombox, playFrom);
		return true;
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
		var current = boombox.getOrDefault(BoomboxComponents.BOOMBOX, BoomboxInvData.EMPTY).getItem();
		var currentSlot = boombox.get(BoomboxComponents.CURRENT_SLOT);
		OptionalInt slot = pickSlot(storage, currentSlot != null ? OptionalInt.of(currentSlot) : OptionalInt.empty(), random);

		if (slot.isPresent()) {
			var next = storage.get(slot.getAsInt()).copy();
			storage.set(slot.getAsInt(), current.copy());

			BoomboxItem.setStorage(boombox, ItemContainerContents.fromItems(storage));
			boombox.update(BoomboxComponents.BOOMBOX, BoomboxInvData.EMPTY, inv -> inv.withItem(next));
			boombox.set(BoomboxComponents.CURRENT_SLOT, slot.getAsInt());
			return true;
		}

		return false;
	}
}
