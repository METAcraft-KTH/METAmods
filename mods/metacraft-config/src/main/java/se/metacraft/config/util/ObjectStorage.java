package se.metacraft.config.util;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import se.metacraft.lib.METAcraftLib;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;
import se.metacraft.config.METAConfig;
import se.metacraft.config.comments.codecs.MapCodecWithComments;
import se.metacraft.config.comments.ops.OpsWithComments;
import se.metacraft.config.parser.CodecParser;
import se.metacraft.config.parser.MetadataKey;
import se.metacraft.config.parser.metadata.ContainerType;
import se.metacraft.config.parser.result.AbstractCodecResult;

/**
 * Allows you to store objects in raw form and parse them at a later point.
 * This is useful whenever you want to use Codecs dependent on {@link RegistryOps} in a context where the required registries are not provided.
 * You use {@link ObjectStorage#createCodec(Codec, boolean)} with the codec you want to use.
 * If needed, you can construct values manually with {@link ObjectStorage#fromValue(Codec, Object, boolean)}, one of its variants,
 * or {@link ObjectStorage#fromData(Codec, Object, boolean)}.
 * To generate the result, use {@link ObjectStorage#parse(HolderLookup.Provider)}. The result will be cached, so feel free to call this multiple times.
 * @param <T> The type of the object.
 */
public class ObjectStorage<T> {

	private static final Supplier<HolderLookup.Provider> DEFAULT_LOOKUP = Suppliers.memoize(VanillaRegistries::createWorldLookup);

	private final Object rawData;
	private final Codec<T> codec;
	private DataResult<T> value;

	private static final Map<ObjectStorage<?>, Unit> RELOAD_CHECK = new WeakHashMap<>();
	private static final Map<ObjectStorage<?>, Unit> SHUTDOWN_CHECK = new WeakHashMap<>();

	static {
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, manager, success) -> {
			if (success) {
				for (var v : RELOAD_CHECK.keySet()) {
					v.value = null;
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			for (var v : SHUTDOWN_CHECK.keySet()) {
				v.value = null;
			}
		});
	}

	protected ObjectStorage(Codec<T> codec, Object data, T value, boolean refreshOnReload) {
		this.codec = codec;
		this.rawData = data;
		this.value = value != null ? DataResult.success(value) : null;
		if (refreshOnReload) {
			RELOAD_CHECK.put(this, Unit.INSTANCE);
		}
		SHUTDOWN_CHECK.put(this, Unit.INSTANCE);
	}

	/**
	 * Creates an object storage with the given value. Useful for default values.
	 * @param codec The codec to parse.
	 * @param value A function creating the value from the default registries.
	 * @param refreshOnReload If true, the cached value will be cleared whenever the server reloads.
	 * @return The ObjectStorage value.
	 * @param <T> The type of the result.
	 */
	public static <T> ObjectStorage<T> fromValueWithDefaultOps(Codec<T> codec, Function<HolderLookup.Provider, T> value, boolean refreshOnReload) {
		return fromValue(codec, value.apply(DEFAULT_LOOKUP.get()), refreshOnReload);
	}

	/**
	 * Creates an object storage with the given value. Useful for default values.
	 * @param codec The codec to parse.
	 * @param value The value the ObjetStorage should contain.
	 * @param lookup The registry lookup to use when encoding the given value.
	 * @param refreshOnReload If true, the cached value will be cleared whenever the server reloads.
	 * @return The ObjectStorage value.
	 * @param <T> The type of the result.
	 */
	public static <T> ObjectStorage<T> fromValue(Codec<T> codec, T value, HolderLookup.Provider lookup, boolean refreshOnReload) {
		return fromValue(codec, value, lookup.createSerializationContext(JavaOps.INSTANCE), refreshOnReload);
	}

	/**
	 * Creates an object storage with the given value. Useful for default values.
	 * @param codec The codec to parse.
	 * @param value The value the ObjetStorage should contain.
	 * @param refreshOnReload If true, the cached value will be cleared whenever the server reloads.
	 * @return The ObjectStorage value.
	 * @param <T> The type of the result.
	 */
	public static <T> ObjectStorage<T> fromValue(Codec<T> codec, T value, boolean refreshOnReload) {
		return fromValue(codec, value, DEFAULT_LOOKUP.get(), refreshOnReload);
	}

	protected static <T> ObjectStorage<T> fromValue(Codec<T> codec, T value, RegistryOps<?> dataCreator, boolean refreshOnReload) {
		return new ObjectStorage<>(
				codec,
				codec.encodeStart(dataCreator, value).resultOrPartial(
						METAcraftLib.LOGGER::error
				).orElse(null),
				value, refreshOnReload
		);
	}

	/**
	 * Creates an object storage from the given raw data.
	 * @param codec The codec to use for decoding the value.
	 * @param data The raw data. Should be parsable with {@link JavaOps#INSTANCE}.
	 * @param refreshOnReload If true, the cached value will be cleared whenever the server reloads.
	 * @return The ObjectStorage value.
	 * @param <T> The type of the result.
	 */
	public static <T> ObjectStorage<T> fromData(Codec<T> codec, Object data, boolean refreshOnReload) {
		return new ObjectStorage<>(codec, data, null, refreshOnReload);
	}

	/**
	 * Creates a codec for an object storage for the given codec.
	 * @param codec The codec to parse.
	 * @param refreshOnReload If true, the cached value will be cleared whenever the server reloads.
	 * @return The ObjectStorage codec.
	 * @param <T> The type of the result.
	 */
	public static <T> Codec<ObjectStorage<T>> createCodec(Codec<T> codec, boolean refreshOnReload) {
		if (codec instanceof MapCodec.MapCodecCodec<T>(MapCodec<T> c)) {
			return createCodec(c, refreshOnReload).codec();
		}
		return new Codec<>() {
			@Override
			public <S> DataResult<Pair<ObjectStorage<T>, S>> decode(DynamicOps<S> ops, S input) {
				return DataResult.success(Pair.of(
						ObjectStorage.fromData(codec, ops.convertTo(JavaOps.INSTANCE, input), refreshOnReload), input
				));
			}

			@Override
			public <S> DataResult<S> encode(ObjectStorage<T> input, DynamicOps<S> ops, S prefix) {
				return input.getRawData(ops, prefix);
			}

			@Override
			public String toString() {
				return "ObjectStorage[" + codec + "]";
			}
		};
	}

	/**
	 * Creates a codec for an object storage for the given codec.
	 * @param codec The codec to parse.
	 * @param refreshOnReload If true, the cached value will be cleared whenever the server reloads.
	 * @return The ObjectStorage codec.
	 * @param <T> The type of the result.
	 */
	public static <T> MapCodec<ObjectStorage<T>> createCodec(MapCodec<T> codec, boolean refreshOnReload) {
		MapCodec<ObjectStorage<T>> returnCodec = new MapCodec<>() {
			@Override
			public <S> RecordBuilder<S> encode(ObjectStorage<T> input, DynamicOps<S> ops, RecordBuilder<S> prefix) {
				var data = input.getRawData(ops);
				if (data.isError()) {
					prefix.withErrorsFrom(data);
				}
				data.resultOrPartial().ifPresent(r -> {
					var map = ops.getMap(r);
					if (map.isError()) {
						prefix.withErrorsFrom(map);
					}
					map.resultOrPartial().map(MapLike::entries).orElse(Stream.of()).forEach(entry -> {
						prefix.add(entry.getFirst(), entry.getSecond());
					});
				});
				return prefix;
			}

			@Override
			public <S> DataResult<ObjectStorage<T>> decode(DynamicOps<S> ops, MapLike<S> input) {
				Map<Object, Object> map = new HashMap<>();
				input.entries().forEach(entry -> {
					map.put(ops.convertTo(JavaOps.INSTANCE, entry.getFirst()), ops.convertTo(JavaOps.INSTANCE, entry.getSecond()));
				});
				return DataResult.success(
						ObjectStorage.fromData(codec.codec(), map, refreshOnReload)
				);
			}

			@Override
			public <S> Stream<S> keys(DynamicOps<S> ops) {
				return codec.keys(ops);
			}

			@Override
			public String toString() {
				return "ObjectStorage[" + codec + "]";
			}
		};
		var comments = CodecParser.parse(codec, null).nestedMetadata(MetadataKey.COMMENTS);
		if (comments.isPresent()) {
			return new MapCodecWithComments<>(returnCodec, comments.get().comments(), Optional.empty());
		}
		return returnCodec;
	}

	public <R> DataResult<R> getRawData(DynamicOps<R> ops) {
		return getRawData(ops, ops.empty());
	}

	private static <R> R applyComments(AbstractCodecResult codec, DynamicOps<R> ops, R result) {
		if (ops instanceof OpsWithComments<R> comments) {
			var container = codec.nestedMetadata(MetadataKey.CONTAINER);
			if (container.isPresent()) {
				var containerCodec = codec.getContainer(container.get().type());
				if (container.get().type() == ContainerType.RECORD) {
					var map = ops.getMap(result);
					if (map.isSuccess()) {
						for (var component : codec.nestedComponents()) {
							var name = component.nestedMetadata(MetadataKey.NAMED_FIELD);
							if (name.isPresent()) {
								var subValue = map.getOrThrow().get(name.get().name());
								if (subValue == null) continue;
								var withComments = applyComments(component, ops, subValue);
								if (subValue != comments) {
									result = ops.mergeToMap(
										result, ops.createString(name.get().name()), withComments
									).resultOrPartial(
										METAConfig.LOGGER::error
									).orElse(result);
								}
							}
						}
					}
				}
				if (container.get().type() == ContainerType.MAP) {
					var map = ops.getMap(result);
					if (map.isSuccess()) {
						var value = codec.nestedComponents().getLast();
						for (var entry : (Iterable<Pair<R, R>>) map.getOrThrow().entries()::iterator) {
							var newValue = applyComments(value, ops, entry.getSecond());
							if (newValue != entry.getSecond()) {
								result = ops.mergeToMap(
									result, entry.getFirst(), entry.getSecond()
								).resultOrPartial(
									METAConfig.LOGGER::error
								).orElse(result);
							}
						}
					}
				}
				if (container.get().type() == ContainerType.DISPATCHED_MAP) {
					var map = ops.getMap(result);
					if (map.isSuccess()) {
						var type = codec.nestedComponents().getFirst();
						var data = codec.getContainerData(ContainerType.DISPATCHED_MAP).orElseThrow();
						for (var entry : (Iterable<Pair<R, R>>) map.getOrThrow().entries()::iterator) {
							var parsed = type.codec().parse(ops, entry.getFirst()).resultOrPartial(
								METAConfig.LOGGER::error
							);
							if (parsed.isEmpty()) continue;
							var c = data.valueCodecFunction().apply(
								parsed.get()
							);
							var newValue = applyComments(CodecParser.parse(c, null), ops, entry.getSecond());
							if (newValue != entry.getSecond()) {
								result = ops.mergeToMap(
									result, entry.getFirst(), entry.getSecond()
								).resultOrPartial(
									METAConfig.LOGGER::error
								).orElse(result);
							}
						}
					}
				}
				if (container.get().type() == ContainerType.DISPATCHED_EITHER) {
					var map = ops.getMap(result);
					if (map.isSuccess()) {
						var data = codec.getContainerData(ContainerType.DISPATCHED_EITHER).orElseThrow();
						var key = containerCodec.orElse(codec).codec().parse(ops, result).flatMap(
							k -> data.keyByValue().apply(k)
						).resultOrPartial(METAConfig.LOGGER::error);
						if (key.isPresent()) {
							var decoder = data.decoderByKey().apply(key.get()).resultOrPartial(METAConfig.LOGGER::error);
							if (decoder.isPresent() && decoder.get() instanceof MapCodec<?> c) {
								var newValue = applyComments(CodecParser.parse(c, null), ops, result);
								if (newValue != result) {
									result = newValue;
								}
							}
						}
					}
				}
				if (container.get().type() == ContainerType.LIST) {
					var list = ops.getStream(result);
					if (list.isSuccess()) {
						var type = codec.nestedComponents().getFirst();
						List<R> newList = new ArrayList<>();
						for (var entry : (Iterable<R>) list.getOrThrow()::iterator) {
							var newValue = applyComments(type, ops, entry);
							newList.add(newValue);
						}
						result = ops.createList(newList.stream());
					}
				}
			}
			var commentContainer = codec.nestedMetadata(MetadataKey.COMMENTS);
			if (commentContainer.isPresent()) {
				if (ops.getMap(result).isSuccess()) {
					result = comments.metacraft$withComments(result, commentContainer.get().getMapFor(ops)).resultOrPartial(
						METAConfig.LOGGER::error
					).orElse(result);
				}
			}
		}
		return result;
	}

	public <R> DataResult<R> getRawData(DynamicOps<R> ops, R prefix) {
		var result = JavaOps.INSTANCE.convertTo(ops, rawData);
		var primitive = ops.mergeToPrimitive(prefix, result);
		if (primitive.isSuccess()) {
			return primitive.map(
				r -> applyComments(CodecParser.parse(codec, null), ops, r)
			);
		}
		var mapResult = ops.getMap(result).flatMap(
				map -> ops.mergeToMap(prefix, map)
		);
		if (mapResult.isSuccess()) {
			return mapResult.map(
				r -> applyComments(CodecParser.parse(codec, null), ops, r)
			);
		}
		var listResult = ops.mergeToList(prefix, result);
		if (listResult.isSuccess()) {
			return listResult.map(
				r -> applyComments(CodecParser.parse(codec, null), ops, r)
			);
		}
		return primitive;
	}

	public DataResult<T> parse(UnaryOperator<DynamicOps<Object>> registryOpsGetter) {
		if (value == null) {
			try {
				value = codec.parse(registryOpsGetter.apply(JavaOps.INSTANCE), rawData);
			} catch (ClassCastException err) {
				return DataResult.error(() -> "Error ops conversion failed! This shouldn't happen!");
			}
		}
		return value;
	}

	public <S> DataResult<T> parse(HolderLookup.Provider lookup) {
		return parse(lookup::createSerializationContext);
	}

}
