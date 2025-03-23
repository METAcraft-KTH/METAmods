package se.datasektionen.mc.metacraft_lib.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.StringNbtReader;

import java.util.HashSet;

/**
 * Some things such as entities depend on the ability to specify type information in the NBT data.
 * This is not possible in JSON, so SNBT must be allowed as an option.
 * However, providing the entire data in the form of SNBT is not ideal for readability or writability.
 * This class allows serializing and deserializing NBT in a way which allows both the normal codec and SNBT
 * to be used at once for different sections of the same NBT data, in the `data` and `snbt` parameters respectively.
 * Alternatively, NBT can also be provided in the normal and SNBT formats directly, and will be serialized in the same way.
 * Use {@link AccurateSerializableNBT#getMerged()} to get the merged data.
 * Note that if any data is provided via both methods, the SNBT one will be preferred.
 */
public class AccurateSerializableNBT {

	protected static final Codec<NbtCompound> SNBT_CODEC = Codec.STRING.comapFlatMap(line -> {
		try {
			return DataResult.success(StringNbtReader.readCompound(line));
		} catch (CommandSyntaxException e) {
			return DataResult.error(e::getMessage);
		}
	}, NbtElement::toString);

	protected static final Codec<AccurateSerializableNBT> SPECIFIC_CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					NbtCompound.CODEC.fieldOf("data").forGetter(t -> t.data),
					SNBT_CODEC.fieldOf("snbt").forGetter(t -> t.snbt)
			).apply(instance, AccurateSerializableNBT::new)
	);

	public static final Codec<AccurateSerializableNBT> CODEC = new Codec<>() {
		@Override
		public <T> DataResult<Pair<AccurateSerializableNBT, T>> decode(DynamicOps<T> dynamicOps, T t) {
			var merged = SPECIFIC_CODEC.decode(dynamicOps, t);
			if (merged.isSuccess()) {
				return merged;
			}
			var nbt = NbtCompound.CODEC.decode(dynamicOps, t);
			if (nbt.isSuccess()) {
				return nbt.map(
					d -> d.mapFirst(
						data -> new AccurateSerializableNBT(
								data, new NbtCompound()
						)
					)
				);
			}
			var snbt = SNBT_CODEC.decode(dynamicOps, t);
			if (snbt.isSuccess()) {
				return snbt.map(
						d -> d.mapFirst(
								data -> new AccurateSerializableNBT(
										new NbtCompound(), data
								)
						)
				);
			}

			return DataResult.error(
					() -> "Failed to parse accurate NBT: Combined Attempt:" +
							merged.error().orElseThrow().message() +
							" | Default Attempt: " + nbt.error().orElseThrow().message() +
							" | SNBT Attempt: " + snbt.error().orElseThrow().message()
			);
		}

		@Override
		public <T> DataResult<T> encode(AccurateSerializableNBT accurateSerializableNBT, DynamicOps<T> dynamicOps, T t) {
			if (accurateSerializableNBT.snbt.isEmpty()) {
				return NbtCompound.CODEC.encode(accurateSerializableNBT.data, dynamicOps, t);
			}
			if (accurateSerializableNBT.data.isEmpty()) {
				return SNBT_CODEC.encode(accurateSerializableNBT.snbt, dynamicOps, t);
			}
			return SPECIFIC_CODEC.encode(accurateSerializableNBT, dynamicOps, t);
		}
	};

	private final NbtCompound data;
	private final NbtCompound snbt;
	private final NbtCompound merged;

	public AccurateSerializableNBT(NbtCompound data, NbtCompound snbt) {
		this.data = data;
		this.snbt = snbt;
		this.merged = mergeCompounds(snbt, data);
	}

	public NbtCompound getMerged() {
		return merged;
	}

	public static NbtCompound mergeCompounds(NbtCompound primaryCompound, NbtCompound secondaryCompound) {
		NbtCompound result = new NbtCompound();
		var keys = new HashSet<>(primaryCompound.getKeys());
		keys.addAll(secondaryCompound.getKeys());
		for (var key : keys) {
			result.put(key, merge(primaryCompound.get(key), secondaryCompound.get(key)));
		}
		return result;
	}

	public static NbtList mergeLists(NbtList primaryList, NbtList secondaryList) {
		NbtList result = new NbtList();
		int minSize = Math.min(primaryList.size(), secondaryList.size());
		for (int i = 0; i < minSize; i++) {
			result.add(merge(primaryList.get(i), secondaryList.get(i)));
		}
		if (primaryList.size() != secondaryList.size()) {
			var remainder = primaryList.size() > secondaryList.size() ? primaryList : secondaryList;
			for (int i = minSize; i < remainder.size(); i++) {
				result.add(remainder.get(i));
			}
		}
		return result;
	}

	public static NbtElement merge(NbtElement primary, NbtElement secondary) {
		return switch (primary) {
			case null -> secondary;
			case NbtCompound primaryCompound when secondary instanceof NbtCompound secondaryCompound ->
					mergeCompounds(primaryCompound, secondaryCompound);
			case NbtList primaryList when secondary instanceof NbtList secondaryList ->
					mergeLists(primaryList, secondaryList);
			default -> primary;
		};
	}
}
