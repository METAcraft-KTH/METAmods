package se.metacraft.config.util.helper;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.PrimitiveCodec;
import net.minecraft.core.HolderLookup;
import org.jspecify.annotations.Nullable;
import org.pcollections.*;
import se.metacraft.config.event.CodecDefaultValueEvent;
import se.metacraft.config.mixin.codec.*;
import se.metacraft.config.parser.CodecParser;
import se.metacraft.config.parser.MetadataKey;
import se.metacraft.config.parser.metadata.ContainerType;
import se.metacraft.config.parser.metadata.DefaultValue;
import se.metacraft.config.parser.result.AbstractCodecResult;
import se.metacraft.config.parser.result.CodecResult;

import java.util.*;

public class CodecInternalsHelper {

	public static PVector<Object> getVariablesWithin(
		Object object, int maxDepth
	) {
		PVector<Object> elements = TreePVector.empty();
		try {
			for (var subField : object.getClass().getDeclaredFields()) {
				subField.setAccessible(true);
				var value = subField.get(object);
				if (value != null) {
					if (maxDepth > 1) {
						var modified = elements.plusAll(getVariablesWithin(value, maxDepth-1));
						if (modified != elements) {
							elements = modified;
						} else {
							elements = elements.plus(value);
						}
					} else {
						elements = elements.plus(value);
					}
				}
			}
		} catch (IllegalAccessException err) {
			throw new RuntimeException(err);
		}
		return elements;
	}

	public static final String LAMBDA_WITH_FUNCTION = "val$function";

	public static PVector<Object> getVariablesInTheMappings(
		Object mapped, int maxDepth, String fieldName
	) {
		try {
			var functionField = mapped.getClass().getDeclaredField(fieldName);

			functionField.setAccessible(true);
			var function = functionField.get(mapped);
			return getVariablesWithin(function, maxDepth);
		} catch (NoSuchFieldException err) {
			return TreePVector.empty();
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public static <T, O> DataResult<O> forceEncode(Encoder<T> codec, DynamicOps<O> ops, Object object) {
		try {
			//noinspection unchecked
			return codec.encodeStart(ops, (T) object);
		} catch (ClassCastException err) {
			return DataResult.error(err::getMessage);
		}
	}

	public static <O> DataResult<O> convert(Encoder<?> encoder, Decoder<O> decoder, Object object, Object prefix, HolderLookup.@Nullable Provider lookup) {
		try {
			var ops = lookup != null ? lookup.createSerializationContext(JavaOps.INSTANCE): JavaOps.INSTANCE;
			return forceEncode(encoder, ops, object).flatMap(
				v -> decoder.parse(ops, mergeWithPrefix(v, prefix, ops))
			);
		} catch (IllegalArgumentException err) {
			return DataResult.error(err::getMessage);
		}
	}

	public static <O> DataResult<O> convert(Encoder<?> encoder, Decoder<O> decoder, Object object, HolderLookup.@Nullable Provider lookup) {
		return convert(encoder, decoder, object, null, lookup);
	}

	public static boolean isCodecValidUncasted(Codec<?> codec, Object object, HolderLookup.@Nullable Provider lookup) {
		return convert(codec, codec, object, lookup).isSuccess();
	}


	public static boolean isOneOfPrimitiveCodecs(Codec<?> lhs, HolderLookup.Provider lookup, PrimitiveCodec<?>... args) {
		return Arrays.stream(args).anyMatch(arg -> isPrimitiveCodec(lhs, arg, lookup));
	}

	public static boolean isPrimitiveCodec(Codec<?> lhs, PrimitiveCodec<?> rhs, HolderLookup.Provider lookup) {
		boolean found = false;
		for (var child : CodecParser.parse(lhs, lookup).thisAndAllUnderlying()) {
			if (child.underlying().isEmpty()) {
				if (child instanceof CodecResult r && r.codec() == rhs) {
					found = true;
				} else if (!child.isEmpty()) {
					return false;
				}
			}
		}
		return found;
	}

	public static <T> Optional<T> getUnderlyingCodec(Codec<?> lhs, Class<T> codecClass, HolderLookup.Provider lookup) {
		return getUnderlyingCodec(CodecParser.parse(lhs, lookup), codecClass);
	}

	public static <T> Optional<T> getUnderlyingCodec(AbstractCodecResult codec, Class<T> codecClass) {
		for (var child : codec.thisAndAllUnderlying()) {
			if (child.mapCodec().isPresent() && codecClass.isInstance(child.mapCodec().get())) {
				return Optional.of(codecClass.cast(child.mapCodec().get()));
			}
			if (codecClass.isInstance(child.codec())) {
				return Optional.of(codecClass.cast(child.codec()));
			}

		}
		return Optional.empty();
	}

	public static <T> Optional<T> getUnderlyingCodec(MapCodec<?> lhs, Class<T> codecClass, HolderLookup.Provider lookup) {
		return getUnderlyingCodec(CodecParser.parse(lhs, lookup), codecClass);
	}


	public static Object mergeWithPrefix(Object value, Object prefix, DynamicOps<Object> ops) {
		if (prefix instanceof Map<?,?>) {
			//noinspection unchecked
			var result = ops.mergeToMap(value, (Map<Object, Object>) prefix);
			if (result.hasResultOrPartial()) {
				value = result.getPartialOrThrow();
			}
		}
		if (prefix instanceof List<?>) {
			//noinspection unchecked
			var result = ops.mergeToList(value, (List<Object>) prefix);
			if (result.hasResultOrPartial()) {
				value = result.getPartialOrThrow();
			}
		}
		return value;
	}

	private static DataResult<Object> objectCast(DataResult<?> result) {
		//noinspection unchecked
		return (DataResult<Object>) result;
	}

	public static List<AbstractCodecResult> getNamedElements(
		AbstractCodecResult element
	) {
		if (element.isContainerType(ContainerType.RECORD)) {
			return element.components();
		}
		if (element.underlying().isEmpty()) return List.of();
		if (!element.hasContainerType(ContainerType.RECORD) && element.metadata(MetadataKey.NAMED_FIELD).isPresent()) {
			return List.of(element);
		}
		return getNamedElements(element.underlying());
	}

	public static AbstractCodecResult getUnnamed(AbstractCodecResult named) {
		return named.getUnderlying(
			codec -> codec.nestedMetadata(MetadataKey.NAMED_FIELD).isEmpty()
		).orElse(named);
	}

	public static DataResult<Object> defaultValue(
		AbstractCodecResult element, HolderLookup.Provider lookup
	) {
		return element.nestedMetadata(MetadataKey.DEFAULT_VALUE).map(DefaultValue::value).map(DataResult::success).orElseGet(() -> {
			var result = CodecDefaultValueEvent.EVENT.invoker().tryGetDefaultValue(element, lookup);
			return result.map(
				CodecInternalsHelper::objectCast
			).orElseGet(
				() -> DataResult.error(() -> "Does not know how to find the default value for " + element.codecName())
			);
		});
	}

}
