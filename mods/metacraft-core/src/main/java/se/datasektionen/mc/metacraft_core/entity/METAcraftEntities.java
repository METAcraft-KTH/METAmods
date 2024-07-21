package se.datasektionen.mc.metacraft_core.entity;

import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import net.minecraft.entity.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.datasektionen.mc.metacraft_core.entity.entities.PlayerMusicPoint;

public class METAcraftEntities {

	public static final EntityType<PlayerMusicPoint> POINT = register(
			"player_music_point", EntityType.Builder.create(
					PlayerMusicPoint::new, SpawnGroup.MISC
			).disableSaving().disableSummon().dimensions(0, 0).build()
	);


	public static void init() {

	}

	private static <T extends Entity> EntityType<T> register(String id, EntityType<T> type) {
		PolymerEntityUtils.registerType(type);
		return Registry.register(Registries.ENTITY_TYPE, METAcraftCore.getID(id), type);
	}
}
