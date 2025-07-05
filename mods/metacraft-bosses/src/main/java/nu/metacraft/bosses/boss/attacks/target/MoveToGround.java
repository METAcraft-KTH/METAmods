package nu.metacraft.bosses.boss.attacks.target;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.floatprovider.FloatProvider;
import net.minecraft.world.Heightmap;
import nu.metacraft.bosses.boss.attacks.Attack;
import nu.metacraft.bosses.boss.attacks.AttackRegistry;

import java.util.Optional;

public class MoveToGround extends PositionTargetSelector {

	public static final MapCodec<MoveToGround> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					FloatProvider.createValidatedCodec(0, Float.MAX_VALUE).fieldOf("range").forGetter(m -> m.range)
			).apply(instance, MoveToGround::new)
	);

	private final FloatProvider range;

	public MoveToGround(FloatProvider range) {
		this.range = range;
	}

	@Override
	public Optional<Vec3d> getTarget(Attack.BossContext<?> ctx) {
		float yaw = ctx.random().nextFloat() * 360 - 180;
		float pitch = ctx.random().nextFloat() * 45;
		var direction = Vec3d.fromPolar(pitch, yaw);
		var offset = direction.multiply(range.get(ctx.random()));
		Vec3d pos = ctx.boss().getPos().add(offset);
		int groundY = ctx.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(pos.x), MathHelper.floor(pos.z));
		if (pos.y < groundY) {
			pos = new Vec3d(pos.x, groundY, pos.z);
		}
		return Optional.of(pos);
	}

	@Override
	public PositionTargetSelectorType getType() {
		return AttackRegistry.PTS.MOVE_TO_GROUND;
	}
}
