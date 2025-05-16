package se.metacraft.bosses.entity;

import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.metacraft.bosses.entity.entities.Beam;
import se.metacraft.bosses.entity.entities.FangPursuit;
import se.metacraft.bosses.entity.entities.ItemSpawnerWithTarget;

public class BossEntities {


	public static final EntityType<Beam> LASER = register(
			"beam", EntityType.Builder.create(
					Beam::new, SpawnGroup.MISC
			).dimensions(0, 0)
	);

	public static final EntityType<FangPursuit> FANG_PURSUIT = register(
			"fang_pursuit", EntityType.Builder.create(
					FangPursuit::new, SpawnGroup.MISC
			).dimensions(0, 0).makeFireImmune()
	);

	public static final EntityType<ItemSpawnerWithTarget> OMINOUS_SPAWNER_WITH_TARGET = register(
			"ominous_item_spawner_with_target", EntityType.Builder.create(
					ItemSpawnerWithTarget::new, SpawnGroup.MISC
			).dimensions(0.25f, 0.25f).maxTrackingRange(8)
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
