package nu.metacraft.lib.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;

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

	protected static final Codec<CompoundTag> SNBT_CODEC = Codec.STRING.comapFlatMap(line -> {
		try {
			return DataResult.success(TagParser.parseCompoundFully(line));
		} catch (CommandSyntaxException e) {
			return DataResult.error(e::getMessage);
		}
	}, Tag::toString);

	protected static final Codec<AccurateSerializableNBT> SPECIFIC_CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					CompoundTag.CODEC.fieldOf("data").forGetter(t -> t.data),
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
			var nbt = CompoundTag.CODEC.decode(dynamicOps, t);
			if (nbt.isSuccess()) {
				return nbt.map(
					d -> d.mapFirst(
						data -> new AccurateSerializableNBT(
								data, new CompoundTag()
						)
					)
				);
			}
			var snbt = SNBT_CODEC.decode(dynamicOps, t);
			if (snbt.isSuccess()) {
				return snbt.map(
						d -> d.mapFirst(
								data -> new AccurateSerializableNBT(
										new CompoundTag(), data
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
				return CompoundTag.CODEC.encode(accurateSerializableNBT.data, dynamicOps, t);
			}
			if (accurateSerializableNBT.data.isEmpty()) {
				return SNBT_CODEC.encode(accurateSerializableNBT.snbt, dynamicOps, t);
			}
			return SPECIFIC_CODEC.encode(accurateSerializableNBT, dynamicOps, t);
		}
	};

	private final CompoundTag data;
	private final CompoundTag snbt;
	private final CompoundTag merged;

	public AccurateSerializableNBT(CompoundTag data, CompoundTag snbt) {
		this.data = data;
		this.snbt = snbt;
		this.merged = mergeCompounds(snbt, data);
	}

	public CompoundTag getMerged() {
		return merged;
	}

	public static CompoundTag mergeCompounds(CompoundTag primaryCompound, CompoundTag secondaryCompound) {
		CompoundTag result = new CompoundTag();
		var keys = new HashSet<>(primaryCompound.keySet());
		keys.addAll(secondaryCompound.keySet());
		for (var key : keys) {
			result.put(key, merge(primaryCompound.get(key), secondaryCompound.get(key)));
		}
		return result;
	}

	public static ListTag mergeLists(ListTag primaryList, ListTag secondaryList) {
		ListTag result = new ListTag();
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

	public static Tag merge(Tag primary, Tag secondary) {
		return switch (primary) {
			case null -> secondary;
			case CompoundTag primaryCompound when secondary instanceof CompoundTag secondaryCompound ->
					mergeCompounds(primaryCompound, secondaryCompound);
			case ListTag primaryList when secondary instanceof ListTag secondaryList ->
					mergeLists(primaryList, secondaryList);
			default -> primary;
		};
	}
}
