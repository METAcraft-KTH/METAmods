package nu.metacraft.cutscenes.cutscene.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.cutscenes.transitions.entity.SpawnEntity;
import nu.metacraft.cutscenes.util.SerialisedStructure;

import java.util.*;
import net.minecraft.nbt.CompoundTag;

public record CutsceneWorldData(
		CutsceneEntityManager.SaveState entities, SerialisedStructure blocks,
		CompoundTag saveProperties, CompoundTag persistentStateStorage
) {

	public static final Codec<CutsceneWorldData> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					CutsceneEntityManager.SaveState.CODEC.forGetter(d -> d.entities),
					SerialisedStructure.CODEC.fieldOf("blocks").forGetter(d -> d.blocks),
					CompoundTag.CODEC.optionalFieldOf("save_properties", new CompoundTag()).forGetter(d -> d.saveProperties),
					CompoundTag.CODEC.optionalFieldOf("persistent_state_storage", new CompoundTag()).forGetter(d -> d.persistentStateStorage)
			).apply(instance, CutsceneWorldData::new)
	);

	public record SerialisedEntity(List<String> ids, CompoundTag data) {
		public static final Codec<SerialisedEntity> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.STRING.listOf().fieldOf("ids").forGetter(SerialisedEntity::ids),
						CompoundTag.CODEC.fieldOf("data").forGetter(SerialisedEntity::data)
				).apply(instance, SerialisedEntity::new)
		);

		public void load(CutsceneLevel world) {
			SpawnEntity.spawnEntities(
					ids, data, Optional.empty(), world, Optional.of(false)
			);
		}
	}
}
