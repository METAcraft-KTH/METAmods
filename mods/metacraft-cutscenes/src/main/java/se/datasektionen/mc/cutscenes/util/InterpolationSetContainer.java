package se.datasektionen.mc.cutscenes.util;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import se.datasektionen.mc.cutscenes.transitions.Transition;
import se.datasektionen.mc.metacraft_core.util.Interpolatable;
import se.datasektionen.mc.metacraft_core.util.InterpolationSet;

import java.util.Optional;
import java.util.stream.Stream;

public class InterpolationSetContainer<T extends Interpolatable<CutsceneContext>> {


	public static <I extends Interpolatable<CutsceneContext>> MapCodec<InterpolationSetContainer<I>> createCodec(
			MapCodec<I> elementCodec, InterpolationSet.Creator<CutsceneContext, I> creator
	) {
		return createCodec(elementCodec, creator, Int2ObjectMaps.emptyMap());
	}

	public static <I extends Interpolatable<CutsceneContext>> MapCodec<InterpolationSetContainer<I>> createCodec(
			MapCodec<I> elementCodec, InterpolationSet.Creator<CutsceneContext, I> creator, Int2ObjectMap<InterpolationSet.Adjuster> adjuster
	) {
		return new MapCodec<>() {

			private static final String RELATIVE = "relative_targets";
			private static final String EXACT = "exact_targets";
			private static final String EITHER = "targets";

			private final Codec<InterpolationSet<CutsceneContext, I>> relativeCodec = InterpolationSet.createCodec(elementCodec, creator, adjuster);
			private final Codec<TimestampedInterpolationSet<I>> exactCodec = TimestampedInterpolationSet.createCodec(elementCodec, creator, adjuster);

			private final Codec<Either<InterpolationSet<CutsceneContext, I>, TimestampedInterpolationSet<I>>> eitherCodec = Codec.either(relativeCodec, exactCodec);

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

	private Optional<InterpolationSet<CutsceneContext, T>> relativeTargets;
	private Optional<TimestampedInterpolationSet<T>> exactTargets;

	public InterpolationSetContainer(Optional<InterpolationSet<CutsceneContext, T>> relativeTargets, Optional<TimestampedInterpolationSet<T>> exactTargets) {
		this.relativeTargets = relativeTargets;
		this.exactTargets = exactTargets;
	}

	public InterpolationSet<CutsceneContext, T> getTargets(IntervalMap.Interval<Transition> interval) {
		return relativeTargets.orElseGet(
				() -> exactTargets.map(t -> {
					return t.createFromRange(interval.getStart(), interval.getEnd());
				}).orElseThrow(() -> new IllegalStateException("Error, config lacks both relative and exact targets!"))
		);
	}

}
