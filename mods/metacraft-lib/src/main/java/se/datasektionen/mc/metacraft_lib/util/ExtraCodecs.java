package se.datasektionen.mc.metacraft_lib.util;

import com.google.common.collect.Multimap;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.BaseMapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.Portal;
import net.minecraft.block.enums.Orientation;
import net.minecraft.entity.SpawnReason;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.Util;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import se.datasektionen.mc.metacraft_lib.util.helper.OrientationHelper;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public class ExtraCodecs {

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

	public static <K, V> Codec<Map<K, V>> multiTypeMapCodec(
			Codec<K> keyCodec,
			Function<K, Codec<? extends V>> elementCodecGetter,
			Keyable keys
	) {
		return new MultiTypeMapCodec<>(keyCodec, elementCodecGetter, keys).codec();
	}

	public static <K, V> Codec<Map<RegistryKey<K>, V>> multiTypeMapCodec(
			Registry<K> registry,
			Function<RegistryKey<K>, Codec<? extends V>> elementCodecGetter
	) {
		return multiTypeMapCodec(RegistryKey.createCodec(registry.getKey()), elementCodecGetter, registry);
	}

	public static class RegistryDependent {
		public static final Codec<Portal> PORTAL_CODEC = Registries.BLOCK.getCodec().flatXmap(
				block -> block instanceof Portal p ? DataResult.success(p) : DataResult.error(() -> block + " is not a portal"),
				portal -> portal instanceof Block b ? DataResult.success(b) : DataResult.error(() -> portal + " is not a block")
		);
	}

	public static class MultiTypeMapCodec<K, V> extends MapCodec<Map<K, V>> implements BaseMapCodec<K, V> {

		private final Codec<K> keyCodec;
		private final Function<K, Codec<? extends V>> elementCodecGetter;
		private final Keyable keys;

		private Codec<V> currentElementCodec;

		private <U extends V> Codec<V> fixCodec(Codec<U> codec) {
			return codec.flatXmap(
					DataResult::success,
					v -> {
						try {
							return DataResult.success((U) v);
						} catch (ClassCastException ignored) {
							return DataResult.error(() -> "Attempting to decode superclass with codec of subclass!");
						}
					}
			);
		}

		public MultiTypeMapCodec(
				Codec<K> keyCodec, Function<K, Codec<? extends V>> elementCodecGetter, Keyable keys
		) {
			this.keyCodec = keyCodec.xmap(key -> {
				currentElementCodec = fixCodec(elementCodecGetter.apply(key));
				return key;
			}, key -> {
				currentElementCodec = fixCodec(elementCodecGetter.apply(key));
				return key;
			});
			this.elementCodecGetter = elementCodecGetter;
			this.keys = keys;
		}


		@Override
		public Codec<K> keyCodec() {
			return keyCodec;
		}

		@Override
		public Codec<V> elementCodec() {
			return currentElementCodec;
		}

		@Override
		public <T> Stream<T> keys(final DynamicOps<T> ops) {
			return keys.keys(ops);
		}

		@Override
		public <T> DataResult<Map<K, V>> decode(final DynamicOps<T> ops, final MapLike<T> input) {
			return BaseMapCodec.super.decode(ops, input);
		}

		@Override
		public <T> RecordBuilder<T> encode(final Map<K, V> input, final DynamicOps<T> ops, final RecordBuilder<T> prefix) {
			return BaseMapCodec.super.encode(input, ops, prefix);
		}

		@Override
		public boolean equals(final Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			final MultiTypeMapCodec<?, ?> that = (MultiTypeMapCodec<?, ?>) o;
			return Objects.equals(keyCodec, that.keyCodec) && Objects.equals(elementCodecGetter, that.elementCodecGetter);
		}

		@Override
		public int hashCode() {
			return Objects.hash(keyCodec, elementCodecGetter);
		}

		@Override
		public String toString() {
			return "MultiTypeMapCodec[" + keyCodec + ']';
		}
	}

}
