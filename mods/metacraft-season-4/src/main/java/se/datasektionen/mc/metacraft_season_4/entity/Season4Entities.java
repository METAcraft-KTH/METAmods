package se.datasektionen.mc.metacraft_season_4.entity;

import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.datasektionen.mc.metacraft_season_4.entity.entities.Beam;

public class Season4Entities {


	public static final EntityType<Beam> LASER = register(
			"beam", EntityType.Builder.create(
					Beam::new, SpawnGroup.MISC
			).dimensions(0, 0)
	);

	public static void init() {

	}

	private static <T extends Entity> EntityType<T> register(String id, EntityType.Builder<T> builder) {
		var key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, METAcraftCore.getID(id));
		var type = builder.build(key);
		PolymerEntityUtils.registerType(type);
		return Registry.register(Registries.ENTITY_TYPE, key, type);
	}

}
