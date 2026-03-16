package nu.metacraft.lib.config;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import nu.metacraft.lib.METAcraftLib;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;

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

	private static final Supplier<HolderLookup.Provider> DEFAULT_LOOKUP = Suppliers.memoize(VanillaRegistries::createLookup);

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
		return new MapCodec<>() {
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
	}

	public <R> DataResult<R> getRawData(DynamicOps<R> ops) {
		return getRawData(ops, ops.empty());
	}

	public <R> DataResult<R> getRawData(DynamicOps<R> ops, R prefix) {
		var result = JavaOps.INSTANCE.convertTo(ops, rawData);
		var primitive = ops.mergeToPrimitive(prefix, result);
		if (primitive.isSuccess()) {
			return primitive;
		}
		var mapResult = ops.getMap(result).flatMap(
				map -> ops.mergeToMap(prefix, map)
		);
		if (mapResult.isSuccess()) {
			return mapResult;
		}
		var listResult = ops.mergeToList(prefix, result);
		if (listResult.isSuccess()) {
			return listResult;
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
