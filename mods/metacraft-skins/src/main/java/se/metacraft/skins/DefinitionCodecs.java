package se.metacraft.skins;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.KeyDispatchCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Building blocks for the codecs of {@code skin.json5} ({@link SkinDefinition}, {@link PerkCodecs})
 * and of Skin Box files ({@link BoxFile}), whose errors are the reason an admin reads when a skin or
 * box is rejected. A value's error says where it is, as {@code perks[0].effects[1].level: starts at 1};
 * a missing field's says which.
 */
final class DefinitionCodecs {
	private DefinitionCodecs() {}

	/** An error that starts with where it is: {@code level: …}, {@code effects[1].level: …}. */
	private static final Pattern PLACED = Pattern.compile("^[a-z_]+(\\[\\d+])*(\\.[a-z_]+(\\[\\d+])*)*: ");

	static final Codec<Double> NUMBER = withError(Codec.DOUBLE, "must be a number")
			.validate(d -> Double.isFinite(d) ? DataResult.success(d) : DataResult.error(() -> "must be a number"));
	static final Codec<Long> WHOLE = NUMBER.comapFlatMap(
			d -> d == Math.rint(d) ? DataResult.success(d.longValue()) : DataResult.error(() -> "must be a whole number"), Long::doubleValue);
	static final Codec<String> TEXT = text("must be a non-empty string");

	/** A top-level field's text: a non-empty string, or "field \"<key>\" must be a non-empty string". */
	static Codec<String> topText(String key) {
		return text("field \"" + key + "\" must be a non-empty string");
	}

	/** {@code name} (of a skin or a Skin Box), with Minecraft's formatting codes ({@link SkinName}): some text besides its codes. */
	static final Codec<String> NAME = topText("name").validate(name -> SkinName.plain(name).isBlank()
			? DataResult.error(() -> "field \"name\" must have some text besides its formatting codes") : DataResult.success(name));
	/** One colour of a gradient: {@code "#FF8C00"}. */
	private static final Codec<TextColor> COLOUR = text("must be a colour written \"#RRGGBB\"").comapFlatMap(
			colour -> colour.matches("#[0-9a-fA-F]{6}") ? DataResult.success(TextColor.fromRgb(Integer.parseInt(colour.substring(1), 16)))
					: DataResult.error(() -> "must be a colour written \"#RRGGBB\", not \"" + colour + "\""),
			colour -> String.format("#%06X", colour.getValue()));
	/** {@code gradient}: {@code { colors: ["#FF8C00", "#FFD700"], mirror: true }}, two colours or more; {@code mirror} is optional. */
	static final Codec<SkinName.Gradient> GRADIENT = object(StrictCodec.of(RecordCodecBuilder.<SkinName.Gradient>mapCodec(i -> i.group(
			rawField("colors", list("colors", COLOUR, "\"colors\" must be a list of colours, as [\"#FF8C00\", \"#FFD700\"]"), "missing field \"colors\"")
					.forGetter(SkinName.Gradient::colours),
			orElse(optionalField("mirror", withError(Codec.BOOL, "must be true or false")), false).forGetter(SkinName.Gradient::mirror)
	).apply(i, SkinName.Gradient::new))).validate(g -> g.colours().size() < 2
			? DataResult.error(() -> "\"colors\" needs two colours or more") : DataResult.success(g)),
			"must be an object: { colors: [\"#FF8C00\", \"#FFD700\"], mirror: true }");

	/** {@code definition}, unless its name has a colour code while a gradient colours it. */
	static <D> DataResult<D> nameFitsGradient(D definition, String name, Optional<SkinName.Gradient> gradient) {
		return gradient.isPresent() && SkinName.hasColourCode(name)
				? DataResult.error(() -> "a name with a \"gradient\" takes no colour codes, only formats such as &l: the gradient colours it")
				: DataResult.success(definition);
	}

	/** A non-empty string; anything else is {@code error}. */
	static Codec<String> text(String error) {
		return withError(Codec.STRING, error).validate(s -> s.isBlank() ? DataResult.error(() -> error) : DataResult.success(s));
	}

	/** {@code error}, placed at {@code where}: "level: starts at 1", "effects[1].level: starts at 1". */
	static String at(String where, String error) {
		return where + (PLACED.matcher(error).find() ? "." : ": ") + error;
	}

	/** {@code codec} with any error it decodes replaced by {@code error} (see {@link #mapError}). */
	static <A> Codec<A> withError(Codec<A> codec, String error) {
		return mapError(codec, e -> error);
	}

	/**
	 * {@code codec} with the errors it decodes rewritten by {@code rewrite}, and without their partial
	 * results: carried on, those would add errors of their own to the one that matters.
	 */
	static <A> Codec<A> mapError(Codec<A> codec, UnaryOperator<String> rewrite) {
		return codec.mapResult(new Codec.ResultFunction<>() {
			@Override
			public <T> DataResult<Pair<A, T>> apply(DynamicOps<T> ops, T input, DataResult<Pair<A, T>> a) {
				return whole(a.mapError(rewrite));
			}

			@Override
			public <T> DataResult<T> coApply(DynamicOps<T> ops, A input, DataResult<T> t) {
				return t;
			}
		});
	}

	/** {@code codec}, but anything that isn't an object is {@code notAnObject}. */
	static <A> Codec<A> object(Codec<A> codec, String notAnObject) {
		return new Codec<>() {
			@Override
			public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
				return ops.getMap(input).isError() ? DataResult.error(() -> notAnObject) : codec.decode(ops, input);
			}

			@Override
			public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
				return codec.encode(input, ops, prefix);
			}
		};
	}

	/** A field that must be there; its errors are placed at {@code key}. */
	static <A> MapCodec<A> field(String key, Codec<A> codec) {
		return field(key, codec, "missing field \"" + key + "\"");
	}

	/** A field that must be there, {@code missing} if it isn't; its errors are placed at {@code key}. */
	static <A> MapCodec<A> field(String key, Codec<A> codec, String missing) {
		return required(optionalField(key, codec), missing);
	}

	/** A field that may be left out; its errors are placed at {@code key}. */
	static <A> MapCodec<Optional<A>> optionalField(String key, Codec<A> codec) {
		return new Field<>(key, codec, e -> at(key, e));
	}

	/** A field that must be there, whose codec's errors already say what they're about (or where, as a {@link #list}'s do). */
	static <A> MapCodec<A> rawField(String key, Codec<A> codec, String missing) {
		return required(optionalRawField(key, codec), missing);
	}

	/** A field that may be left out, whose codec's errors already say what they're about. */
	static <A> MapCodec<Optional<A>> optionalRawField(String key, Codec<A> codec) {
		return new Field<>(key, codec, UnaryOperator.identity());
	}

	/** An optional field read as {@code fallback} when it's left out. */
	static <A> MapCodec<A> orElse(MapCodec<Optional<A>> field, A fallback) {
		return field.xmap(o -> o.orElse(fallback), Optional::of);
	}

	private static <A> MapCodec<A> required(MapCodec<Optional<A>> field, String missing) {
		return field.flatXmap(o -> o.map(DataResult::success).orElseGet(() -> DataResult.error(() -> missing)), a -> DataResult.success(Optional.of(a)));
	}

	/** A list named {@code key}, its items' errors placed at their index ({@code perks[1]}); anything but a list is {@code notAList}. */
	static <E> Codec<List<E>> list(String key, Codec<E> item, String notAList) {
		return new Codec<>() {
			@Override
			public <T> DataResult<Pair<List<E>, T>> decode(DynamicOps<T> ops, T input) {
				Optional<List<T>> items = ops.getStream(input).result().map(Stream::toList);
				if (items.isEmpty()) return DataResult.error(() -> notAList);
				List<E> read = new ArrayList<>();
				for (int i = 0; i < items.get().size(); i++) {
					T value = items.get().get(i);
					DataResult<E> one = item.parse(ops, value == null ? ops.empty() : value);
					String where = key + "[" + i + "]";
					if (one instanceof DataResult.Error<E> error) return DataResult.error(() -> at(where, error.message()));
					read.add(one.getOrThrow());
				}
				return DataResult.success(Pair.of(List.copyOf(read), ops.empty()));
			}

			@Override
			public <T> DataResult<T> encode(List<E> input, DynamicOps<T> ops, T prefix) {
				return item.listOf().encode(input, ops, prefix);
			}
		};
	}

	/** Reads {@code key} first, then the rest of the object with the codec its value picks. */
	static <K, V> MapCodec<V> dispatch(MapCodec<K> key, Function<V, K> type, Function<K, MapCodec<? extends V>> codec) {
		return new KeyDispatchCodec<>(key, v -> DataResult.success(type.apply(v)), k -> DataResult.success(codec.apply(k)));
	}

	/** {@code result}, or its error without the partial result. */
	private static <R> DataResult<R> whole(DataResult<R> result) {
		return result instanceof DataResult.Error<R> error ? DataResult.error(error.messageSupplier()) : result;
	}

	/** One field of an object; {@code place} rewrites its value's errors. */
	private static final class Field<A> extends MapCodec<Optional<A>> {
		private final String key;
		private final Codec<A> codec;
		private final UnaryOperator<String> place;

		Field(String key, Codec<A> codec, UnaryOperator<String> place) {
			this.key = key;
			this.codec = codec;
			this.place = place;
		}

		@Override
		public <T> Stream<T> keys(DynamicOps<T> ops) {
			return Stream.of(ops.createString(key));
		}

		@Override
		public <T> DataResult<Optional<A>> decode(DynamicOps<T> ops, MapLike<T> input) {
			T value = input.get(key);
			if (value == null) return DataResult.success(Optional.empty());
			return whole(codec.parse(ops, value).map(Optional::of).mapError(place));
		}

		@Override
		public <T> RecordBuilder<T> encode(Optional<A> input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
			return input.isPresent() ? prefix.add(key, codec.encodeStart(ops, input.get())) : prefix;
		}
	}
}
