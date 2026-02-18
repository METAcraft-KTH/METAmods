package nu.metacraft.core.entity;

import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.core.entity.entities.MovingBlock;
import nu.metacraft.core.entity.entities.MovingMarker;
import nu.metacraft.core.entity.entities.StructureDisplay;
import nu.metacraft.core.entity.entities.player_mob.PlayerMob;
import nu.metacraft.core.entity.ai.METAcraftActivities;
import nu.metacraft.core.entity.ai.METAcraftMemoryModules;
import nu.metacraft.core.entity.ai.METAcraftSensorTypes;
import nu.metacraft.core.mixin.EntityTypeAccessor;

public class METAcraftEntities {

	public static final EntityType<PlayerMob> PLAYER = register(
			"player",
			FabricEntityType.Builder.createMob(
					PlayerMob::new, MobCategory.MISC, builder -> builder.defaultAttributes(
							PlayerMob::createPlayerAttributes
					).spawnPlacement(
							SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
							Monster::checkAnyLightMonsterSpawnRules
					)
			).sized(
					0.6f, 1.8f
			).eyeHeight(1.62f).vehicleAttachment(
					Avatar.DEFAULT_VEHICLE_ATTACHMENT
			).clientTrackingRange(32).updateInterval(2)
	);

	public static final EntityType<StructureDisplay> STRUCTURE_DISPLAY = register(
			"structure_display", EntityType.Builder.of(
					StructureDisplay::new, MobCategory.MISC
			).noLootTable().sized(0, 0)
	);

	public static final EntityType<MovingBlock> MOVING_BLOCK = register(
			"moving_block", EntityType.Builder.of(
					MovingBlock::new, MobCategory.MISC
			).noLootTable().sized(1, 1).updateInterval(1)
	);

	public static final EntityType<MovingMarker> MOVING_MARKER = register(
			"moving_marker", EntityType.Builder.of(
					MovingMarker::new, MobCategory.MISC
			).noLootTable().sized(0,0).clientTrackingRange(0)
	);


	public static void init() {
		METAcraftActivities.init();
		METAcraftMemoryModules.init();
		METAcraftSensorTypes.init();

		((EntityTypeAccessor) PLAYER).setDescriptionId(EntityType.PLAYER.getDescriptionId());
	}

	private static <T extends Entity> EntityType<T> register(String id, EntityType.Builder<T> builder) {
		var key = ResourceKey.create(Registries.ENTITY_TYPE, METAcraftCore.getID(id));
		var type = builder.build(key);
		PolymerEntityUtils.registerType(type);
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
