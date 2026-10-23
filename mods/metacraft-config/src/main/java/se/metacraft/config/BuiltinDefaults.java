package se.metacraft.config;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.DispatchedMapCodec;
import com.mojang.serialization.codecs.ListCodec;
import com.mojang.serialization.codecs.SimpleMapCodec;
import com.mojang.serialization.codecs.UnboundedMapCodec;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.data.BlockDataSource;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.random.Weighted;
import se.metacraft.lib.util.helper.DataResultHelper;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import org.pcollections.PVector;
import org.pcollections.TreePVector;
import se.metacraft.config.event.CodecDefaultValueEvent;
import se.metacraft.config.mixin.TranslatableContentsAccessor;
import se.metacraft.config.parser.CodecParser;
import se.metacraft.config.parser.MetadataKey;
import se.metacraft.config.parser.metadata.Container;
import se.metacraft.config.parser.metadata.ContainerType;
import se.metacraft.config.parser.metadata.Entries;
import se.metacraft.config.parser.result.AbstractCodecResult;
import se.metacraft.config.util.CapturedCodecs;
import se.metacraft.config.util.helper.CodecInternalsHelper;

import java.nio.ByteBuffer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public class BuiltinDefaults {

	private static <T> DataResult<?> tryParseMapCodec(
		T map, Codec<?> simple, DynamicOps<Object> ctx,
		BiFunction<T, HolderLookup.Provider, AbstractCodecResult> parser, HolderLookup.Provider lookup
	) {
		try {
			var easyPath = simple.parse(ctx, Map.of());
			if (easyPath.hasResultOrPartial()) return easyPath;
			var parsedMap = parser.apply(map, lookup);
			var keyEntry = CodecInternalsHelper.getUnnamed(parsedMap.components().getFirst());
			var valueEntry = CodecInternalsHelper.getUnnamed(parsedMap.components().getLast());
			return CodecInternalsHelper.defaultValue(keyEntry, lookup).flatMap(defaultValue ->
				CodecInternalsHelper.forceEncode(
					keyEntry.codec(),
					ctx, defaultValue
				)
			).flatMap(
				key -> CodecInternalsHelper.defaultValue(valueEntry, lookup).flatMap(
					defaultValue -> CodecInternalsHelper.forceEncode(
						valueEntry.codec(),
						ctx, defaultValue
					).flatMap(
						value -> simple.parse(
							ctx, Map.of(key, value)
						)
					)
				)
			);
		} catch (ClassCastException e) {
			return DataResult.error(e::getMessage);
		}
	}

	@SafeVarargs
	private static DataResult<String> tryFormat(DateTimeFormatter formatter, Supplier<TemporalAccessor>... accessors) {
		DataResult<String> string = null;
		for (var accessor : accessors) {
			try {
				string = DataResult.success(formatter.format(accessor.get()));
				if (string.isSuccess()) break;
			} catch (UnsupportedTemporalTypeException err) {
				if (string != null) {
					string = DataResult.error(err::getMessage);
				}
			}
		}
		return string;
	}

	private static DataResult<String> defaultString(AbstractCodecResult element) {
		if (element.getUnderlying(codec -> codec.codec() == TagParser.FLATTENED_CODEC).isPresent()) {
			return DataResult.success("{}");
		}
		if (element.getUnderlying(codec -> codec.codec() == ExtraCodecs.NON_EMPTY_STRING).isPresent()) {
			return DataResult.success("missingno");
		}
		if (element.getUnderlying(codec -> codec.codec() == ExtraCodecs.CODEPOINT).isPresent()) {
			return DataResult.success("0");
		}
		if (element.getUnderlying(codec -> codec.codec() == ExtraCodecs.UNTRUSTED_URI).isPresent()) {
			return DataResult.success("https://minecraft.wiki");
		}
		if (element.getUnderlying(codec -> codec.codec() == BlockDataSource.BLOCK_POS_CODEC).isPresent()) {
			return DataResult.success("0 0 0");
		}
		if (element.getUnderlying(codec -> codec.codec() == EntitySelector.COMPILABLE_CODEC).isPresent()) {
			return DataResult.success("@s");
		}
		if (element.getUnderlying(codec -> codec.codec() == UUIDUtil.STRING_CODEC).isPresent()) {
			return DataResult.success(new UUID(0, 0).toString());
		}
		var timeFormatter = element.nestedMetadata(MetadataKey.REMAINDER).flatMap(
			param -> param.parameters().stream().filter(
				e -> e instanceof DateTimeFormatter
			).map(e -> (DateTimeFormatter) e).findAny()
		);
		if (timeFormatter.isPresent()) {
			return tryFormat(
				timeFormatter.get(), Instant::now, Year::now,
				YearMonth::now, LocalDate::now,
				LocalTime::now, LocalDateTime::now
			);
		}
		if (element.getUnderlying(codec -> codec.codec() == ExtraCodecs.BASE64_STRING).isPresent()) {
			return DataResult.success(Base64.getEncoder().encodeToString(new byte[0]));
		}
		return DataResult.success("");
	}

	private static Object handleDefaultObject(Object object) {
		// Weighted allows a weight of 0, but some weighted lists might not allow that as the only entry.
		if (object instanceof Weighted<?>(Object value, int weight)) {
			return new Weighted<>(value, Math.max(1, weight));
		}
		return object;
	}

	private static DataResult<?> compilerHack(Object d, Container.DispatchedEither dispatchData) {
		return dispatchData.decoderByKey().apply(d);
	}

	private static Optional<DataResult<PMap<String, Object>>> handleDispatch(
		AbstractCodecResult element, PMap<String, Object> result, DynamicOps<?> ctx, HolderLookup.Provider lookup
	) {
		var eitherDispatch = element.getContainer(ContainerType.DISPATCHED_EITHER);
		if (eitherDispatch.isPresent()) {
			var name = eitherDispatch.get().components().getFirst().nestedMetadata(MetadataKey.NAMED_FIELD);
			if (name.isEmpty()) return Optional.empty();

			var k = CodecInternalsHelper.defaultValue(eitherDispatch.get().components().getFirst(), lookup);
			var dispatchData = element.getContainerData(ContainerType.DISPATCHED_EITHER).orElseThrow();
			return Optional.of(
				DataResultHelper.withPartialIfError(
					k.flatMap(key -> compilerHack(key, dispatchData).flatMap(decoder -> {
						if (decoder instanceof MapCodec<?> c) {
							return CodecInternalsHelper.forceEncode(
								eitherDispatch.get().components().getFirst().codec(), ctx, key
							).map(
								encoded -> {
									//noinspection unchecked
									return encoded instanceof Map<?, ?> ? result.plusAll((Map<String, ?>) encoded) : result;
								}
							).flatMap(
								r -> appendElements(
									CodecParser.parse(c, lookup),
									r, ctx, lookup
								)
							);
						}
						return DataResult.error(() -> decoder + " is not a MapCodec");
					})),
					result
				)
			);
		}
		return Optional.empty();
	}

	private static DataResult<PMap<String, Object>> appendElements(
		AbstractCodecResult element, PMap<String, Object> result, DynamicOps<?> ctx, HolderLookup.Provider lookup
	) {
		DataResult<PMap<String, Object>> r = DataResult.success(result);
		for (var subElement : CodecInternalsHelper.getNamedElements(element)) {
			var name = subElement.nestedMetadata(MetadataKey.NAMED_FIELD);
			if (name.isEmpty()) {
				r = r.flatMap(currentResult -> {
					var dispatch = handleDispatch(subElement, currentResult, ctx, lookup);
					return dispatch.orElseGet(() -> DataResult.success(currentResult));
				});
			}
			if (name.isEmpty() || !name.get().required()) continue;
			var type = CodecInternalsHelper.getUnnamed(subElement);
			DataResult<Object> foundDefaultValue = CodecInternalsHelper.defaultValue(type, lookup);
			r = r.flatMap(
				currentResult -> foundDefaultValue.flatMap(v ->
					CodecInternalsHelper.forceEncode(type.codec(), ctx, v)
				).map(
					encoded -> currentResult.plus(name.get().name(), encoded)
				).setPartial(currentResult)
			);
		}
		return r;
	}

	public static final CodecDefaultValueEvent BUILTIN = (element, lookup) -> {
		var recursion = element.getContainerData(ContainerType.RECURSIVE);
		if (recursion.isPresent()) {
			return Optional.of(CodecInternalsHelper.defaultValue(recursion.get().wrapped().get(), lookup));
		}

		// Force Component codec to return literal. It returns keybind otherwise.
		if (
			element.getUnderlying(
				codec -> codec.mapCodec().stream().anyMatch(
					c -> c == CapturedCodecs.CAPTURED_CODECS.get(CapturedCodecs.COMPONENT_TYPE)
				)
			).isPresent()
		) {
			return Optional.of(DataResult.success(PlainTextContents.MAP_CODEC));
		}

		if (element.mapCodec().isPresent()) {
			var mapCodec = element.mapCodec().get();
			var ctx = lookup.createSerializationContext(JavaOps.INSTANCE);

			Optional<DataResult<?>> map = CodecInternalsHelper.getUnderlyingCodec(mapCodec, SimpleMapCodec.class, lookup).map(
				codec -> tryParseMapCodec(
					codec, mapCodec.codec(), ctx, CodecParser::parse, lookup
				)
			);
			if (map.isPresent()) {
				return map;
			}

			DataResult<PMap<String, Object>> result = handleDispatch(
				element, HashTreePMap.empty(), ctx, lookup
			).orElseGet(
				() -> appendElements(
					element, HashTreePMap.empty(), ctx, lookup
				)
			);

			if (
				element.hasContainerType(ContainerType.RECORD) || element.hasContainerType(ContainerType.UNIT) ||
				element.hasContainerType(ContainerType.DISPATCHED_EITHER) || result.hasResultOrPartial()
			) {
				return Optional.of(result.flatMap(r -> mapCodec.codec().parse(ctx, r).map(BuiltinDefaults::handleDefaultObject)));
			}
			return Optional.empty();
		} else {
			var simple = element.codec();
			var ctx = lookup.createSerializationContext(JavaOps.INSTANCE);
			var list = CodecInternalsHelper.getUnderlyingCodec(simple, ListCodec.class, lookup);
			if (list.isPresent()) {
				int minSize = element.nestedMetadata(MetadataKey.RANGE).map(r -> r.min().intValue()).orElse(list.get().minSize());
				if (minSize == 0) {
					var parsed = simple.parse(ctx, List.of());
					if (parsed.hasResultOrPartial()) return Optional.of(parsed);
					minSize = 1; // They probably used nonEmptyList.
				}
				var type = CodecInternalsHelper.getUnnamed(element.getUnderlying(c -> c.codec() == list.get()).orElseThrow().components().getFirst());
				DataResult<PVector<Object>> returnList = DataResult.success(TreePVector.empty());
				for (int i = 0; i < minSize; i++) {
					returnList = returnList.flatMap(
						prevList -> DataResultHelper.withPartialIfError(
							CodecInternalsHelper.defaultValue(type, lookup).flatMap(
								v -> CodecInternalsHelper.forceEncode(type.codec(), ctx, v)
							).map(prevList::plus),
							prevList
						)
					);
				}
				return Optional.of(returnList.flatMap(r -> simple.parse(ctx, r)));
			}

			Optional<DataResult<?>> map = CodecInternalsHelper.getUnderlyingCodec(simple, UnboundedMapCodec.class, lookup).map(
				c -> tryParseMapCodec(
					c, simple, ctx, CodecParser::parse, lookup
				)
			);
			if (map.isEmpty()) {
				map = CodecInternalsHelper.getUnderlyingCodec(simple, SimpleMapCodec.class, lookup).map(
					c -> tryParseMapCodec(
						c, simple, ctx, CodecParser::parse, lookup
					)
				);
			}
			if (map.isEmpty()) {
				map = CodecInternalsHelper.getUnderlyingCodec(simple, DispatchedMapCodec.class, lookup).map(
					c -> tryParseMapCodec(
						c, simple, ctx, CodecParser::parse, lookup
					)
				);
			}
			if (map.isPresent()) {
				return map;
			}

			{
				var container = element.getContainer(ContainerType.EITHER);
				if (container.isPresent()) {
					var c = container.get().components().getFirst();
					return Optional.of(
						CodecInternalsHelper.defaultValue(c, lookup).flatMap(
							v -> CodecInternalsHelper.convert(c.codec(), element.codec(), v, lookup)
						)
					);
				}
			}

			var container = element.getContainer(ContainerType.DISPATCHED_EITHER);
			if (container.isPresent()) {
				var data = element.getContainerData(ContainerType.DISPATCHED_EITHER).orElseThrow();
				var c = container.get().components().getFirst();
				return Optional.of(
					CodecInternalsHelper.defaultValue(c, lookup).flatMap(key -> data.decoderByKey().apply(key).flatMap(decoder -> {
						if (decoder instanceof MapCodec<?> codec) {
							return CodecInternalsHelper.defaultValue(CodecParser.parse(codec, lookup), lookup).flatMap(
								defaultValue -> CodecInternalsHelper.forceEncode(c.codec(), ctx, key).flatMap(
									v -> CodecInternalsHelper.convert(
										codec.codec(), element.codec(),
										defaultValue, v, lookup
									)
								)
							);
						} else {
							return DataResult.error(() -> decoder + " is not a MapCodec, does not know how to find the default value");
						}
					}))
				);
			}

			if (CodecInternalsHelper.isPrimitiveCodec(simple, Codec.INT_STREAM, lookup)) {
				var first = simple.parse(ctx, IntList.of());
				if (!first.hasResultOrPartial()) {
					for (int i = 1; i < 10; i++) {
						var parsed = simple.parse(ctx, IntArrayList.toList(IntStream.generate(() -> 0).limit(i)));
						if (parsed.hasResultOrPartial()) {
							return Optional.of(parsed);
						}
					}
				}
				return Optional.of(first);
			}

			if (CodecInternalsHelper.isPrimitiveCodec(simple, Codec.LONG_STREAM, lookup)) {
				var first = simple.parse(ctx, LongList.of());
				if (!first.hasResultOrPartial()) {
					for (int i = 1; i < 10; i++) {
						var parsed = simple.parse(ctx, LongArrayList.toList(LongStream.generate(() -> 0).limit(i)));
						if (parsed.hasResultOrPartial()) {
							return Optional.of(parsed);
						}
					}
				}
				return Optional.of(first);
			}

			if (CodecInternalsHelper.isPrimitiveCodec(simple, Codec.BYTE_BUFFER, lookup)) {
				var first = simple.parse(ctx, LongList.of());
				if (!first.hasResultOrPartial()) {
					for (int i = 1; i < 10; i++) {
						var parsed = simple.parse(ctx, ByteArrayList.wrap(ByteBuffer.allocate(i).array()));
						if (parsed.hasResultOrPartial()) {
							return Optional.of(parsed);
						}
					}
				}
				return Optional.of(first);
			}

			if (CodecInternalsHelper.isOneOfPrimitiveCodecs(
				simple, lookup,
				Codec.BYTE, Codec.INT, Codec.SHORT, Codec.LONG,
				Codec.DOUBLE, Codec.FLOAT
			)) {
				// Range might not include 0, so clamp it so we get some value.
				return Optional.of(
					simple.parse(
						ctx, element.metadata(MetadataKey.RANGE).map(
							r -> Mth.clamp(0, r.min().doubleValue(), r.max().doubleValue())
						).orElse(0.0)
					)
				);
			}
			if (CodecInternalsHelper.isOneOfPrimitiveCodecs(
				simple, lookup, Codec.BOOL
			)) {
				return Optional.of(simple.parse(ctx, false));
			}
			var entries = Entries.getEntries(simple, lookup);
			if (!entries.isEmpty()) {
				return Optional.of(simple.parse(ctx, entries.stream().findAny().get().key()));
			}
			if (CodecInternalsHelper.isOneOfPrimitiveCodecs(
				simple, lookup, Codec.STRING
			)) {
				return Optional.of(defaultString(element).flatMap(string -> simple.parse(ctx, string)));
			}
			if (element.getUnderlying(c -> c.codec() == Codec.PASSTHROUGH).isPresent()) {
				if (element.getUnderlying(c -> c.codec() == TranslatableContentsAccessor.getPrimitiveArgCodec()).isPresent()) {
					return Optional.of(DataResult.success(""));
				}
				return Optional.of(simple.parse(ctx, Map.of()));
			}
			if (!element.underlying().isEmpty()) {
				var type = CodecInternalsHelper.getUnnamed(element.underlying());
				return Optional.of(
					CodecInternalsHelper.defaultValue(type, lookup).flatMap(
						empty -> CodecInternalsHelper.forceEncode(type.codec(), ctx, empty).flatMap(
							v -> simple.parse(ctx, v)
						)
					)
				);
			}
			return Optional.empty();
		}
	};

}
