package se.datasektionen.mc.portable_jukebox.entity;

import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import se.datasektionen.mc.portable_jukebox.PortableJukebox;

public class Entities {

	public static final EntityType<PortableJukeboxEntity> PORTABLE_JUKEBOX = register(
			"portable_jukebox", EntityType.Builder.create(PortableJukeboxEntity::new, SpawnGroup.MISC)
					.dimensions(0, 0).disableSummon().disableSaving().maxTrackingRange(8)
	);

	public static void init() {

	}

	private static <T extends Entity> EntityType<T> register(String id, EntityType.Builder<T> entity) {
		var key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, PortableJukebox.getID(id));
		var type = entity.build(key);
		PolymerEntityUtils.registerType(type);
		return Registry.register(Registries.ENTITY_TYPE, key, type);
	}

}
