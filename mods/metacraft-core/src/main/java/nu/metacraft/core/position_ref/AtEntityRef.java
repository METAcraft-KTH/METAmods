package nu.metacraft.core.position_ref;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.core.entity_ref.EntityRef;
import nu.metacraft.core.registry.EntityRefRegistry;
import nu.metacraft.core.registry.PositionRefRegistry;
import nu.metacraft.core.util.RefContext;

import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class AtEntityRef implements PositionRef {

	public static final MapCodec<AtEntityRef> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					EntityRefRegistry.CODEC.fieldOf("entity").forGetter(ref -> ref.entityRef),
					Vec3.CODEC.optionalFieldOf("axis_offset", Vec3.ZERO).forGetter(ref -> ref.axisOffset),
					Vec3.CODEC.optionalFieldOf("facing_offset", Vec3.ZERO).forGetter(ref -> ref.facingOffset),
					Codec.BOOL.optionalFieldOf("eye_position", false).forGetter(ref -> ref.eyePos)
			).apply(instance, AtEntityRef::new)
	);

	private final EntityRef entityRef;
	private final Vec3 axisOffset;
	private final Vec3 facingOffset;
	private final boolean eyePos;

	public AtEntityRef(EntityRef entityRef, Vec3 axisOffset, Vec3 facingOffset, boolean eyePos) {
		this.entityRef = entityRef;
		this.axisOffset = axisOffset;
		this.facingOffset = facingOffset;
		this.eyePos = eyePos;
	}

	private Vec3 getFeetOrEyePos(Entity entity) {
		if (eyePos) {
			if (entity instanceof LivingEntity living) {
				return living.getEyePosition();
			} else {
				return entity.getBoundingBox().getCenter();
			}
		} else {
			return entity.position();
		}
	}

	@Override
	public Optional<Vec3> get(RefContext ctx) {
		return entityRef.get(ctx).findFirst().map(entity -> getFeetOrEyePos(entity).add(axisOffset).add(
				facingOffset.xRot(-Mth.DEG_TO_RAD * entity.getXRot()).yRot(-Mth.DEG_TO_RAD * entity.getYRot())
		));
	}

	@Override
	public PositionRefType<?> getType() {
		return PositionRefRegistry.ENTITY;
	}
}
