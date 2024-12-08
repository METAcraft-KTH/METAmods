package se.datasektionen.mc.cutscenes.util;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.*;
import se.datasektionen.mc.cutscenes.transitions.Transition;

import java.util.Optional;
import java.util.stream.Stream;

public class InterpolationSetContainer<T extends Interpolatable> {

	public static <I extends Interpolatable> MapCodec<InterpolationSetContainer<I>> createCodec(
			MapCodec<I> elementCodec, InterpolationSet.Creator<I> creator
	) {
		return new MapCodec<>() {

			private static final String RELATIVE = "relative_targets";
			private static final String EXACT = "exact_targets";
			private static final String EITHER = "targets";

			private final Codec<InterpolationSet<I>> relativeCodec = InterpolationSet.createCodec(elementCodec, creator);
			private final Codec<TimestampedInterpolationSet<I>> exactCodec = TimestampedInterpolationSet.createCodec(elementCodec, creator);

			private final Codec<Either<InterpolationSet<I>, TimestampedInterpolationSet<I>>> eitherCodec = Codec.either(relativeCodec, exactCodec);

			@Override
			public <T> RecordBuilder<T> encode(InterpolationSetContainer<I> input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
				input.relativeTargets.ifPresentOrElse(
						set -> prefix.add(RELATIVE, relativeCodec.encodeStart(ops, set)),
						() -> {
							input.exactTargets.ifPresent(
									set -> prefix.add(
											EXACT, exactCodec.encodeStart(ops, set)
									)
							);
						}
				);
				return prefix;
			}

			@Override
			public <T> DataResult<InterpolationSetContainer<I>> decode(DynamicOps<T> ops, MapLike<T> input) {
				var relative = input.get(RELATIVE);
				if (relative != null) {
					return relativeCodec.parse(ops, relative).map(targets -> new InterpolationSetContainer<>(
							Optional.of(targets), Optional.empty()
					));
				}
				var timestamped = input.get(EXACT);
				if (timestamped != null) {
					return exactCodec.parse(ops, timestamped).map(targets -> new InterpolationSetContainer<>(
							Optional.empty(), Optional.of(targets)
					));
				}
				var arbitrary = input.get(EITHER);
				if (arbitrary == null) { //Legacy data
					arbitrary = input.get("unparsed_targets");
				}
				if (arbitrary != null) {
					return eitherCodec.parse(ops, arbitrary).map(
						targetEither -> new InterpolationSetContainer<>(targetEither.left(), targetEither.right())
					);
				}
				return DataResult.error(() -> "No key " + RELATIVE + " or " + EXACT + " or " + EITHER + " in " + input);
			}

			@Override
			public <T> Stream<T> keys(DynamicOps<T> ops) {
				return Stream.empty();
			}
		};
	}

	private Optional<InterpolationSet<T>> relativeTargets;
	private Optional<TimestampedInterpolationSet<T>> exactTargets;

	public InterpolationSetContainer(Optional<InterpolationSet<T>> relativeTargets, Optional<TimestampedInterpolationSet<T>> exactTargets) {
		this.relativeTargets = relativeTargets;
		this.exactTargets = exactTargets;
	}

	public InterpolationSet<T> getTargets(IntervalMap.Interval<Transition> interval) {
		return relativeTargets.orElseGet(
				() -> exactTargets.map(t -> {
					return t.createFromRange(interval.getStart(), interval.getEnd());
				}).orElseThrow(() -> new IllegalStateException("Error, config lacks both relative and exact targets!"))
		);
	}

}
