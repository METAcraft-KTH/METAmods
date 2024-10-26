package se.datasektionen.mc.faster_minecarts.configs;

import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import se.datasektionen.mc.faster_minecarts.FasterMinecarts;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NumberedRegistryEntry<T> {

	private final Map<T, Double> objectTypes = new HashMap<>();
	private double defaultValue = Double.NaN;

	public NumberedRegistryEntry(List<? extends String> config, Registry<T> registry, String splitter) {
		for (String value : config) {
			String[] entry = value.split(splitter);
			try {
				if (entry.length == 1) {
					defaultValue = Double.parseDouble(entry[0]);
				} else if (entry.length == 2) {
					double factor = Double.parseDouble(entry[1]);
					String match = entry[0];
					if (match.startsWith("#")) {
						registry.getOptional(
								TagKey.of(registry.getKey(), Identifier.tryParse(match.substring(1)))
						).ifPresent(list -> {
							list.forEach(entity -> {
								objectTypes.put(entity.value(), factor);
							});
						});
					} else {
						var id = Identifier.tryParse(match);
						if (registry.containsId(id)) {
							objectTypes.put(registry.get(id), factor);
						}
					}
				} else {
					FasterMinecarts.logger.error(value + " is not valid!");
				}
			} catch (NumberFormatException ignored) {
				FasterMinecarts.logger.error(value + " is not valid!");
			}
		}
	}

	public double getValue(T object) {
		return objectTypes.getOrDefault(object, defaultValue);
	}

}
