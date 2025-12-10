package nu.metacraft.zones.spawns;

import com.google.common.collect.Multimap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.advancements.criterion.EntityTypePredicate;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import nu.metacraft.zones.METAcraftZones;

public class SpawnRemoverRegistry {

	public static final Registry<MapCodec<? extends SpawnRemover>> REGISTRY = FabricRegistryBuilder.<MapCodec<? extends SpawnRemover>>createSimple(
			ResourceKey.createRegistryKey(METAcraftZones.getID("spawn_remover"))
	).buildAndRegister();

	public static void init() {
		Registry.register(REGISTRY, METAcraftZones.getID("all"), AllSpawnRemover.CODEC);
		Registry.register(REGISTRY, METAcraftZones.getID("types"), TypesSpawnRemover.CODEC);
		Registry.register(REGISTRY, METAcraftZones.getID("spawn_group"), SpawnGroupSpawnRemover.CODEC);
	}

	public interface SpawnRemover {
		Codec<SpawnRemover> REGISTRY_CODEC = REGISTRY.byNameCodec().dispatch(
				SpawnRemover::getCodec, codec -> codec
		);
		void removeEntities(MobCategory spawnGroup, Multimap<EntityType<?>, Weighted<MobSpawnSettings.SpawnerData>> spawnsMap);

		MapCodec<? extends SpawnRemover> getCodec();
	}

	public static class AllSpawnRemover implements SpawnRemover {

		public static final AllSpawnRemover INSTANCE = new AllSpawnRemover();
		public static final MapCodec<AllSpawnRemover> CODEC = MapCodec.unit(INSTANCE);

		private AllSpawnRemover() {}

		@Override
		public void removeEntities(MobCategory spawnGroup, Multimap<EntityType<?>, Weighted<MobSpawnSettings.SpawnerData>> spawnsMap) {
			spawnsMap.clear();
		}

		@Override
		public MapCodec<? extends SpawnRemover> getCodec() {
			return CODEC;
		}
	}

	public record TypesSpawnRemover(EntityTypePredicate entityType) implements SpawnRemover {

		public static final MapCodec<TypesSpawnRemover> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						EntityTypePredicate.CODEC.fieldOf("entity").forGetter(TypesSpawnRemover::entityType)
				).apply(instance, TypesSpawnRemover::new)
		);

		@Override
		public void removeEntities(MobCategory spawnGroup, Multimap<EntityType<?>, Weighted<MobSpawnSettings.SpawnerData>> spawnsMap) {
			for (var type : entityType.types()) {
				spawnsMap.removeAll(type.value());
			}
		}

		@Override
		public MapCodec<? extends SpawnRemover> getCodec() {
			return CODEC;
		}
	}

	public record SpawnGroupSpawnRemover(MobCategory spawnGroup) implements SpawnRemover {

		public static final MapCodec<SpawnGroupSpawnRemover> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						MobCategory.CODEC.fieldOf("spawnGroup").forGetter(SpawnGroupSpawnRemover::spawnGroup)
				).apply(instance, SpawnGroupSpawnRemover::new)
		);

		@Override
		public void removeEntities(MobCategory spawnGroup, Multimap<EntityType<?>, Weighted<MobSpawnSettings.SpawnerData>> spawnsMap) {
			if (spawnGroup == this.spawnGroup) {
				spawnsMap.clear();
			}
		}

		@Override
		public MapCodec<? extends SpawnRemover> getCodec() {
			return CODEC;
		}
	}

}
