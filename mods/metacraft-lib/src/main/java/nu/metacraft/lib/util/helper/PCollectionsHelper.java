package nu.metacraft.lib.util.helper;

import org.pcollections.*;

import java.util.function.Function;
import java.util.stream.Stream;

public class PCollectionsHelper {

	public static <T> TreePVector<T> collect(Stream<T> elements) {
		return collect(elements, TreePVector.empty());
	}

	public static <T, C extends PCollection<T>> C collect(Stream<T> elements, C emptyCollection) {
		return elements.reduce(
				emptyCollection,
				(c, element) -> (C) c.plus(element),
				(lhs, rhs) -> (C) lhs.plusAll(rhs)
		);
	}

	public static <K, V, E> HashPMap<K, V> collectToMap(
			Stream<E> stream, Function<E, K> keyGetter, Function<E, V> valueGetter
	) {
		return collectToMap(stream, keyGetter, valueGetter, HashTreePMap.empty());
	}

	public static <K, V, M extends PMap<K, V>, E> M collectToMap(
			Stream<E> stream, Function<E, K> keyGetter, Function<E, V> valueGetter, M emptyMap
	) {
		if (!emptyMap.isEmpty()) throw new IllegalArgumentException("Empty map must be empty");
		return stream.reduce(
				emptyMap,
				(map, element) -> (M) map.plus(keyGetter.apply(element), valueGetter.apply(element)),
				(lhs, rhs) -> (M) lhs.plusAll(rhs)
		);
	}

}
