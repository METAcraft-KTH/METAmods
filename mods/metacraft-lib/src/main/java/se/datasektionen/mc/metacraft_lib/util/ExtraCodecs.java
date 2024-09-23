package se.datasektionen.mc.metacraft_lib.util;

import com.google.common.collect.Multimap;
import com.mojang.serialization.*;
import net.minecraft.block.Block;
import net.minecraft.block.Portal;
import net.minecraft.block.enums.Orientation;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.Util;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import se.datasektionen.mc.metacraft_lib.util.helper.OrientationHelper;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@SuppressWarnings("unused")
public class ExtraCodecs {

	public static final Codec<PlayerModelPart> MODEL_PART_CODEC = enumCodec(PlayerModelPart.class, true);

	public static final Codec<Set<PlayerModelPart>> MODEL_PART_SET_CODEC = MODEL_PART_CODEC.listOf().xmap(
			list -> list.isEmpty() ? Set.of() : EnumSet.copyOf(list), ArrayList::new
	);

	public static final Codec<Orientation> ORIENTATION_CODEC = Codec.withAlternative(
			StringIdentifiable.createCodec(Orientation::values),
			Direction.CODEC,
			OrientationHelper::fromDirection
	);

	public static final Codec<ChunkPos> CHUNK_POS_CODEC = Codec.INT_STREAM.comapFlatMap(
			stream -> Util.decodeFixedLengthArray(stream, 2).map(values -> new ChunkPos(values[0], values[1])),
			pos -> IntStream.of(pos.x, pos.z)
	).stable();

	public static final Codec<SpawnReason> SPAWN_REASON_CODEC = enumCodec(SpawnReason.class, true);


	public static final Codec<SoundCategory> SOUND_CATEGORY_CODEC = enumCodec(SoundCategory.class, true);

	public static final Codec<Box> BOX_CODEC = Codec.DOUBLE.listOf().listOf().flatXmap(
			positions -> {
				if (positions.size() == 2) {
					var pos1 = positions.get(0);
					var pos2 = positions.get(1);
					if (pos1.size() == 3 && pos2.size() == 3) {
						return DataResult.success(
								new Box(
										pos1.get(0), pos1.get(1), pos1.get(2),
										pos2.get(0), pos2.get(1), pos2.get(2)
								)
						);
					} else {
						return DataResult.error(
								() -> "Expected [x, y, z], [x, y, z]" + " Found + " + pos1 + ", " + pos2
						);
					}
				} else {
					return DataResult.error(() -> "Expected 2 positions, found " + positions.size());
				}
			},
			box -> {
				var positions = new ArrayList<List<Double>>(2);
				var pos1 = new ArrayList<Double>(3);
				pos1.add(box.minX);
				pos1.add(box.minY);
				pos1.add(box.minZ);
				var pos2 = new ArrayList<Double>(3);
				pos2.add(box.maxX);
				pos2.add(box.maxY);
				pos2.add(box.maxZ);
				positions.add(pos1);
				positions.add(pos2);
				return DataResult.success(positions);
			}
	);

	/**
	 * Creates a codec for the given enum class.
	 * Note, this is only meant for enums that already exist.
	 * If you're making your own enum, please make it {@link net.minecraft.util.StringIdentifiable} instead.
	 * @param enumClass The enum class to make a codec for.
	 * @param forceLowercase Normally, enums use all uppercase names. However, all lowercase looks better serialized in my opinion. If this is true, the enum will be serialized in lowercase and then uppercased when parsed. Warning, if any of the enum constants have any lowercase letters you need to set this to false!
	 * @return A codec.
	 * @param <T> The enum type.
	 */
	public static <T extends Enum<T>> Codec<T> enumCodec(Class<T> enumClass, boolean forceLowercase) {
		return Codec.STRING.flatXmap(
				key -> {
					try {
						return DataResult.success(Enum.valueOf(enumClass, forceLowercase ? key.toUpperCase(Locale.ROOT) : key));
					} catch (IllegalArgumentException err) {
						return DataResult.error(err::getMessage);
					}
				},
				value -> DataResult.success(forceLowercase ? value.name().toLowerCase(Locale.ROOT) : value.name())
		);
	}

	public static <K, V> Codec<Multimap<K, V>> unboundedMultimap(
			Codec<K> keyCodec, Codec<V> valueCodec, Supplier<Multimap<K, V>> multimapCreator
	) {
		return Codec.unboundedMap(keyCodec, valueCodec.listOf()).xmap(
				map -> mapToMultimap(map, multimapCreator),
				ExtraCodecs::multimapToMap
		);
	}

	public static <K, V> MapCodec<Multimap<K, V>> simpleMultimap(
			Codec<K> keyCodec, Codec<V> valueCodec, Keyable keys, Supplier<Multimap<K, V>> multimapCreator
	) {
		return Codec.simpleMap(keyCodec, valueCodec.listOf(), keys).xmap(
				map -> mapToMultimap(map, multimapCreator),
				ExtraCodecs::multimapToMap
		);
	}

	private static <K, V> Multimap<K, V> mapToMultimap(
			Map<K, List<V>> map, Supplier<Multimap<K, V>> multimapCreator
	) {
		var multimap = multimapCreator.get();
		map.forEach(multimap::putAll);
		return multimap;
	}

	private static <K, V> Map<K, List<V>> multimapToMap(
			Multimap<K, V> multimap
	) {
		return multimap.asMap().entrySet().stream().collect(
				Collectors.toMap(Map.Entry::getKey, entry -> new ArrayList<>(entry.getValue()))
		);
	}

	public static class RegistryDependent {
		public static final Codec<Portal> PORTAL_CODEC = Registries.BLOCK.getCodec().flatXmap(
				block -> block instanceof Portal p ? DataResult.success(p) : DataResult.error(() -> block + " is not a portal"),
				portal -> portal instanceof Block b ? DataResult.success(b) : DataResult.error(() -> portal + " is not a block")
		);
	}

}
