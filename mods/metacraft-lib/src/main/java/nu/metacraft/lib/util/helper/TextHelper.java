package nu.metacraft.lib.util.helper;

import java.util.SequencedCollection;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class TextHelper {

	public static String combine(SequencedCollection<String> toCombine) {
		return combine(toCombine, s -> s);
	}

	public static <T> String combine(SequencedCollection<T> toCombine, Function<T, String> stringifier) {
		String last = toCombine.isEmpty() ? "" : " and " + stringifier.apply(toCombine.getLast());
		return toCombine.stream().map(stringifier).limit(toCombine.size()-1).collect(Collectors.joining(", ")) + last;
	}

}
