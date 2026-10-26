package se.metacraft.traders;

import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.List;

/** The traders: server-side (Polymer), shown to vanilla clients as BIL display models. */
public final class TraderEntities {

	public static final EntityType<TraderEntity> DATA = register(TraderKind.DATA);
	public static final EntityType<TraderEntity> IT = register(TraderKind.IT);
	public static final EntityType<TraderEntity> MEDIA = register(TraderKind.MEDIA);

	private TraderEntities() {}

	public static void init() {
		for (var type : List.of(DATA, IT, MEDIA)) {
			FabricDefaultAttributeRegistry.register(type, TraderEntity.createAttributes());
		}
	}

	/** A villager's box and eyes. */
	private static EntityType<TraderEntity> register(TraderKind kind) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Traders.getID(kind.entityName()));
		var type = EntityType.Builder.<TraderEntity>of((t, level) -> new TraderEntity(t, level, kind), MobCategory.MISC)
				.sized(0.6f, 1.95f).eyeHeight(1.62f).clientTrackingRange(10).build(key);
		PolymerEntityUtils.registerType(type);
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
