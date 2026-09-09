package metacraft.ovvar.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Map;

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

    static JsonArray arr(Object... items) {
        JsonArray a = new JsonArray();
        for (Object v : items) a.add(el(v));
        return a;
    }

    static JsonArray nums(double... items) {
        JsonArray a = new JsonArray();
        for (double v : items) a.add(v == Math.rint(v) ? new JsonPrimitive((int) v) : new JsonPrimitive(v));
        return a;
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
        if (v instanceof Map<?, ?> m) {
            JsonObject o = new JsonObject();
            m.forEach((k, x) -> o.add((String) k, el(x)));
            return o;
        }
        throw new IllegalArgumentException("not JSON: " + v);
    }

    /** Deep copy with every {@code {c}} in keys and string values replaced by the colour id. */
    static JsonElement sub(JsonElement e, String cid) {
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            return new JsonPrimitive(e.getAsString().replace("{c}", cid));
        }
        if (e.isJsonObject()) {
            JsonObject o = new JsonObject();
            for (var entry : e.getAsJsonObject().entrySet()) {
                o.add(entry.getKey().replace("{c}", cid), sub(entry.getValue(), cid));
            }
            return o;
        }
        if (e.isJsonArray()) {
            JsonArray a = new JsonArray();
            for (JsonElement x : e.getAsJsonArray()) a.add(sub(x, cid));
            return a;
        }
        return e.deepCopy();
    }

    /** {@code {"model": {"type": "minecraft:model", "model": <model>}}} — a plain item definition. */
    static JsonObject itemDef(String model) {
        return obj("model", obj("type", "minecraft:model", "model", model));
    }
}
