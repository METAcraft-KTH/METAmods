package se.datasektionen.mc.cutscenes.position_ref;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import se.datasektionen.mc.cutscenes.entity_ref.EntityRef;
import se.datasektionen.mc.cutscenes.registry.EntityRefRegistry;
import se.datasektionen.mc.cutscenes.registry.PositionRefRegistry;
import se.datasektionen.mc.cutscenes.util.RefContext;

import java.util.Optional;

public class AtEntityRef implements PositionRef {

	public static final MapCodec<AtEntityRef> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					EntityRefRegistry.CODEC.fieldOf("entity").forGetter(ref -> ref.entityRef),
					Vec3d.CODEC.optionalFieldOf("axis_offset", Vec3d.ZERO).forGetter(ref -> ref.axisOffset),
					Vec3d.CODEC.optionalFieldOf("facing_offset", Vec3d.ZERO).forGetter(ref -> ref.facingOffset),
					Codec.BOOL.optionalFieldOf("eye_position", false).forGetter(ref -> ref.eyePos)
			).apply(instance, AtEntityRef::new)
	);

	private final EntityRef entityRef;
	private final Vec3d axisOffset;
	private final Vec3d facingOffset;
	private final boolean eyePos;

	public AtEntityRef(EntityRef entityRef, Vec3d axisOffset, Vec3d facingOffset, boolean eyePos) {
		this.entityRef = entityRef;
		this.axisOffset = axisOffset;
		this.facingOffset = facingOffset;
		this.eyePos = eyePos;
	}

	private Vec3d getFeetOrEyePos(Entity entity) {
		if (eyePos) {
			if (entity instanceof LivingEntity living) {
				return living.getEyePos();
			} else {
				return entity.getBoundingBox().getCenter();
			}
		} else {
			return entity.getPos();
		}
	}

	@Override
	public Optional<Vec3d> get(RefContext ctx) {
		return entityRef.get(ctx).findFirst().map(entity -> getFeetOrEyePos(entity).add(axisOffset).add(
				facingOffset.rotateX(-MathHelper.RADIANS_PER_DEGREE * entity.getPitch()).rotateY(-MathHelper.RADIANS_PER_DEGREE * entity.getYaw())
		));
	}

	@Override
	public PositionRefType<?> getType() {
		return PositionRefRegistry.ENTITY;
	}
}
