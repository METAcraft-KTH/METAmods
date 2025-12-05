package nu.metacraft.lib.util;

import com.google.common.collect.Multimap;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.apache.commons.lang3.math.Fraction;
import org.pcollections.PCollection;
import org.pcollections.PMap;
import nu.metacraft.lib.util.helper.OrientationHelper;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.util.Util;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.phys.AABB;

@SuppressWarnings("unused")
public class METACodecs {

	public static final Codec<Relative> POSITION_FLAG_CODEC = enumCodec(Relative.class, true);
	public static final Codec<Set<Relative>> POSITION_FLAG_SET_CODEC = POSITION_FLAG_CODEC.listOf().xmap(
			HashSet::new, ArrayList::new
	);

	public static final Codec<Pose> ENTITY_POSE_CODEC = enumCodec(Pose.class, true);

	public static final Codec<Fraction> FRACTION_CODEC = Codec.withAlternative(
			Codec.STRING.comapFlatMap(
					str -> {
						try {
							return DataResult.success(Fraction.getFraction(str));
						} catch (ArithmeticException | NumberFormatException e) {
							return DataResult.error(e::getMessage);
						}
					},
					Fraction::toProperString
			),
			Codec.DOUBLE.comapFlatMap(
					decimal -> {
						try {
							return DataResult.success(Fraction.getFraction(decimal));
						} catch (ArithmeticException e) {
							return DataResult.error(e::getMessage);
						}
					},
					Fraction::doubleValue
			)
	);

	public static final Codec<Fraction> POSITIVE_FRACTION_CODEC = FRACTION_CODEC.validate(
			fraction -> fraction.doubleValue() > 0 ?
					DataResult.success(fraction) :
					DataResult.error(() -> "Fraction must be positive")
	);

	public static final Codec<InteractionHand> HAND_CODEC = enumCodec(InteractionHand.class, true);

	public static final Codec<PlayerModelPart> MODEL_PART_CODEC = StringRepresentable.fromEnum(PlayerModelPart::values);

	public static final Codec<Set<PlayerModelPart>> MODEL_PART_SET_CODEC = MODEL_PART_CODEC.listOf().xmap(
			list -> list.isEmpty() ? Set.of() : EnumSet.copyOf(list), ArrayList::new
	);

	public static final Codec<FrontAndTop> ORIENTATION_CODEC = Codec.withAlternative(
			StringRepresentable.fromEnum(FrontAndTop::values),
			Direction.CODEC,
			OrientationHelper::fromDirection
	);

	public static final Codec<ChunkPos> CHUNK_POS_CODEC = Codec.INT_STREAM.comapFlatMap(
			stream -> Util.fixedSize(stream, 2).map(values -> new ChunkPos(values[0], values[1])),
			pos -> IntStream.of(pos.x, pos.z)
	).stable();

	public static final Codec<EntitySpawnReason> SPAWN_REASON_CODEC = enumCodec(EntitySpawnReason.class, true);


	public static final Codec<SoundSource> SOUND_CATEGORY_CODEC = StringRepresentable.fromEnum(SoundSource::values);

	public static final Codec<AABB> BOX_CODEC = Codec.DOUBLE.listOf().listOf().flatXmap(
			positions -> {
				if (positions.size() == 2) {
					var pos1 = positions.get(0);
					var pos2 = positions.get(1);
					if (pos1.size() == 3 && pos2.size() == 3) {
						return DataResult.success(
								new AABB(
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

	public static <T> MapCodec<T> withAlternative(MapCodec<T> left, MapCodec<T> right) {
		return Codec.mapEither(left, right).xmap(
				Either::unwrap,
				Either::left
		);
	}

	public static <T, C extends Collection<T>> Codec<C> createCollectionCodec(
			Codec<T> codec, Supplier<C> collectionSupplier
	) {
		return createCollectionCodec(
				codec, collectionSupplier,
				(list, element) -> {
					list.add(element);
					return list;
				},
				(lhs, rhs) -> {
					lhs.addAll(rhs);
					return lhs;
				}
		);
	}

	public static <T, C extends PCollection<T>> Codec<C> createPCollectionCodec(
			Codec<T> codec, C emptyCollection
	) {
		if (!emptyCollection.isEmpty()) throw new IllegalArgumentException("Empty collection should be empty!");
		return createCollectionCodec(
				codec, () -> emptyCollection,
				(list, element) -> {
					return (C) list.plus(element);
				},
				(lhs, rhs) -> {
					return (C) lhs.plusAll(rhs);
				}
		);
	}

	public static <T, C extends Collection<T>> Codec<C> createCollectionCodec(
			Codec<T> codec, Supplier<C> collectionSupplier, BiFunction<C, T, C> adder, BinaryOperator<C> combiner
	) {
		return codec.listOf().xmap(
				list -> list.stream().reduce(collectionSupplier.get(), adder, combiner),
				c -> c.stream().toList()
		);
	}

	private static <L, R> Codec<Map.Entry<L, R>> makeSimpleEntryCodec(MapCodec<L> lhs, MapCodec<R> rhs) {
		return RecordCodecBuilder.create(
				instance -> instance.group(
						lhs.forGetter(Map.Entry::getKey),
						rhs.forGetter(Map.Entry::getValue)
				).apply(instance, AbstractMap.SimpleEntry::new)
		);
	}

	/**
	 * An alternative to {@link Codec#unboundedMap(Codec, Codec)} which is encoded as a list internally.
	 * This means that non-string keys are valid.
	 * To use a non-map codec, simply give it a key using {@link Codec#fieldOf(String)}.
	 * Warning, both map-codecs are encoded in the same map.
	 * If both map-codecs target the same key, you may want to use {@link MapCodec#fieldOf(String)}.
	 * When deserializing, warnings will be logged for any duplicate values, regardless of the map type used.
	 * This function is meant for persistent maps.
	 * @param keyCodec The key codec.
	 * @param valueCodec The value codec.
	 * @param mapBase An empty map of the type you want.
	 * @return The codec.
	 * @param <K> The key type.
	 * @param <V> The value type.
	 */
	public static <K, V> Codec<PMap<K, V>> createListSerializedPMap(
			MapCodec<K> keyCodec, MapCodec<V> valueCodec,
			PMap<K, V> mapBase
	) {
		return createListSerializedMap(
				keyCodec, valueCodec, () -> mapBase,
				(map, entry) -> {
					return map.plus(entry.getKey(), entry.getValue());
				},
				(lhs, rhs) -> {
					return rhs.plusAll(lhs);
				}
		);
	}

	/**
	 * An alternative to {@link Codec#unboundedMap(Codec, Codec)} which is encoded as a list internally.
	 * This means that non-string keys are valid.
	 * To use a non-map codec, simply give it a key using {@link Codec#fieldOf(String)}.
	 * Warning, both map-codecs are encoded in the same map.
	 * If both map-codecs target the same key, you may want to use {@link MapCodec#fieldOf(String)}.
	 * When deserializing, warnings will be logged for any duplicate values, regardless of the map type used.
	 * This function is meant for mutable maps.
	 * @param keyCodec The key codec.
	 * @param valueCodec The value codec.
	 * @param mapBase A generator which generates an empty map of the type you want.
	 * @return The codec.
	 * @param <K> The key type.
	 * @param <V> The value type.
	 * @throws UnsupportedOperationException If mapBase does not provide mutable maps.
	 */
	public static <K, V> Codec<Map<K, V>> createListSerializedMap(
			MapCodec<K> keyCodec, MapCodec<V> valueCodec,
			Supplier<Map<K, V>> mapBase
	) {
		return createListSerializedMap(
				keyCodec, valueCodec, mapBase,
				(map, entry) -> {
					map.put(entry.getKey(), entry.getValue());
					return map;
				},
				(lhs, rhs) -> {
					rhs.putAll(lhs);
					return rhs;
				}
		);
	}

	/**
	 * An alternative to {@link Codec#unboundedMap(Codec, Codec)} which is encoded as a list internally.
	 * This means that non-string keys are valid.
	 * To use a non-map codec, simply give it a key using {@link Codec#fieldOf(String)}.
	 * Warning, both map-codecs are encoded in the same map.
	 * If both map-codecs target the same key, you may want to use {@link MapCodec#fieldOf(String)}.
	 * When deserializing, warnings will be logged for any duplicate values, regardless of the map type used.
	 *
	 * Note, you probably don't want to use this function unless you're working with a specialized persistent map.
	 * {@link METACodecs#createListSerializedMap(MapCodec, MapCodec, Supplier)} and {@link METACodecs#createListSerializedPMap(MapCodec, MapCodec, PMap)}
	 * should have you covered in most cases.
	 * @param keyCodec The key codec.
	 * @param valueCodec The value codec.
	 * @param mapBase A generator which generates an empty map of the type you want.
	 * @param appender Specialized function for adding each entry.
	 * @param combiner Specialized function for merging two maps.
	 * @return The codec.
	 * @param <K> The key type.
	 * @param <V> The value type.
	 * @param <M> The map type.
	 */
	public static <K, V, M extends Map<K, V>> Codec<M> createListSerializedMap(
			MapCodec<K> keyCodec, MapCodec<V> valueCodec,
			Supplier<M> mapBase,
			BiFunction<M, Map.Entry<K, V>, M> appender,
			BiFunction<M, M, M> combiner
	) {
		return makeSimpleEntryCodec(keyCodec, valueCodec).listOf().comapFlatMap(
				list -> {
					Set<K> duplicateKeys = new HashSet<>();
					var result = list.stream().reduce(
							mapBase.get(), (map, entry) -> {
								if (map.containsKey(entry.getKey())) {
									duplicateKeys.add(entry.getKey());
									return map;
								}
								return appender.apply(map, entry);
							}, (lhs, rhs) -> {
								for (var k : lhs.keySet()) {
									if (rhs.containsKey(k)) {
										duplicateKeys.add(k);
									}
								}
								return combiner.apply(lhs, rhs);
							}
					);
					if (duplicateKeys.isEmpty()) {
						return DataResult.success(result);
					} else {
						return DataResult.error(
								() -> "Duplicate keys found: " + duplicateKeys.stream().map(Objects::toString).collect(Collectors.joining(",")),
								result
						);
					}
				},
				map -> map.entrySet().stream().toList()
		);
	}


	/**
	 * Creates a codec for the given enum class.
	 * Note, this is only meant for enums that already exist.
	 * If you're making your own enum, please make it {@link net.minecraft.util.StringRepresentable} instead.
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
				METACodecs::multimapToMap
		);
	}

	public static <K, V> MapCodec<Multimap<K, V>> simpleMultimap(
			Codec<K> keyCodec, Codec<V> valueCodec, Keyable keys, Supplier<Multimap<K, V>> multimapCreator
	) {
		return Codec.simpleMap(keyCodec, valueCodec.listOf(), keys).xmap(
				map -> mapToMultimap(map, multimapCreator),
				METACodecs::multimapToMap
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
		public static final Codec<Portal> PORTAL_CODEC = BuiltInRegistries.BLOCK.byNameCodec().flatXmap(
				block -> block instanceof Portal p ? DataResult.success(p) : DataResult.error(() -> block + " is not a portal"),
				portal -> portal instanceof Block b ? DataResult.success(b) : DataResult.error(() -> portal + " is not a block")
		);
	}

}
