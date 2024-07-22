package se.datasektionen.mc.portable_jukebox.entity;

import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import se.datasektionen.mc.portable_jukebox.PortableJukebox;

public class Entities {

	public static final EntityType<PortableJukeboxEntity> PORTABLE_JUKEBOX = register(
			"portable_jukebox", EntityType.Builder.create(PortableJukeboxEntity::new, SpawnGroup.MISC)
					.dimensions(0, 0).disableSummon().disableSaving().maxTrackingRange(8).build()
	);

	public static void init() {

	}

	private static <T extends EntityType<?>> T register(String id, T entity) {
		PolymerEntityUtils.registerType(entity);
		return Registry.register(Registries.ENTITY_TYPE, PortableJukebox.getID(id), entity);
	}

}
