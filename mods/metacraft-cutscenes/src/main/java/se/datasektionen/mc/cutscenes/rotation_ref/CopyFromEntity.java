package se.datasektionen.mc.cutscenes.rotation_ref;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec2f;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.entity_ref.EntityRef;
import se.datasektionen.mc.cutscenes.registry.EntityRefRegistry;
import se.datasektionen.mc.cutscenes.registry.RotationRefRegistry;

import java.util.Optional;

public class CopyFromEntity implements RotationRef {

	public static final MapCodec<CopyFromEntity> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					EntityRefRegistry.CODEC.fieldOf("entity").forGetter(t -> t.entity)
			).apply(instance, CopyFromEntity::new)
	);

	private final EntityRef entity;

	public CopyFromEntity(EntityRef entity) {
		this.entity = entity;
	}

	@Override
	public Optional<Vec2f> get(@Nullable ServerPlayerEntity player, CutsceneInstance cutsceneInstance) {
		return entity.get(player, cutsceneInstance).findFirst().map(Entity::getRotationClient);
	}

	@Override
	public RotationRefType<?> getType() {
		return RotationRefRegistry.COPY_FROM_ENTITY;
	}
}
