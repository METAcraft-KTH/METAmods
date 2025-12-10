package nu.metacraft.core.rotation_ref;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.core.position_ref.PositionRef;
import nu.metacraft.core.registry.PositionRefRegistry;
import nu.metacraft.core.registry.RotationRefRegistry;
import nu.metacraft.core.util.RefContext;

import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class TowardsTarget implements RotationRef {

	public static final MapCodec<TowardsTarget> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					PositionRefRegistry.CODEC.fieldOf("target").forGetter(t -> t.target)
			).apply(instance, TowardsTarget::new)
	);

	private final PositionRef target;

	public TowardsTarget(PositionRef target) {
		this.target = target;
	}

	@Override
	public Optional<Vec2> get(RefContext ctx) {
		var entity = ctx.entity();
		return entity.flatMap(value -> target.get(ctx).map(target -> {
			//Copied from Entity#lookAt
			Vec3 vec3d = value.getEyePosition();
			double d = target.x - vec3d.x;
			double e = target.y - vec3d.y;
			double f = target.z - vec3d.z;
			double g = Math.sqrt(d * d + f * f);
			return new Vec2(
					Mth.wrapDegrees((float) (-(Mth.atan2(e, g) * (double) (180F / (float) Math.PI)))),
					Mth.wrapDegrees((float) (Mth.atan2(f, d) * (double) (180F / (float) Math.PI)) - 90.0F)
			);
		}));
	}

	@Override
	public RotationRefType<?> getType() {
		return RotationRefRegistry.TOWARDS_TARGET;
	}
}
