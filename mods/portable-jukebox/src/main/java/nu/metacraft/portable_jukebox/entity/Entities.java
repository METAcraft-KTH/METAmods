package nu.metacraft.portable_jukebox.entity;

import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import nu.metacraft.portable_jukebox.PortableJukebox;

public class Entities {

	public static final EntityType<PortableJukeboxEntity> PORTABLE_JUKEBOX = register(
			"portable_jukebox", EntityType.Builder.of(PortableJukeboxEntity::new, MobCategory.MISC)
					.sized(0, 0).noSummon().noSave().clientTrackingRange(8)
	);

	public static void init() {

	}

	private static <T extends Entity> EntityType<T> register(String id, EntityType.Builder<T> entity) {
		var key = ResourceKey.create(Registries.ENTITY_TYPE, PortableJukebox.getID(id));
		var type = entity.build(key);
		PolymerEntityUtils.registerType(type);
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}

}
