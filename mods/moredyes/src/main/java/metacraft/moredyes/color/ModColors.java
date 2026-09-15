package metacraft.moredyes.color;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import metacraft.moredyes.MoreDyes;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Loads {@code colors.json} from the mod jar and holds the resulting colours in file order.
 *
 * The file is the single source of truth shared with {@code tools/gen_assets.py}; both sides fail
 * loudly on anything malformed rather than guessing, per the mod's no-silent-fallback rule.
 */
public final class ModColors {
	private static final Pattern HEX = Pattern.compile("#?[0-9a-fA-F]{6}");

	public static final Codec<Map<String, ModColor>> COLOUR_MAP_CODEC = ModColor.CODEC.listOf().comapFlatMap(
			colours -> {
				Map<String, ModColor> colourMap = new HashMap<>();
				StringBuilder error = new StringBuilder();
				for (var colour : colours) {
					if (colourMap.containsKey(colour.id())) {
						String msg = "duplicate id " + colour.id();
						if (error.isEmpty()) {
							error.append(msg);
						} else {
							error.append("; ").append(msg);
						}
					}
					colourMap.put(colour.id(), colour);
				}
				if (error.isEmpty()) {
					return DataResult.success(colourMap);
				} else {
					return DataResult.error(error::toString, colourMap);
				}
			},
			colours -> colours.values().stream().toList()
	);

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
			var map = COLOUR_MAP_CODEC.parse(JsonOps.INSTANCE, root).getOrThrow(
					err -> new IllegalStateException("colors.json: " + err)
			);

			if (!root.isJsonArray()) {
				throw new IllegalStateException("colors.json must be a JSON array");
			}
			if (map.isEmpty()) {
				throw new IllegalStateException("colors.json defines no colours");
			}
			byId = map;
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
}
