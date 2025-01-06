package se.datasektionen.mc.cutscenes.cutscene.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.NbtCompound;
import se.datasektionen.mc.cutscenes.transitions.entity.SpawnEntity;
import se.datasektionen.mc.cutscenes.util.SerialisedStructure;

import java.util.*;

public record CutsceneWorldData(
		List<SerialisedEntity> entities, SerialisedStructure blocks,
		NbtCompound saveProperties, NbtCompound persistentStateStorage
) {
	public static final Codec<CutsceneWorldData> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					SerialisedEntity.CODEC.listOf().fieldOf("entities").forGetter(d -> d.entities),
					SerialisedStructure.CODEC.fieldOf("blocks").forGetter(d -> d.blocks),
					NbtCompound.CODEC.optionalFieldOf("save_properties", new NbtCompound()).forGetter(d -> d.saveProperties),
					NbtCompound.CODEC.optionalFieldOf("persistent_state_storage", new NbtCompound()).forGetter(d -> d.persistentStateStorage)
			).apply(instance, CutsceneWorldData::new)
	);

	public record SerialisedEntity(List<String> ids, NbtCompound data) {
		public static final Codec<SerialisedEntity> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.STRING.listOf().fieldOf("ids").forGetter(SerialisedEntity::ids),
						NbtCompound.CODEC.fieldOf("data").forGetter(SerialisedEntity::data)
				).apply(instance, SerialisedEntity::new)
		);

		public void load(CutsceneWorld world) {
			SpawnEntity.spawnEntities(
					ids, data, Optional.empty(), world, Optional.of(false)
			);
		}
	}
}
