package nu.metacraft.cutscenes.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.Util;

public class IntervalMap<T> {

	public static <T> Codec<IntervalMap<T>> createCodec(MapCodec<T> valueCodec) {
		return Interval.createCodec(valueCodec).listOf().xmap(
				IntervalMap::new, map -> map.getIntervals().stream().toList()
		);
	}

	private final Set<Interval<T>> intervals;
	private OptionalInt end = OptionalInt.empty();

	public IntervalMap(Stream<Interval<T>> intervals) {
		this.intervals = intervals.collect(Collectors.toSet());
	}

	public IntervalMap(Collection<Interval<T>> intervals) {
		this.intervals = new HashSet<>(intervals);
	}

	public IntervalMap() {
		this.intervals = new HashSet<>();
	}

	public void add(Interval<T> interval) {
		clearCache();
		this.intervals.add(interval);
	}

	public Stream<Interval<T>> getIntervalsAt(int index) {
		return intervals.stream().filter(
				interval -> interval.contains(index)
		);
	}

	public Stream<T> getValuesAt(int index) {
		return getIntervalsAt(index).map(Interval::getObject);
	}

	public Collection<Interval<T>> getIntervals() {
		return intervals;
	}

	public int getEnd() {
		if (end.isEmpty()) {
			end = intervals.stream().mapToInt(Interval::getEnd).max();
		}
		return end.getAsInt();
	}

	protected void clearCache() {
		end = OptionalInt.empty();
	}

	public static class Interval<T> {

		public static <T> Codec<Interval<T>> createCodec(MapCodec<T> objectCodec) {
			return RecordCodecBuilder.create(
					instance -> instance.group(
							Codec.INT_STREAM.comapFlatMap(
									stream -> Util.fixedSize(stream, 2), IntStream::of
							).fieldOf("range").forGetter(
									interval -> new int[]{interval.getStart(), interval.getEnd()}
							),
							objectCodec.forGetter(Interval::getObject)
					).apply(instance, (range, object) -> new Interval<>(range[0], range[1], object))
			);
		}

		private final int start;
		private final int end;
		private final T object;

		public Interval(int lhs, int rhs, T object) {
			this.start = Math.min(lhs, rhs);
			this.end = Math.max(lhs, rhs);
			this.object = object;
		}

		public boolean contains(int value) {
			return start <= value && end >= value;
		}

		public int getPosInRange(int pos) {
			return pos - start;
		}

		public int getRemaining(int pos) {
			return getLength() - getPosInRange(pos);
		}

		public int getStart() {
			return start;
		}

		public int getEnd() {
			return end;
		}

		public int getLength() {
			return end - start;
		}

		public T getObject() {
			return object;
		}

		public float getDelta(int pos) {
			return (float) getPosInRange(pos) / getLength();
		}

		public <S> Interval<S> map(Function<T, S> mapper) {
			return new Interval<>(start, end, mapper.apply(object));
		}
	}

}
