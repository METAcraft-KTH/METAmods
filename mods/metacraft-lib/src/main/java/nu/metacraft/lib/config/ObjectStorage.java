package nu.metacraft.lib.config;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JavaOps;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.config.container.ConfigContainerBase;
import nu.metacraft.lib.config.container.ReloadFunction;
import nu.metacraft.lib.config.container.ServerAware;
import nu.metacraft.lib.mixin.AccessorServerDynamicRegistryType;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;

/**
 * Allows you to store objects in raw form and parse them at a later point.
 * This is meant to be used in conjunction with {@link ServerAware#wrap(ConfigContainerBase, BiFunction, ReloadFunction)}
 * to allow deserialization of objects dependent on {@link HolderLookup.Provider}.
 *
 * You use {@link ObjectStorage#createCodec(Codec)} with the codec you want to use.
 * If needed, you can construct values manually with {@link ObjectStorage#fromValue(Codec, T)}
 * or in special cases {@link ObjectStorage#fromData(Codec, Object)}
 * To generate the result, use {@link ObjectStorage#parse(HolderLookup.Provider)}.
 * @param <T> The type of the object.
 */
public class ObjectStorage<T> {

	private static final Supplier<HolderLookup.Provider> DEFAULT_LOOKUP = Suppliers.memoize(VanillaRegistries::createLookup);

	private final Object rawData;
	private final Codec<T> codec;

	protected ObjectStorage(Codec<T> codec, Object data) {
		this.codec = codec;
		this.rawData = data;
	}

	public static <T, S> ObjectStorage<T> fromValueWithDefaultOps(Codec<T> codec, Function<HolderLookup.Provider, T> value) {
		return fromValue(codec, value.apply(DEFAULT_LOOKUP.get()));
	}

	public static <T, S> ObjectStorage<T> fromValue(Codec<T> codec, T value) {
		return fromValue(codec, value, DEFAULT_LOOKUP.get().createSerializationContext(JavaOps.INSTANCE));
	}

	protected static <T, S> ObjectStorage<T> fromValue(Codec<T> codec, T value, RegistryOps<S> dataCreator) {
		return new ObjectStorage<>(
				codec,
				codec.encodeStart(dataCreator, value).resultOrPartial(
						METAcraftLib.LOGGER::error
				).orElse(null)
		);
	}

	public static <T> ObjectStorage<T> fromData(Codec<T> codec, Object data) {
		return new ObjectStorage<>(codec, data);
	}

	public static <T> Codec<ObjectStorage<T>> createCodec(Codec<T> codec) {
		return new Codec<>() {
			@Override
			public <S> DataResult<Pair<ObjectStorage<T>, S>> decode(DynamicOps<S> ops, S input) {
				return DataResult.success(Pair.of(
						ObjectStorage.fromData(codec, ops.convertTo(JavaOps.INSTANCE, input)), input
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
		try {
			return codec.parse(registryOpsGetter.apply(JavaOps.INSTANCE), rawData);
		} catch (ClassCastException err) {
			return DataResult.error(() -> "Error ops conversion failed! This shouldn't happen!");
		}
	}

	public <S> DataResult<T> parse(HolderLookup.Provider lookup) {
		return parse(lookup::createSerializationContext);
	}

}
