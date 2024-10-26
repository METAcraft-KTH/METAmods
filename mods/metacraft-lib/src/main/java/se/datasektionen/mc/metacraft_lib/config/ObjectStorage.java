package se.datasektionen.mc.metacraft_lib.config;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JavaOps;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;
import se.datasektionen.mc.metacraft_lib.mixin.AccessorServerDynamicRegistryType;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Allows you to store objects in raw form and parse them at a later point.
 * This is meant to be used in conjunction with {@link ServerAware#parse(RegistryWrapper.WrapperLookup)}
 * to allow deserialization of objects dependent on {@link RegistryWrapper.WrapperLookup}.
 *
 * You use {@link ObjectStorage#createCodec(Codec)} with the codec you want to use.
 * If needed, you can construct values manually with {@link ObjectStorage#fromValue(Codec, T)}
 * or in special cases {@link ObjectStorage#fromData(Codec, Object)}
 * To generate the result, use {@link ObjectStorage#parse(RegistryWrapper.WrapperLookup)}.
 * @param <T> The type of the object.
 */
public class ObjectStorage<T> {

	private static final Supplier<RegistryWrapper.WrapperLookup> DEFAULT_LOOKUP = new Supplier<RegistryWrapper.WrapperLookup>() {

		private RegistryWrapper.WrapperLookup lookup;

		@Override
		public RegistryWrapper.WrapperLookup get() {
			if (lookup == null) {
				lookup = AccessorServerDynamicRegistryType.getStaticRegistryManager();
			}
			return lookup;
		}
	};

	private Object rawData;
	private final Codec<T> codec;

	protected ObjectStorage(Codec<T> codec, Object data) {
		this.codec = codec;
		this.rawData = data;
	}

	public static <T, S> ObjectStorage<T> fromValue(Codec<T> codec, T value) {
		return fromValue(codec, value, DEFAULT_LOOKUP.get().getOps(JavaOps.INSTANCE));
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

	public <S> DataResult<T> parse(RegistryWrapper.WrapperLookup lookup) {
		return parse(lookup::getOps);
	}

}
