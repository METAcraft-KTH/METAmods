package nu.metacraft.lib.config;

import blue.endless.jankson.*;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import nu.metacraft.lib.config.comments.ops.OpsWithComments;
import nu.metacraft.lib.config.comments.ops.WrappingRecordBuilderWithComments;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;

// Heavily copied from JsonOps
public class JanksonOps implements OpsWithComments<JsonElement> {

	public static final JanksonOps INSTANCE = new JanksonOps();

	@Override
	public JsonElement empty() {
		return JsonNull.INSTANCE;
	}

	@Override
	public JsonElement emptyMap() {
		return new JsonObject();
	}

	@Override
	public JsonElement emptyList() {
		return new JsonArray();
	}

	@Override
	public <U> U convertTo(DynamicOps<U> outOps, JsonElement input) {
		if (input instanceof JsonObject) {
			return convertMap(outOps, input);
		}
		if (input instanceof JsonArray) {
			return convertList(outOps, input);
		}
		if (input instanceof JsonNull) {
			return outOps.empty();
		}
		if (input instanceof JsonPrimitive primitive) {
			return switch (primitive.getValue()) {
				case String string -> outOps.createString(string);
				case Boolean bool -> outOps.createBoolean(bool);
				case Number n -> {
					final BigDecimal value = BigDecimal.valueOf(n.doubleValue());
					try {
						final long l = value.longValueExact();
						if ((byte) l == l) {
							yield outOps.createByte((byte) l);
						}
						if ((short) l == l) {
							yield outOps.createShort((short) l);
						}
						if ((int) l == l) {
							yield outOps.createInt((int) l);
						}
						yield outOps.createLong(l);
					} catch (final ArithmeticException e) {
						final double d = value.doubleValue();
						if ((float) d == d) {
							yield outOps.createFloat((float) d);
						}
						yield outOps.createDouble(d);
					}
				}
				default -> outOps.empty();
			};
		}
		return outOps.empty();
	}

	@Override
	public DataResult<Number> getNumberValue(JsonElement input) {
		if (input instanceof JsonPrimitive primitive) {
			if (primitive.getValue() instanceof Number n) {
				return DataResult.success(n);
			}
		}
		return DataResult.error(() -> "Not a number: " + input);
	}

	@Override
	public JsonElement createNumeric(Number i) {
		return new JsonPrimitive(i);
	}

	@Override
	public DataResult<Boolean> getBooleanValue(JsonElement input) {
		if (input instanceof JsonPrimitive primitive) {
			if (primitive.getValue() instanceof Boolean bool) {
				return DataResult.success(bool);
			}
		}
		return DataResult.error(() -> "Not a boolean: " + input);
	}

	@Override
	public JsonElement createBoolean(boolean value) {
		return new JsonPrimitive(value);
	}

	@Override
	public DataResult<String> getStringValue(JsonElement input) {
		if (input instanceof JsonPrimitive primitive) {
			if (primitive.getValue() instanceof String string) {
				return DataResult.success(string);
			}
		}
		return DataResult.error(() -> "Not a string: " + input);
	}

	@Override
	public JsonElement createString(String value) {
		return new JsonPrimitive(value);
	}

	private static DataResult<JsonElement> notAList(JsonElement list) {
		return DataResult.error(() -> "mergeToList called with not a list: " + list, list);
	}

	private static JsonArray copy(JsonArray array) {
		var newArray = new JsonArray();
		newArray.addAll(array);
		return newArray;
	}

	private static JsonArray withValue(JsonArray array, JsonElement value) {
		array.add(value);
		return array;
	}
	private static JsonArray withValues(JsonArray array, List<JsonElement> values) {
		array.addAll(values);
		return array;
	}

	@Override
	public DataResult<JsonElement> mergeToList(JsonElement list, JsonElement value) {
		if (list instanceof JsonArray array) {
			return DataResult.success(withValue(copy(array), value));
		} else if (list == empty()) {
			return DataResult.success(withValue(new JsonArray(), value));
		} else {
			return notAList(list);
		}
	}

	@Override
	public DataResult<JsonElement> mergeToList(JsonElement list, List<JsonElement> values) {
		if (list instanceof JsonArray array) {
			if (values.isEmpty()) {
				return DataResult.success(list);
			}
			return DataResult.success(withValues(copy(array), values));
		} else if (list == empty()) {
			if (values.isEmpty()) {
				return DataResult.success(emptyList());
			}
			return DataResult.success(withValues(new JsonArray(), values));
		} else {
			return notAList(list);
		}
	}

	private static DataResult<JsonElement> notAMap(JsonElement map) {
		return DataResult.error(() -> "mergeToMap called with not a map: " + map, map);
	}

	private static JsonObject copy(JsonObject object) {
		var newObject = new JsonObject();
		newObject.putAll(object);
		return newObject;
	}

	private static DataResult<JsonElement> keyError(JsonElement key, JsonElement map) {
		return DataResult.error(() -> "key is not a string: " + key, map);
	}

	private static JsonObject put(JsonObject map, String key, JsonElement value) {
		map.put(key, value);
		return map;
	}

	@Override
	public DataResult<JsonElement> mergeToMap(JsonElement map, JsonElement key, JsonElement value) {
		if (map instanceof JsonObject object) {
			if (key instanceof JsonPrimitive primitive && primitive.getValue() instanceof String string) {
				return DataResult.success(put(copy(object), string, value));
			} else {
				return keyError(map, key);
			}
		} else if (map == empty()) {
			if (key instanceof JsonPrimitive primitive && primitive.getValue() instanceof String string) {
				return DataResult.success(put(new JsonObject(), string, value));
			} else {
				return keyError(map, key);
			}
		} else {
			return notAMap(map);
		}
	}

	private static DataResult<JsonElement> putAll(JsonObject map, Iterator<Pair<JsonElement, JsonElement>> valuesIterator) {
		List<JsonElement> missed = new ArrayList<>();
		valuesIterator.forEachRemaining(pair -> {
			if (pair.getFirst() instanceof JsonPrimitive primitive && primitive.getValue() instanceof String string) {
				map.put(string, pair.getSecond());
			} else {
				missed.add(pair.getFirst());
			}
		});
		if (missed.isEmpty()) {
			return DataResult.success(map);
		}
		return DataResult.error(() -> "some keys are not strings: " + missed, map);
	}

	@Override
	public DataResult<JsonElement> mergeToMap(final JsonElement map, final MapLike<JsonElement> values) {
		if (map instanceof JsonObject object) {
			var it = values.entries().iterator();
			if (!it.hasNext()) {
				return DataResult.success(map);
			}
			return putAll(copy(object), it);
		} else if (map == empty()) {
			var it = values.entries().iterator();
			if (!it.hasNext()) {
				return DataResult.success(emptyMap());
			}
			return putAll(new JsonObject(), it);
		} else {
			return notAMap(map);
		}
	}

	@Override
	public DataResult<Stream<Pair<JsonElement, JsonElement>>> getMapValues(JsonElement input) {
		if (input instanceof JsonObject object) {
			return DataResult.success(object.entrySet().stream().map(
				entry -> Pair.of(
					new JsonPrimitive(entry.getKey()),
					entry.getValue() instanceof JsonNull ? null : entry.getValue()
				)
			));
		}
		return DataResult.error(() -> "Not a JSON object: " + input);
	}

	@Override
	public JsonElement createMap(Stream<Pair<JsonElement, JsonElement>> map) {
		JsonObject object = new JsonObject();
		map.forEach(p -> {
			if (p.getFirst() instanceof JsonPrimitive prim) {
				object.put(prim.asString(), p.getSecond());
			}
		});
		return object;
	}

	@Override
	public DataResult<Stream<JsonElement>> getStream(JsonElement input) {
		if (input instanceof JsonArray array) {
			return DataResult.success(array.stream().map(e -> e instanceof JsonNull ? null : e));
		}
		return DataResult.error(() -> "Not a json array: " + input);
	}

	@Override
	public JsonElement createList(Stream<JsonElement> input) {
		final JsonArray result = new JsonArray();
		input.forEach(result::add);
		return result;
	}

	@Override
	public JsonElement remove(JsonElement input, String key) {
		if (input instanceof JsonObject object) {
			final JsonObject result = new JsonObject();
			object.entrySet().stream().filter(
				entry -> !Objects.equals(entry.getKey(), key)
			).forEach(entry -> result.put(entry.getKey(), entry.getValue()));
			return result;
		}
		return input;
	}

	@Override
	public String toString() {
		return "Jankson";
	}

	@Override
	public DataResult<JsonElement> metacraft$withComments(JsonElement value, Map<JsonElement, String> comments) {
		if (value instanceof JsonObject object) {
			List<JsonElement> missed = new ArrayList<>();
			var newCopy = copy(object);
			for (var comment : comments.entrySet()) {
				if (comment.getKey() instanceof JsonPrimitive p && p.getValue() instanceof String s) {
					newCopy.setComment(s, comment.getValue());
				} else {
					missed.add(comment.getKey());
				}
			}
			if (missed.isEmpty()) {
				return DataResult.success(newCopy);
			}
			return DataResult.error(() -> "Missed comment keys " + missed, newCopy);
		}
		return notAMap(value);
	}

	@Override
	public DataResult<JsonElement> metacraft$withComments(JsonElement value, Int2ObjectMap<String> comments) {
		if (value instanceof JsonArray array) {
			var newCopy = copy(array);
			for (var comment : comments.int2ObjectEntrySet()) {
				newCopy.setComment(comment.getIntKey(), comment.getValue());
			}
			return DataResult.success(newCopy);
		}
		return notAList(value);
	}

	@Override
	public RecordBuilder<JsonElement> mapBuilder() {
		return new WrappingRecordBuilderWithComments<>(OpsWithComments.super.mapBuilder());
	}
}
