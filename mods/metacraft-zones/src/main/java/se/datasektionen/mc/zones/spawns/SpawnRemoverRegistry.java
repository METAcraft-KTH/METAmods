package se.datasektionen.mc.zones.spawns;

import com.google.common.collect.Multimap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.predicate.entity.EntityTypePredicate;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.biome.SpawnSettings;
import se.datasektionen.mc.zones.METAcraftZones;

public class SpawnRemoverRegistry {

	public static final Registry<MapCodec<? extends SpawnRemover>> REGISTRY = FabricRegistryBuilder.<MapCodec<? extends SpawnRemover>>createSimple(
			RegistryKey.ofRegistry(METAcraftZones.getID("spawn_remover"))
	).buildAndRegister();

	public static void init() {
		Registry.register(REGISTRY, METAcraftZones.getID("all"), AllSpawnRemover.CODEC);
		Registry.register(REGISTRY, METAcraftZones.getID("types"), TypesSpawnRemover.CODEC);
		Registry.register(REGISTRY, METAcraftZones.getID("spawn_group"), SpawnGroupSpawnRemover.CODEC);
	}

	public interface SpawnRemover {
		Codec<SpawnRemover> REGISTRY_CODEC = REGISTRY.getCodec().dispatch(
				SpawnRemover::getCodec, codec -> codec
		);
		void removeEntities(SpawnGroup spawnGroup, Multimap<EntityType<?>, SpawnSettings.SpawnEntry> spawnsMap);

		MapCodec<? extends SpawnRemover> getCodec();
	}

	public static class AllSpawnRemover implements SpawnRemover {

		public static final AllSpawnRemover INSTANCE = new AllSpawnRemover();
		public static final MapCodec<AllSpawnRemover> CODEC = MapCodec.unit(INSTANCE);

		private AllSpawnRemover() {}

		@Override
		public void removeEntities(SpawnGroup spawnGroup, Multimap<EntityType<?>, SpawnSettings.SpawnEntry> spawnsMap) {
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
		public void removeEntities(SpawnGroup spawnGroup, Multimap<EntityType<?>, SpawnSettings.SpawnEntry> spawnsMap) {
			for (var type : entityType.types()) {
				spawnsMap.removeAll(type.value());
			}
		}

		@Override
		public MapCodec<? extends SpawnRemover> getCodec() {
			return CODEC;
		}
	}

	public record SpawnGroupSpawnRemover(SpawnGroup spawnGroup) implements SpawnRemover {

		public static final MapCodec<SpawnGroupSpawnRemover> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						SpawnGroup.CODEC.fieldOf("spawnGroup").forGetter(SpawnGroupSpawnRemover::spawnGroup)
				).apply(instance, SpawnGroupSpawnRemover::new)
		);

		@Override
		public void removeEntities(SpawnGroup spawnGroup, Multimap<EntityType<?>, SpawnSettings.SpawnEntry> spawnsMap) {
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
