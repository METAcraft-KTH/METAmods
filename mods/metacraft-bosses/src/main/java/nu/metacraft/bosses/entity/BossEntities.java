package nu.metacraft.bosses.entity;

import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.bosses.entity.entities.Beam;
import nu.metacraft.bosses.entity.entities.FangPursuit;
import nu.metacraft.bosses.entity.entities.ItemSpawnerWithTarget;

public class BossEntities {


	public static final EntityType<Beam> LASER = register(
			"beam", EntityType.Builder.of(
					Beam::new, MobCategory.MISC
			).sized(0, 0)
	);

	public static final EntityType<FangPursuit> FANG_PURSUIT = register(
			"fang_pursuit", EntityType.Builder.of(
					FangPursuit::new, MobCategory.MISC
			).sized(0, 0).fireImmune()
	);

	public static final EntityType<ItemSpawnerWithTarget> OMINOUS_SPAWNER_WITH_TARGET = register(
			"ominous_item_spawner_with_target", EntityType.Builder.of(
					ItemSpawnerWithTarget::new, MobCategory.MISC
			).sized(0.25f, 0.25f).clientTrackingRange(8)
	);

	public static void init() {

	}

	private static <T extends Entity> EntityType<T> register(String id, EntityType.Builder<T> builder) {
		var key = ResourceKey.create(Registries.ENTITY_TYPE, METAcraftCore.getID(id));
		var type = builder.build(key);
		PolymerEntityUtils.registerType(type);
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}

}
