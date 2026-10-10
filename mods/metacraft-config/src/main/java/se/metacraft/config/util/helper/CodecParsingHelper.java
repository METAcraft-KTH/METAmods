package se.metacraft.config.util.helper;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapDecoder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import org.pcollections.PMap;
import org.pcollections.PSet;
import org.pcollections.PVector;
import org.pcollections.TreePVector;
import se.metacraft.config.event.CodecParseEvents;
import se.metacraft.config.mixin.codec.*;
import se.metacraft.config.parser.Metadata;
import se.metacraft.config.parser.MetadataKey;
import se.metacraft.config.parser.CodecParser;
import se.metacraft.config.parser.MetadataMap;
import se.metacraft.config.parser.metadata.DefaultValue;
import se.metacraft.config.parser.metadata.Entries;
import se.metacraft.config.parser.metadata.Remainder;
import se.metacraft.config.parser.result.AbstractCodecResult;
import se.metacraft.config.parser.result.CodecResult;
import se.metacraft.config.parser.result.MapCodecResult;

import java.util.Optional;

public class CodecParsingHelper {

	private static PVector<AbstractCodecResult> parseFlattenedComponentsOrSelf(
		MapCodec<?> mapCodec, HolderLookup.Provider lookup
	) {
		var flattened = parseFlattenedComponents(mapCodec, lookup);
		if (flattened.isEmpty()) {
			return TreePVector.singleton(CodecParser.parse(mapCodec, lookup));
		} else {
			return flattened;
		}
	}

	private static PVector<AbstractCodecResult> parsePair(
		MapCodec<?> mapCodec, HolderLookup.Provider lookup
	) {
		PVector<AbstractCodecResult> elements = TreePVector.empty();
		if (mapCodec instanceof PairMapCodecAccessor pair) {
			elements = elements.plusAll(parseFlattenedComponentsOrSelf(pair.getFirst(), lookup));
			elements = elements.plusAll(parseFlattenedComponentsOrSelf(pair.getSecond(), lookup));
		}
		if (mapCodec instanceof MappedMapCodecAccessor mapped) {
			elements = elements.plusAll(parsePair(mapped.getParent(), lookup));
		}
		if (mapCodec instanceof SimpleMapCodecAccessor simple) {
			if ( // Codecs defined via xmap
				simple.getDecoder() instanceof MappedMapDecoderAccessor mapped &&
					mapped.getParent() instanceof MapCodec<?> parent
			) {
				elements = elements.plusAll(parsePair(parent, lookup));
			}
		}
		return elements;
	}

	public static PVector<AbstractCodecResult> parseFlattenedComponents(
		MapCodec<?> mapCodec, HolderLookup.Provider lookup
	) {
		PVector<AbstractCodecResult> elements = TreePVector.empty();
		elements = elements.plusAll(parsePair(mapCodec, lookup));
		if (mapCodec instanceof RecordCodecBuilderAccessor.MapCodec builder) {
			elements = elements.plusAll(parseRecordCodecBuilder(
				(RecordCodecBuilderAccessor) (Object) builder.getBuilder(), lookup)
			);
		}
		return elements;
	}

	private static PVector<AbstractCodecResult> parseRecordCodecMapDecoder(
		MapDecoder<?> decoder, HolderLookup.Provider lookup
	) {
		PVector<AbstractCodecResult> subMapCodecs = TreePVector.empty();
		for (var field : decoder.getClass().getDeclaredFields()) {
			field.setAccessible(true);
			try {
				var value = field.get(decoder);
				if (value instanceof MapDecoder<?> nextDecoder) {
					subMapCodecs = subMapCodecs.plusAll(parseRecordCodecMapDecoder(nextDecoder, lookup));
				}
				if (value instanceof RecordCodecBuilderAccessor subBuilder) {
					if (subBuilder.getDecoder() instanceof MapCodec<?> subCodec) {
						subMapCodecs = subMapCodecs.plusAll(parseFlattenedComponentsOrSelf(subCodec, lookup));
					} else if (field.getName().equals(CodecInternalsHelper.LAMBDA_WITH_FUNCTION)) {
						subMapCodecs = subMapCodecs.plusAll(parseRecordCodecBuilder(subBuilder, lookup));
					}
				}
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}
		return subMapCodecs;
	}

	private static PVector<AbstractCodecResult> parseRecordCodecBuilder(
		RecordCodecBuilderAccessor recordCodecBuilder, HolderLookup.Provider lookup
	) {
		return parseRecordCodecMapDecoder(recordCodecBuilder.getDecoder(), lookup);
	}

	public static <T> MetadataMap metadataFromRegistry(HolderLookup.RegistryLookup<T> registry) {
		return MetadataMap.from(
			Entries.from(
				registry.listElements().map(
					e -> new Entries.Entry(e.key().identifier().toString(), e)
				)
			),
			registry instanceof Registry<T> reg ? reg.getAny().map(DefaultValue::new).orElse(null) : null
		);
	}

	private static MetadataMap postProcessMappingsInternal(
		Codec<?> nextLevel, AbstractCodecResult underlying,
		PVector<Object> parameters, HolderLookup.Provider lookup
	) {
		MetadataMap metadata = MetadataMap.EMPTY;
		metadata = CodecParseEvents.POST_PROCESS.invoker().modify(metadata, nextLevel, underlying, parameters, lookup);
		if (!parameters.isEmpty()) {
			metadata = metadata.plus(new Remainder(parameters));
		}
		return metadata;
	}

	public static AbstractCodecResult postProcessMappings(
		Codec<?> nextLevel, AbstractCodecResult underlying, PVector<Object> parameters, HolderLookup.Provider lookup
	) {
		return CodecResult.createMapped(
			nextLevel, postProcessMappingsInternal(nextLevel, underlying, parameters, lookup), underlying
		);
	}

	public static AbstractCodecResult postProcessMappings(
		MapCodec<?> nextLevel, AbstractCodecResult underlying, PVector<Object> parameters, HolderLookup.Provider lookup
	) {
		return MapCodecResult.createMapped(
			nextLevel, postProcessMappingsInternal(nextLevel.codec(), underlying, parameters, lookup), underlying
		);
	}

}
