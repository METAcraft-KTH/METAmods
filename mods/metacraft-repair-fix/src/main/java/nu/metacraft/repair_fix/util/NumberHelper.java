package nu.metacraft.repair_fix.util;

import java.util.OptionalInt;

public class NumberHelper {

	public static OptionalInt getInt(String string) {
		try {
			return OptionalInt.of(Integer.parseInt(string));
		} catch (NumberFormatException ignored) {
			return OptionalInt.empty();
		}
	}

}
