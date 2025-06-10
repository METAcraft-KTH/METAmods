package se.datasektionen.mc.metacraft_season_4.entity;

import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.Heightmap;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.datasektionen.mc.metacraft_season_4.entity.ai.Season4MemoryModules;
import se.datasektionen.mc.metacraft_season_4.entity.ai.Season4Sensors;
import se.datasektionen.mc.metacraft_season_4.entity.entities.MagicProjectile;
import se.datasektionen.mc.metacraft_season_4.entity.entities.bosses.*;

public class Season4Entities {

	public static final EntityType<DevinBossEntity> DEVIN_BOSS = register(
			"devin_boss",
			FabricEntityType.Builder.createMob(
					DevinBossEntity::new, SpawnGroup.MISC, builder -> builder.defaultAttributes(
							DevinBossEntity::createBossAttributes
					).spawnRestriction(
							SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
							HostileEntity::canSpawnIgnoreLightLevel
					)
			).dimensions(
					0.6f, 1.8f
			).eyeHeight(1.62f).vehicleAttachment(
					PlayerEntity.VEHICLE_ATTACHMENT_POS
			).maxTrackingRange(32).trackingTickInterval(2).makeFireImmune()
	);

	public static final EntityType<AvolineBossEntity> AVOLINE_BOSS = register(
			"avoline_boss",
			FabricEntityType.Builder.createMob(
					AvolineBossEntity::new, SpawnGroup.MISC, builder -> builder.defaultAttributes(
							AvolineBossEntity::createBossAttributes
					).spawnRestriction(
							SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
							HostileEntity::canSpawnIgnoreLightLevel
					)
			).dimensions(
					0.6f, 1.8f
			).eyeHeight(1.62f).vehicleAttachment(
					PlayerEntity.VEHICLE_ATTACHMENT_POS
			).maxTrackingRange(32).trackingTickInterval(2).makeFireImmune()
	);


	public static final EntityType<GiocatBossEntity> GIOCAT_BOSS = register(
			"giocat_boss",
			FabricEntityType.Builder.createMob(
					GiocatBossEntity::new, SpawnGroup.MISC, builder -> builder.defaultAttributes(
							GiocatBossEntity::createBossAttributes
					).spawnRestriction(
							SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
							HostileEntity::canSpawnIgnoreLightLevel
					)
			).dimensions(
					0.6f, 1.8f
			).eyeHeight(1.62f).vehicleAttachment(
					PlayerEntity.VEHICLE_ATTACHMENT_POS
			).maxTrackingRange(32).trackingTickInterval(2).makeFireImmune()
	);

	public static final EntityType<WilliamBossEntity> WILLIAM_BOSS = register(
			"william_boss",
			FabricEntityType.Builder.createMob(
					WilliamBossEntity::new, SpawnGroup.MISC, builder -> builder.defaultAttributes(
							WilliamBossEntity::createBossAttributes
					).spawnRestriction(
							SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
							HostileEntity::canSpawnIgnoreLightLevel
					)
			).dimensions(
					0.6f, 1.8f
			).eyeHeight(1.62f).vehicleAttachment(
					PlayerEntity.VEHICLE_ATTACHMENT_POS
			).maxTrackingRange(32).trackingTickInterval(2).makeFireImmune()
	);


	public static final EntityType<MagicProjectile> MAGIC_PROJECTILE = register(
			"magic_projectile",
			EntityType.Builder.create(
					MagicProjectile::new, SpawnGroup.MISC
			).dimensions(1.0f, 1.0f).maxTrackingRange(4).trackingTickInterval(10)
	);

	public static final EntityType<PollyBossEntity> POLLY_BOSS = register(
			"polly_boss",
			FabricEntityType.Builder.createMob(
					PollyBossEntity::new, SpawnGroup.MISC, builder -> builder.defaultAttributes(
							PollyBossEntity::createBossPollyAttributes
					).spawnRestriction(
							SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
							TameableEntity::canMobSpawn
					)
			).dimensions(
					0.5F, 0.9F
			).eyeHeight(0.54F).passengerAttachments(
					0.4625F
			).maxTrackingRange(32).makeFireImmune()
	);


	public static void init() {
		Season4MemoryModules.init();
		Season4Sensors.init();
	}

	private static <T extends Entity> EntityType<T> register(String id, EntityType.Builder<T> builder) {
		var key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, METAcraftCore.getID(id));
		var type = builder.build(key);
		PolymerEntityUtils.registerType(type);
		return Registry.register(Registries.ENTITY_TYPE, key, type);
	}

}
