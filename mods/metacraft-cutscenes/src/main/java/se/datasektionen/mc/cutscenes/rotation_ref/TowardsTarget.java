package se.datasektionen.mc.cutscenes.rotation_ref;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.position_ref.PositionRef;
import se.datasektionen.mc.cutscenes.registry.PositionRefRegistry;
import se.datasektionen.mc.cutscenes.registry.RotationRefRegistry;

import java.util.Optional;

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
	public Optional<Vec2f> get(@Nullable ServerPlayerEntity player, CutsceneInstance cutsceneInstance) {
		if (player != null) {
			return target.get(player, cutsceneInstance).map(target -> {
				//Copied from Entity#lookAt
				Vec3d vec3d = player.getEyePos();
				double d = target.x - vec3d.x;
				double e = target.y - vec3d.y;
				double f = target.z - vec3d.z;
				double g = Math.sqrt(d * d + f * f);
				return new Vec2f(
						MathHelper.wrapDegrees((float)(-(MathHelper.atan2(e, g) * (double)(180F / (float)Math.PI)))),
						MathHelper.wrapDegrees((float)(MathHelper.atan2(f, d) * (double)(180F / (float)Math.PI)) - 90.0F)
				);
			});
		}
		return Optional.empty();
	}

	@Override
	public RotationRefType<?> getType() {
		return RotationRefRegistry.TOWARDS_TARGET;
	}
}
