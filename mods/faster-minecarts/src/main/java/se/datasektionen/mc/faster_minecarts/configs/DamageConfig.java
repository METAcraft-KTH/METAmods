package se.datasektionen.mc.faster_minecarts.configs;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DamageConfig {

	private final Map<EntityType<?>, Boolean> minecartValues = new HashMap<>();

	private final boolean isWhitelist;

	public DamageConfig(List<String> entityDamageBlacklist, boolean isWhitelist) {
		this.isWhitelist = isWhitelist;
		for (String rawEntry : entityDamageBlacklist) {
			String entry = rawEntry;
			boolean passengerCheck = entry.endsWith(">");
			if (passengerCheck) {
				entry = entry.substring(0, entry.length()-1);
			}


			if (entry.startsWith("#")) {
				Registries.ENTITY_TYPE.getOptional(TagKey.of(RegistryKeys.ENTITY_TYPE, Identifier.tryParse(entry.substring(1)))).ifPresent(list -> {
					list.forEach(entity -> {
						minecartValues.put(entity.value(), passengerCheck);
					});
				});
			} else {
				var id = Identifier.tryParse(entry);
				if (Registries.ENTITY_TYPE.containsId(id)) {
					minecartValues.put(Registries.ENTITY_TYPE.get(id), passengerCheck);
				}
			}
		}
	}

	public boolean shouldDamageEntity(Entity target) {
		var result = minecartValues.get(target.getType());
		var root = target.getRootVehicle();
		if (target != root) {
			var rootResult = minecartValues.get(root.getType());
			if (rootResult != null && rootResult) {
				return isWhitelist;
			}
		}
		if (result == null) {
			return !isWhitelist;
		} else {
			return isWhitelist;
		}
	}

}
