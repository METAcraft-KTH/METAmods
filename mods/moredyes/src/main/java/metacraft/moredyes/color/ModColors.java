package metacraft.moredyes.color;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import metacraft.moredyes.MoreDyes;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Loads {@code colors.json} from the mod jar and holds the resulting colours in file order.
 *
 * The file is the single source of truth shared with {@code tools/gen_assets.py}; both sides fail
 * loudly on anything malformed rather than guessing, per the mod's no-silent-fallback rule.
 */
public final class ModColors {
    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");
    private static final Pattern HEX = Pattern.compile("#?[0-9a-fA-F]{6}");

    private static Map<String, ModColor> byId = Map.of();
    private static List<ModColor> all = List.of();

    private ModColors() {}

    public static List<ModColor> all() {
        return all;
    }

    public static ModColor get(String id) {
        ModColor c = byId.get(id);
        if (c == null) {
            throw new IllegalArgumentException("Unknown More Dyes colour '" + id + "'");
        }
        return c;
    }

    public static ModColor getOrNull(String id) {
        return byId.get(id);
    }

    public static void load() {
        try (InputStream in = MoreDyes.class.getResourceAsStream("/colors.json")) {
            if (in == null) {
                throw new IllegalStateException("colors.json missing from the More Dyes jar");
            }
            JsonElement root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            if (!root.isJsonArray()) {
                throw new IllegalStateException("colors.json must be a JSON array");
            }
            JsonArray arr = root.getAsJsonArray();
            Map<String, ModColor> map = new LinkedHashMap<>();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                String id = required(o, "id");
                if (!ID.matcher(id).matches()) {
                    throw new IllegalStateException("colors.json: id '" + id + "' must match [a-z0-9_]+");
                }
                if (map.containsKey(id)) {
                    throw new IllegalStateException("colors.json: duplicate id '" + id + "'");
                }
                if (o.has("retired") && o.get("retired").getAsBoolean()) {
                    MoreDyes.LOGGER.info("[{}] colour '{}' is retired; keeping its content registered for existing worlds",
                            MoreDyes.MOD_ID, id);
                }
                String name = required(o, "name");
                int rgb = hex(required(o, "rgb"), id + ".rgb");
                // Texture ramp: optional; derived from the base colour (same rule as gen_assets.py) when absent.
                int dark, light;
                if (o.has("ramp")) {
                    JsonArray ramp = o.getAsJsonArray("ramp");
                    if (ramp == null || ramp.size() != 2) {
                        throw new IllegalStateException("colors.json: '" + id + "' \"ramp\" must be [dark, light]");
                    }
                    dark = hex(ramp.get(0).getAsString(), id + ".ramp[0]");
                    light = hex(ramp.get(1).getAsString(), id + ".ramp[1]");
                } else {
                    dark = ModColor.deriveRamp(rgb, true);
                    light = ModColor.deriveRamp(rgb, false);
                }
                map.put(id, ModColor.of(id, name, rgb, dark, light));
            }
            if (map.isEmpty()) {
                throw new IllegalStateException("colors.json defines no colours");
            }
            byId = Collections.unmodifiableMap(map);
            all = List.copyOf(map.values());
            for (ModColor c : all) {
                MoreDyes.LOGGER.info("[{}] colour {} #{} (map colour id {})", MoreDyes.MOD_ID, c.id(),
                        Integer.toHexString(c.rgb()).toUpperCase(), c.mapColor().id);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read colors.json", e);
        }
    }

    private static String required(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive()) {
            throw new IllegalStateException("colors.json: entry missing \"" + key + "\": " + o);
        }
        return o.get(key).getAsString();
    }

    private static int hex(String s, String what) {
        if (!HEX.matcher(s).matches()) {
            throw new IllegalStateException("colors.json: " + what + " must be #RRGGBB, got '" + s + "'");
        }
        return Integer.parseInt(s.startsWith("#") ? s.substring(1) : s, 16);
    }
}
