package nu.metacraft.core.rotation_ref;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec2f;
import nu.metacraft.core.entity_ref.EntityRef;
import nu.metacraft.core.registry.EntityRefRegistry;
import nu.metacraft.core.registry.RotationRefRegistry;
import nu.metacraft.core.util.RefContext;

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
	public Optional<Vec2f> get(RefContext ctx) {
		return entity.get(ctx).findFirst().map(Entity::getRotationClient);
	}

	@Override
	public RotationRefType<?> getType() {
		return RotationRefRegistry.COPY_FROM_ENTITY;
	}
}
