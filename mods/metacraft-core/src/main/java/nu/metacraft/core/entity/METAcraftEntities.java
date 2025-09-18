package nu.metacraft.core.entity;

import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.Heightmap;
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.core.entity.entities.MovingBlock;
import nu.metacraft.core.entity.entities.MovingMarker;
import nu.metacraft.core.entity.entities.StructureDisplay;
import nu.metacraft.core.entity.entities.player_mob.PlayerMob;
import nu.metacraft.core.entity.ai.METAcraftActivities;
import nu.metacraft.core.entity.ai.METAcraftMemoryModules;
import nu.metacraft.core.entity.ai.METAcraftSensorTypes;
import nu.metacraft.core.mixin.AccessorEntityType;

public class METAcraftEntities {

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
					PlayerLikeEntity.VEHICLE_ATTACHMENT
			).maxTrackingRange(32).trackingTickInterval(2)
	);

	public static final EntityType<StructureDisplay> STRUCTURE_DISPLAY = register(
			"structure_display", EntityType.Builder.create(
					StructureDisplay::new, SpawnGroup.MISC
			).dropsNothing().dimensions(0, 0)
	);

	public static final EntityType<MovingBlock> MOVING_BLOCK = register(
			"moving_block", EntityType.Builder.create(
					MovingBlock::new, SpawnGroup.MISC
			).dropsNothing().dimensions(1, 1).trackingTickInterval(1)
	);

	public static final EntityType<MovingMarker> MOVING_MARKER = register(
			"moving_marker", EntityType.Builder.create(
					MovingMarker::new, SpawnGroup.MISC
			).dropsNothing().dimensions(0,0).maxTrackingRange(0)
	);


	public static void init() {
		METAcraftActivities.init();
		METAcraftMemoryModules.init();
		METAcraftSensorTypes.init();

		((AccessorEntityType) PLAYER).setTranslationKey(EntityType.PLAYER.getTranslationKey());
	}

	private static <T extends Entity> EntityType<T> register(String id, EntityType.Builder<T> builder) {
		var key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, METAcraftCore.getID(id));
		var type = builder.build(key);
		PolymerEntityUtils.registerType(type);
		return Registry.register(Registries.ENTITY_TYPE, key, type);
	}
}
