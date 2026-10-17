package metacraft.kultur.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/** Tiny JSON literal helpers so the generator reads like the data it emits. */
final class J {
	private J() {}

	/** {@code obj("k", v, "k2", v2, ...)}; values may be JsonElement, String, Number, Boolean, or null (skipped). */
	static JsonObject obj(Object... kv) {
		JsonObject o = new JsonObject();
		for (int i = 0; i < kv.length; i += 2) {
			Object v = kv[i + 1];
			if (v != null) o.add((String) kv[i], el(v));
		}
		return o;
	}

	static JsonArray strings(Iterable<String> items) {
		JsonArray a = new JsonArray();
		for (String s : items) a.add(s);
		return a;
	}

	static JsonElement el(Object v) {
		if (v instanceof JsonElement e) return e;
		if (v instanceof String s) return new JsonPrimitive(s);
		if (v instanceof Number n) return new JsonPrimitive(n);
		if (v instanceof Boolean b) return new JsonPrimitive(b);
		throw new IllegalArgumentException("not JSON: " + v);
	}
}
