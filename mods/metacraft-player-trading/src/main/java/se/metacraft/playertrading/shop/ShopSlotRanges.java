package se.metacraft.playertrading.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.ints.*;
import net.minecraft.world.inventory.SlotRange;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class ShopSlotRanges {

	public static final int MAXIMUM = 8;

	public static final SlotRange WILDCARD = SlotRange.of(
			"*", IntImmutableList.toList(IntStream.range(0, MAXIMUM))
	);

	public static final Codec<SlotRange> CODEC = Codec.STRING.comapFlatMap(
			ShopSlotRanges::parseSlot, SlotRange::toString
	);

	private static DataResult<Integer> parseInt(String arg) {
		try {
			int num = Integer.parseInt(arg);
			if (num > MAXIMUM) {
				return DataResult.error(() -> num + " is too large");
			}
			if (num < 0) {
				return DataResult.error(() -> num + " is not positive");
			}
			return DataResult.success(num);
		} catch (NumberFormatException e) {
			return DataResult.error(e::getMessage);
		}
	}

	private static DataResult<SlotRange> parseSlot(String arg) {
		if (arg.equals("*")) {
			return DataResult.success(WILDCARD);
		}
		IntSet slots = new IntOpenHashSet();
		List<Supplier<String>> errors = new ArrayList<>();
		String[] ranges = arg.split(",");
		for (var range : ranges) {
			if (range.contains("-") && !range.startsWith("-")) {
				var values = range.split("-");
				if (values.length != 2) {
					errors.add(() -> "Not a range " + range);
					continue;
				}
				var left = parseInt(values[0]);
				var right = parseInt(values[1]);
				if (left.isError() || right.isError()) {
					errors.add(
							() -> "Error parsing " + range + ": Left: " +
									left.error().map(DataResult.Error::message).orElse("null") +
									"Right: " + right.error().map(DataResult.Error::message).orElse("null")
					);
					continue;
				}
				int min = left.getOrThrow();
				int max = right.getOrThrow();
				if (min > max) {
					errors.add(
							() -> "Error parsing " + range + ": Minimum " + min + " is greater than maximum " + max
					);
					continue;
				}
				for (int i = min; i <= max; i++) {
					slots.add(i);
				}
			} else {
				var num = parseInt(range);
				num.result().ifPresent(slots::add);
				num.error().ifPresent(e -> errors.add(e::message));
			}
		}
		Supplier<SlotRange> list = () -> SlotRange.of(arg, IntImmutableList.toList(slots.intStream().sorted()));
		if (errors.isEmpty()) {
			return DataResult.success(list.get());
		} else {
			Supplier<String> error = () -> errors.stream().map(Supplier::get).reduce(
					DataResult::appendMessages
			).orElse("");
			if (slots.isEmpty()) {
				return DataResult.error(error);
			} else {
				return DataResult.error(error, list.get());
			}
		}
	}

	public static SlotRange extractSlots(SlotRange sourceSlots, IntCollection slotsToRemove) {
		if (sourceSlots.getSerializedName().equals("*")) {
			return sourceSlots;
		}
		IntStream slots = sourceSlots.slots().intStream();
		for (int slot : slotsToRemove) {
			slots = extractAndDecrement(slots, slot);
		}
		IntList newSlots = IntArrayList.toList(slots.sorted());
		StringBuilder encoded = new StringBuilder();
		int prev = -1;
		boolean sequence = false;
		for (int i : newSlots) {
			if (prev == -1) {
				encoded.append(i);
			} else if (prev == i - 1) {
				sequence = true;
			} else {
				if (sequence) {
					encoded.append("-").append(prev);
				}
				encoded.append(",").append(i);
				sequence = false;
			}

			prev = i;
		}
		return SlotRange.of(encoded.toString(), newSlots);
	}

	public static IntStream extractAndDecrement(IntStream slots, int toRemove) {
		return slots.filter(slot -> slot != toRemove).map(slot -> slot > toRemove ? slot-1 : slot);
	}

}
