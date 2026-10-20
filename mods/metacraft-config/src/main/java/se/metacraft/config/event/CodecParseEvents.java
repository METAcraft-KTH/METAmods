package se.metacraft.config.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.core.HolderLookup;
import org.jspecify.annotations.Nullable;
import org.pcollections.PVector;
import se.metacraft.config.BuiltinParsers;
import se.metacraft.config.parser.MetadataMap;
import se.metacraft.config.parser.result.AbstractCodecResult;
import se.metacraft.config.util.event.EventWithPhases;

import java.util.Optional;

public class CodecParseEvents {


	public static final Event<ParseCodec> PARSE_CODEC = EventWithPhases.createPrePostEventWithInternal(
		ParseCodec.class, events -> (codec, lookup) -> EventWithPhases.getOptionalResult(
			events, event -> event.parse(codec, lookup)
		),
		BuiltinParsers.ALL_CODECS
	);

	public static final Event<ParseMapCodec> PARSE_MAP_CODEC = EventWithPhases.createPrePostEventWithInternal(
		ParseMapCodec.class, events -> (codec, lookup) -> EventWithPhases.getOptionalResult(
			events, event -> event.parse(codec, lookup)
		),
		BuiltinParsers.ALL_MAP_CODECS
	);

	public static final Event<PostProcessMappings> POST_PROCESS = EventWithPhases.createPrePostEventWithInternal(
		PostProcessMappings.class, events -> (metadata, nextLevel, underlying, parameters, lookup) -> EventWithPhases.getMergedResult(
			events, event -> event.modify(metadata, nextLevel, underlying, parameters, lookup),
			MetadataMap::plusAll, MetadataMap.EMPTY
		),
		BuiltinParsers.POST_PROCESS
	);

	public interface PostProcessMappings {
		MetadataMap modify(
			MetadataMap metadata,
			Codec<?> nextLevel, AbstractCodecResult underlying,
			PVector<Object> parameters, HolderLookup.@Nullable Provider lookup
		);
	}

	public interface ParseCodec {
		Optional<AbstractCodecResult> parse(Codec<?> codec, HolderLookup.@Nullable Provider lookup);
	}

	public interface ParseMapCodec {
		Optional<AbstractCodecResult> parse(MapCodec<?> codec, HolderLookup.@Nullable Provider lookup);
	}

}
