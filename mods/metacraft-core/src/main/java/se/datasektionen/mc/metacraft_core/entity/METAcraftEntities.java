package se.datasektionen.mc.metacraft_core.entity;

import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.world.Heightmap;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.datasektionen.mc.metacraft_core.entity.entities.PlayerMusicPoint;
import se.datasektionen.mc.metacraft_core.entity.entities.player_mob.PlayerMob;
import se.datasektionen.mc.metacraft_core.entity.ai.METAcraftActivities;
import se.datasektionen.mc.metacraft_core.entity.ai.METAcraftMemoryModules;
import se.datasektionen.mc.metacraft_core.entity.ai.METAcraftSensorTypes;

public class METAcraftEntities {

	public static final EntityType<PlayerMusicPoint> POINT = register(
			"player_music_point", EntityType.Builder.create(
					PlayerMusicPoint::new, SpawnGroup.MISC
			).disableSaving().disableSummon().dimensions(0, 0)
	);

	public static final EntityType<PlayerMob> PLAYER = register(
			"player",
			FabricEntityType.Builder.createMob(
					PlayerMob::new, SpawnGroup.MISC, builder -> builder.defaultAttributes(
							PlayerMob::createPlayerAttributes
					).spawnRestriction(
							SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
							HostileEntity::canSpawnIgnoreLightLevel
					)
			).dimensions(
					0.6f, 1.8f
			).eyeHeight(1.62f).vehicleAttachment(
					PlayerEntity.VEHICLE_ATTACHMENT_POS
			).maxTrackingRange(32).trackingTickInterval(2)
	);


	public static void init() {
		METAcraftActivities.init();
		METAcraftMemoryModules.init();
		METAcraftSensorTypes.init();
	}

	private static <T extends Entity> EntityType<T> register(String id, EntityType.Builder<T> builder) {
		var type = builder.build();
		PolymerEntityUtils.registerType(type);
		return Registry.register(Registries.ENTITY_TYPE, METAcraftCore.getID(id), type);
	}
}
