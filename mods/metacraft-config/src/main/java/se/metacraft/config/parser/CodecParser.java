package se.metacraft.config.parser;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import org.jspecify.annotations.Nullable;
import org.pcollections.PVector;
import org.pcollections.TreePVector;
import se.metacraft.config.event.CodecParseEvents;
import se.metacraft.config.parser.result.AbstractCodecResult;
import se.metacraft.config.parser.result.CodecResult;
import se.metacraft.config.parser.result.MapCodecResult;
import se.metacraft.config.util.helper.CodecParsingHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Optional;
import java.util.function.Predicate;

public class CodecParser {

	public static AbstractCodecResult parse(MapCodec<?> codec, HolderLookup.@Nullable Provider lookup) {
		// Null while CodecParseEvents is still being initialised: making its phases' ids can initialise
		// Identifier, which builds vanilla codecs, which come back here (RecordCodecBuilderMixin).
		if (CodecParseEvents.PARSE_MAP_CODEC == null) return MapCodecResult.createMapped(codec, Metadata.noMetadata(), AbstractCodecResult.EMPTY);
		var eventBased = CodecParseEvents.PARSE_MAP_CODEC.invoker().parse(codec, lookup);
		if (eventBased.isPresent()) return eventBased.get();
		var nested = getNested(codec, lookup);
		if (nested.isPresent()) return nested.get();
		return MapCodecResult.createMapped(codec, Metadata.noMetadata(), AbstractCodecResult.EMPTY);
	}

	public static AbstractCodecResult parse(Codec<?> codec, HolderLookup.@Nullable Provider lookup) {
		if (CodecParseEvents.PARSE_CODEC == null) return CodecResult.createMapped(codec, Metadata.noMetadata(), AbstractCodecResult.EMPTY);   // see above
		var eventBased = CodecParseEvents.PARSE_CODEC.invoker().parse(codec, lookup);
		if (eventBased.isPresent()) return eventBased.get();
		var nested = getNested(codec, lookup);
		if (nested.isPresent()) return nested.get();
		return CodecResult.createMapped(codec, Metadata.noMetadata(), AbstractCodecResult.EMPTY);
	}

	private static Optional<AbstractCodecResult> getNested(Object object, HolderLookup.@Nullable Provider lookup) {
		var nested = getNestedCodecs(object);
		if (nested.size() == 1) {
			var codec = nested.getFirst();
			var parameters = getNestedNonCodecs(object);
			if (codec instanceof Codec<?> c) {
				return Optional.of(CodecParsingHelper.postProcessMappings(c, parse(c, lookup), parameters, lookup));
			} else if (codec instanceof MapCodec<?> c) {
				return Optional.of(CodecParsingHelper.postProcessMappings(c, parse(c, lookup), parameters, lookup));
			}
		}
		return Optional.empty();
	}

	private static PVector<Object> getNestedParameters(Object object, Predicate<Field> predicate) {
		var parameters = TreePVector.empty();
		for (var field : object.getClass().getDeclaredFields()) {
			if (predicate.test(field)) {
				field.setAccessible(true);
				try {
					parameters = parameters.plus(Modifier.isStatic(field.getModifiers()) ? field.get(null) : field.get(object));
				} catch (IllegalAccessException e) {
					throw new RuntimeException(e);
				}
			}
		}
		return parameters;
	}

	public static PVector<Object> getNestedCodecs(Object object) {
		return getNestedParameters(
			object,
			field -> isCodec(field.getType())
		);
	}

	private static boolean isCodec(Class<?> clazz) {
		return Codec.class.isAssignableFrom(clazz) || MapCodec.class.isAssignableFrom(clazz);
	}

	private static PVector<Object> getNestedNonCodecs(Object object) {
		return getNestedParameters(
			object,
			field -> !isCodec(field.getType())
		);
	}

}
