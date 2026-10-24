package se.metacraft.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.*;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.*;
import net.minecraft.core.registries.codec.RegistryFileCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Xoroshiro128PlusPlus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import se.metacraft.lib.METAcraftLib;
import se.metacraft.config.comments.codecs.MapCodecWithComments;
import se.metacraft.lib.mixin.FieldDecoderAccessor;
import org.pcollections.PSet;
import org.pcollections.PVector;
import org.pcollections.TreePVector;
import se.metacraft.config.event.CodecParseEvents;
import se.metacraft.config.mixin.codec.*;
import se.metacraft.config.parser.Metadata;
import se.metacraft.config.parser.CodecParser;
import se.metacraft.config.parser.MetadataKey;
import se.metacraft.config.parser.MetadataMap;
import se.metacraft.config.parser.metadata.*;
import se.metacraft.config.parser.result.AbstractCodecResult;
import se.metacraft.config.parser.result.CodecResult;
import se.metacraft.config.parser.result.MapCodecResult;
import se.metacraft.config.util.event.EventWithPhases;
import se.metacraft.config.util.helper.CodecInternalsHelper;
import se.metacraft.config.util.helper.CodecParsingHelper;

import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class BuiltinParsers {

	private static <T> Codec<Holder<T>> removeInline(RegistryFileCodecAccessor<T> codec) {
		if (!codec.getAllowInline()) {
			//noinspection unchecked
			return (Codec<Holder<T>>) codec;
		} else {
			return RegistryFileCodec.create(codec.getRegistryKey(), codec.getElementCodec(), false);
		}
	}

	private static <T> Codec<Holder<T>> asHolder(Codec<T> codec) {
		return codec.xmap(Holder::direct, Holder::value);
	}

	private static <K, V> AbstractCodecResult keyDispatch(
		MapCodec<?> codec, KeyDispatchCodecAccessor<K, V> dispatched, HolderLookup.Provider lookup
	) {
		return MapCodecResult.createWithComponents(
			codec, Metadata.metadataMap(
				new Container<>(
					ContainerType.DISPATCHED_EITHER,
					Container.DispatchedEither.create(
						dispatched.getType(),
						dispatched.getDecoder(),
						dispatched.getEncoder()
					)
				)
			),
			TreePVector.singleton(CodecParser.parse(dispatched.getKeyCodec(), lookup))
		);
	}

	private static final CodecParseEvents.ParseCodec CODEC = (codec, lookup) -> {
		if (codec instanceof SimpleCodecAccessor simple) {
			if (
				simple.getDecoder() instanceof MappedDecoderAccessor mapped &&
				mapped.getParent() instanceof Codec<?> c
			) {
				var parameters = CodecInternalsHelper.getVariablesInTheMappings(
					mapped, 1, CodecInternalsHelper.LAMBDA_WITH_FUNCTION
				);
				return Optional.of(CodecParsingHelper.postProcessMappings(
					codec, CodecParser.parse(c, lookup), parameters, lookup
				));
			}
		}
		if (codec instanceof ListCodec<?>(Codec<?> elementCodec, int minSize, int maxSize)) {
			return Optional.of(
				CodecResult.createWithComponents(
					codec,
					Metadata.metadataMap(Container.simple(ContainerType.LIST), new Range(minSize, maxSize)),
					TreePVector.singleton(CodecParser.parse(elementCodec, lookup))
				)
			);
		}
		if (codec instanceof UnboundedMapCodec<?, ?>(Codec<?> keyCodec, Codec<?> elementCodec)) {
			return Optional.of(
				CodecResult.createWithComponents(
					codec, Metadata.metadataMap(Container.simple(ContainerType.MAP)),
					TreePVector.singleton(
						CodecParser.parse(keyCodec, lookup)
					).plus(
						CodecParser.parse(elementCodec, lookup)
					)
				)
			);
		}
		if (codec instanceof DispatchedMapCodec<?, ?>(Codec<?> keyCodec, Function<?, ? extends Codec<?>> valueCodecFunction)) {
			return Optional.of(
				CodecResult.createWithComponents(
					codec,
					Metadata.metadataMap(
						new Container<>(
							ContainerType.DISPATCHED_MAP, Container.DispatchedMap.create(valueCodecFunction)
						)
					),
					TreePVector.singleton(CodecParser.parse(keyCodec, lookup))
				)
			);
		}
		if (codec instanceof RecursiveCodecAccessor<?> recursive) {
			// It might be tempting to just get the codec here, but then the tree might never stop growing.
			return Optional.of(
				CodecResult.createWithComponents(
					codec, Metadata.metadataMap(
						new Container<>(
							ContainerType.RECURSIVE, Container.Recursive.create(recursive.getWrapped(), lookup)
						)
					),
					TreePVector.empty()
				)
			);
		}
		if (codec instanceof EitherCodec<?, ?>(Codec<?> first, Codec<?> second)) {
			return Optional.of(
				CodecResult.createWithComponents(
					codec, Metadata.metadataMap(Container.simple(ContainerType.EITHER)),
					TreePVector.singleton(CodecParser.parse(first, lookup)).plus(
						CodecParser.parse(second, lookup)
					)
				)
			);
		}
		if (codec instanceof XorCodec<?, ?>(Codec<?> first, Codec<?> second)) {
			return Optional.of(
				CodecResult.createWithComponents(
					codec, Metadata.metadataMap(Container.simple(ContainerType.EITHER)),
					TreePVector.singleton(CodecParser.parse(first, lookup)).plus(
						CodecParser.parse(second, lookup)
					)
				)
			);
		}
		if (codec instanceof MappedCodecAccessor mapped) {
			var parameters = CodecInternalsHelper.getVariablesInTheMappings(
				mapped, 1, CodecInternalsHelper.LAMBDA_WITH_FUNCTION
			);
			return Optional.of(CodecParsingHelper.postProcessMappings(
				codec, CodecParser.parse(mapped.getParent(), lookup), parameters, lookup)
			);
		}
		if (codec instanceof MapCodec.MapCodecCodec<?>(MapCodec<?> c)) {
			return Optional.of(
				CodecResult.createMapped(codec, Metadata.noMetadata(), CodecParser.parse(c, lookup))
			);
		}
		if (codec instanceof UnitCodecAccessor unit) {
			return Optional.of(
				CodecResult.createMapped(
					codec, Metadata.metadataMap(Container.simple(ContainerType.UNIT),
					new DefaultValue(unit.getValue().get())), AbstractCodecResult.EMPTY
				)
			);
		}
		if (codec == Codec.INT_STREAM) {
			return Optional.of(
				CodecResult.createWithComponents(
					codec,
					Metadata.metadataMap(Container.simple(ContainerType.LIST)),
					TreePVector.singleton(CodecParser.parse(Codec.INT, lookup))
				)
			);
		}
		if (codec == Codec.LONG_STREAM) {
			return Optional.of(
				CodecResult.createWithComponents(
					codec,
					Metadata.metadataMap(Container.simple(ContainerType.LIST)),
					TreePVector.singleton(CodecParser.parse(Codec.LONG, lookup))
				)
			);
		}
		if (codec == Codec.BYTE_BUFFER) {
			return Optional.of(
				CodecResult.createWithComponents(
					codec,
					Metadata.metadataMap(Container.simple(ContainerType.LIST)),
					TreePVector.singleton(CodecParser.parse(Codec.BYTE, lookup))
				)
			);
		}
		return Optional.empty();
	};

	private static final CodecParseEvents.ParseMapCodec MAP_CODEC = (codec, lookup) -> {
		var components = CodecParsingHelper.parseFlattenedComponents(codec, lookup);
		if (!components.isEmpty()) {
			return Optional.of(
				MapCodecResult.createWithComponents(
					codec, Metadata.metadataMap(Container.simple(ContainerType.RECORD)), components
				)
			);
		}
		if (codec instanceof SimpleMapCodecAccessor simple) {
			if ( // Codecs defined via xmap
				simple.getDecoder() instanceof MappedMapDecoderAccessor mapped &&
				mapped.getParent() instanceof MapCodec<?> parent
			) {
				var parameters = CodecInternalsHelper.getVariablesInTheMappings(
					mapped, 1, CodecInternalsHelper.LAMBDA_WITH_FUNCTION
				);
				return Optional.of(CodecParsingHelper.postProcessMappings(
					codec, CodecParser.parse(parent, lookup), parameters, lookup)
				);
			}
			if (simple.getDecoder() instanceof FieldDecoderAccessor d) {
				if (d.getElementCodec() instanceof Codec<?> c) {
					return Optional.of(
						MapCodecResult.createMapped(
							codec,
							Metadata.metadataMap(
								new NamedField(d.getName(), true)
							),
							CodecParser.parse(c, lookup)
						)
					);
				}
			}
		}
		if (codec instanceof MappedMapCodecAccessor mapped) {
			var parameters = CodecInternalsHelper.getVariablesInTheMappings(
				mapped, 1, CodecInternalsHelper.LAMBDA_WITH_FUNCTION
			);
			return Optional.of(CodecParsingHelper.postProcessMappings(
				codec, CodecParser.parse(mapped.getParent(), lookup), parameters, lookup)
			);
		}
		if (codec instanceof RecursiveMapCodecAccessor<?> recursive) {
			// It might be tempting to just get the codec here, but then the tree might never stop growing.
			return Optional.of(
				MapCodecResult.createWithComponents(
					codec,
					Metadata.metadataMap(new Container<>(
						ContainerType.RECURSIVE, Container.Recursive.createMap(recursive.getWrapped(), lookup)
					)),
					TreePVector.empty()
				)
			);
		}
		if (codec instanceof SimpleMapCodec<?, ?> map) {
			return Optional.of(
				MapCodecResult.createWithComponents(
					codec, Metadata.metadataMap(Container.simple(ContainerType.MAP)),
					TreePVector.singleton(
						CodecParser.parse(map.keyCodec(), lookup)
					).plus(
						CodecParser.parse(map.elementCodec(), lookup)
					)
				)
			);
		}
		if (codec instanceof EitherMapCodecAccessor either) {
			return Optional.of(
				MapCodecResult.createWithComponents(
					codec, Metadata.metadataMap(Container.simple(ContainerType.EITHER)),
					TreePVector.singleton(CodecParser.parse(either.getFirst(), lookup)).plus(
						CodecParser.parse(either.getSecond(), lookup)
					)
				)
			);
		}
		if (codec instanceof OptionalFieldCodecAccessor optionalField) {
			return Optional.of(
				MapCodecResult.createMapped(
					codec,
					Metadata.metadataMap(new NamedField(optionalField.getName(), false)),
					CodecParser.parse(optionalField.getElementCodec(), lookup)
				)
			);
		}
		if (codec instanceof KeyDispatchCodecAccessor<?, ?> dispatched) {
			return Optional.of(keyDispatch(codec, dispatched, lookup));
		}
		if (codec instanceof UnitCodecAccessor unit) {
			return Optional.of(
				MapCodecResult.createMapped(
					codec, Metadata.metadataMap(Container.simple(ContainerType.UNIT),
						new DefaultValue(unit.getValue().get())), AbstractCodecResult.EMPTY
				)
			);
		}
		return Optional.empty();
	};

	private static AbstractCodecResult sizelimited(Codec<?> codec, int limit, HolderLookup.Provider lookup) {
		if (codec instanceof SimpleCodecAccessor simple) {
			if ( // Codecs defined via xmap
				simple.getDecoder() instanceof MappedDecoderAccessor mapped &&
				mapped.getParent() instanceof Codec<?> parent
			) {
				return CodecResult.createMapped(
					codec, MetadataMap.from(new Range(limit, limit)), CodecParser.parse(parent, lookup)
				);
			}
		}
		if (codec instanceof MappedCodecAccessor mapped) {
			return CodecResult.createMapped(
				codec, MetadataMap.from(new Range(limit, limit)), CodecParser.parse(mapped.getParent(), lookup)
			);
		}
		throw new IllegalArgumentException(codec + "is not a simple mapped codec!");
	}

	// Util::fixedSize does not leave any trace for us to follow unfortunately, so we just put them in this map manually instead.
	public static final Object2IntMap<Object> SIZE_LIMITED_CODECS = new Object2IntOpenHashMap<>();
	static {
		SIZE_LIMITED_CODECS.put(Vec3.CODEC, 3);
		SIZE_LIMITED_CODECS.put(Rotations.CODEC, 3);
		SIZE_LIMITED_CODECS.put(Vec2.CODEC, 2);
		SIZE_LIMITED_CODECS.put(ExtraCodecs.VECTOR2F, 2);
		SIZE_LIMITED_CODECS.put(ExtraCodecs.VECTOR3F, 3);
		SIZE_LIMITED_CODECS.put(ExtraCodecs.VECTOR3I, 3);
		SIZE_LIMITED_CODECS.put(ExtraCodecs.VECTOR4F, 4);
		SIZE_LIMITED_CODECS.put(ExtraCodecs.QUATERNIONF, 4);
		SIZE_LIMITED_CODECS.put(ExtraCodecs.MATRIX4F, 16);
		SIZE_LIMITED_CODECS.put(Xoroshiro128PlusPlus.CODEC, 2);
		SIZE_LIMITED_CODECS.put(BlockPos.CODEC, 3);
		SIZE_LIMITED_CODECS.put(Vec3i.CODEC, 3);
		SIZE_LIMITED_CODECS.put(UUIDUtil.CODEC, 4);
		SIZE_LIMITED_CODECS.put(ChunkPos.CODEC, 2);
		SIZE_LIMITED_CODECS.put(BoundingBox.CODEC, 6);
	}

	private static final CodecParseEvents.ParseCodec MINECRAFT_PRE_CODECS = (codec, lookup) -> {
		if (SIZE_LIMITED_CODECS.containsKey(codec)) {
			return Optional.of(sizelimited(codec, SIZE_LIMITED_CODECS.getInt(codec), lookup));
		}
		return Optional.empty();
	};

	private static final CodecParseEvents.ParseCodec MINECRAFT_CODECS = (codec, lookup) -> {
		if (
			codec instanceof StringRepresentableCodecAccessor stringRepresentable &&
			stringRepresentable.getCodec() instanceof OrCompressedCodecAccessor orCompressed &&
			orCompressed.getCompressed() instanceof SimpleCodecAccessor c &&
			c.getDecoder() instanceof MappedDecoderAccessor mapped
		) {
			PSet<Entries.Entry> entries = Entries.EMPTY_SET;
			var variables = CodecInternalsHelper.getVariablesInTheMappings(
				mapped, 2, CodecInternalsHelper.LAMBDA_WITH_FUNCTION
			);
			for (var variable : variables) {
				if (variable.getClass().isArray()) {
					var arr = (Object[]) variable;
					if (arr.length > 0) {
						var element = arr[0];
						if (CodecInternalsHelper.isCodecValidUncasted(codec, element, lookup)) {
							for (var e : arr) {
								if (e instanceof StringRepresentable representable) {
									entries = entries.plus(
										new Entries.Entry(representable.getSerializedName(), representable)
									);
								}
							}
						}
					}
				}
			}
			return Optional.of(CodecResult.createWithComponents(
				codec, Metadata.metadataMap(new Entries(entries)), TreePVector.empty())
			);
		}
		if (codec instanceof OrCompressedCodecAccessor orCompressed) {
			return Optional.of(
				CodecResult.createMapped(
					codec, MetadataMap.from(new Remainder(TreePVector.singleton(orCompressed.getCompressed()))),
					CodecParser.parse(orCompressed.getNormal(), lookup)
				)
			);
		}
		if (codec instanceof RegistryFixedCodecAccessor fixed) {
			if (lookup != null) {
				var registry = lookup.lookup(fixed.getRegistryKey());
				if (registry.isPresent()) {
					return Optional.of(CodecResult.createWithComponents(
						codec, CodecParsingHelper.metadataFromRegistry(registry.get()), TreePVector.empty())
					);
				}
			} else {
				return Optional.of(CodecResult.createWithComponents(
					codec, MetadataMap.from(new Remainder(TreePVector.singleton(fixed.getRegistryKey()))), TreePVector.empty())
				);
			}
		}
		if (codec instanceof RegistryFileCodecAccessor<?> file) {
			PVector<AbstractCodecResult> results = TreePVector.empty();
			if (lookup != null) {
				var registry = lookup.lookup(file.getRegistryKey());
				if (registry.isPresent()) {
					results = results.plus(
						CodecResult.createWithComponents(
							removeInline(file),
							CodecParsingHelper.metadataFromRegistry(registry.get()),
							TreePVector.empty()
						)
					);
				}
			} else if (!file.getAllowInline()) {
				return Optional.of(CodecResult.createWithComponents(
					codec, MetadataMap.from(new Remainder(TreePVector.singleton(file.getRegistryKey()))), TreePVector.empty())
				);
			}
			if (file.getAllowInline()) {
				results = results.plus(
					CodecParser.parse(
						asHolder(file.getElementCodec()),
						lookup
					)
				);
			}
			if (results.size() == 1) return Optional.of(results.getFirst());
			return Optional.of(
				CodecResult.createWithComponents(
					codec, Metadata.metadataMap(Container.simple(ContainerType.EITHER)), results
				)
			);
		}
		return Optional.empty();
	};

	private static final CodecParseEvents.ParseMapCodec MINECRAFT_MAP_CODECS = (codec, lookup) -> {
		if (codec instanceof OrCompressedMapCodecAccessor orCompressed) {
			return Optional.of(
				MapCodecResult.createMapped(
					codec, MetadataMap.from(new Remainder(TreePVector.singleton(orCompressed.getCompressed()))),
					CodecParser.parse(orCompressed.getNormal(), lookup)
				)
			);
		}
		if (codec instanceof StrictEitherAccessor strictEither) {
			return Optional.of(
				MapCodecResult.createMapped(
					codec, MetadataMap.from(new Remainder(TreePVector.singleton(strictEither.getFuzzy()))),
					CodecParser.parse(strictEither.getTyped(), lookup)
				)
			);
		}
		return Optional.empty();
	};

	private static final CodecParseEvents.ParseMapCodec CUSTOM = (codec, lookup) -> {
		if (codec instanceof MapCodecWithComments<?> comments) {
			return Optional.of(
				MapCodecResult.createMapped(
					codec,
					comments.comments().isEmpty() ? MetadataMap.EMPTY : Metadata.metadataMap(new Comments(comments.comments())),
					CodecParser.parse(comments.getCodec(), lookup)
				)
			);
		}
		return Optional.empty();
	};

	public static final CodecParseEvents.ParseCodec ALL_CODECS = (codec, lookup) -> EventWithPhases.getOptionalResult(
		new CodecParseEvents.ParseCodec[]{MINECRAFT_PRE_CODECS, CODEC, MINECRAFT_CODECS},
		p -> p.parse(codec, lookup)
	);

	public static final CodecParseEvents.ParseMapCodec ALL_MAP_CODECS = (codec, lookup) -> EventWithPhases.getOptionalResult(
		new CodecParseEvents.ParseMapCodec[]{CUSTOM, MAP_CODEC, MINECRAFT_MAP_CODECS},
		p -> p.parse(codec, lookup)
	);


	private static Optional<Codec<?>> findNearestCodec(AbstractCodecResult result) {
		if (result instanceof CodecResult res) {
			return Optional.of(res.codec());
		}
		if (result.isEmpty()) return Optional.empty();
		var underlying = findNearestCodec(result.underlying());
		if (underlying.isPresent()) return underlying;
		return Optional.empty();
	}

	public static final CodecParseEvents.PostProcessMappings POST_PROCESS = (metadata, nextLevel, underlying, parameters, lookup) -> {
		var nearestCodec = findNearestCodec(underlying);
		if (nearestCodec.isPresent()) {
			var list = parameters.stream().filter(
				param -> CodecInternalsHelper.isCodecValidUncasted(nearestCodec.get(), param, lookup)
			).toList();
			if (list.size() == 1) {
				metadata = metadata.plus(new DefaultValue(list.getFirst()));
			}
		}

		Registry<?> registry = null;
		for (var param : parameters) {
			if (param instanceof Registry<?> r) {
				if (registry == null) {
					registry = r;
				} else if (registry != r) {
					METAcraftLib.LOGGER.warn("Found multiple registries!");
				}
			}
		}

		if (registry != null) {
			metadata = metadata.plusAll(
				CodecParsingHelper.metadataFromRegistry(registry)
			);
		}

		var list = parameters.stream().filter(
			param -> CodecInternalsHelper.isCodecValidUncasted(underlying.codec(), param, lookup) || underlying.isContainerType(ContainerType.LIST)
		).toList();
		if (
			list.size() == 2 &&
			list.getFirst() instanceof Number lhs &&
			list.getLast() instanceof Number rhs
		) {
			metadata = metadata.plus(Range.range(lhs, rhs));
		}

		for (var param : parameters) {
			for (var field : param.getClass().getDeclaredFields()) {
				if (Map.class.isAssignableFrom(field.getType())) {
					try {
						field.setAccessible(true);
						try {
							var map = (Map<?, ?>) (Modifier.isStatic(field.getModifiers()) ? field.get(null) : field.get(param));
							if (map == null) continue;
							var entries = Entries.from(
								map.entrySet().stream().filter(
									e -> e.getKey() instanceof String && e.getValue() instanceof MapCodec<?>
								).map(
									e -> new Entries.Entry((String) e.getKey(), e.getValue())
								)
							);
							if (!entries.isEmpty()) {
								metadata = metadata.plus(entries);
							}
						} catch (IllegalAccessException e) {
							throw new RuntimeException(e);
						}
					} catch (InaccessibleObjectException ignored) {}
				}
			}
		}

		var entries = underlying.metadata(MetadataKey.ENTRIES);
		if (entries.isPresent()) {
			PSet<Entries.Entry> newStrings = entries.get().strings();
			for (var value : entries.get().strings()) {
				var converted = CodecInternalsHelper.convert(underlying.codec(), nextLevel, value.value(), lookup);
				newStrings = newStrings.minus(value);
				if (converted.hasResultOrPartial()) {
					newStrings = newStrings.plus(value.withValue(converted.getPartialOrThrow()));
				}
			}
			metadata = metadata.plus(
				newStrings == entries.get().strings() ? entries.get() : new Entries(newStrings)
			);
		}
		var defaultValue = underlying.metadata(MetadataKey.DEFAULT_VALUE);
		if (defaultValue.isPresent()) {
			var converted = CodecInternalsHelper.convert(
				underlying.codec(), nextLevel, defaultValue.get().value(), lookup
			);
			if (converted.hasResultOrPartial()) {
				metadata = metadata.plus(
					converted == defaultValue.get().value() ? defaultValue.get() : new DefaultValue(converted.getPartialOrThrow())
				);
			}
		}
		return metadata;
	};

}
