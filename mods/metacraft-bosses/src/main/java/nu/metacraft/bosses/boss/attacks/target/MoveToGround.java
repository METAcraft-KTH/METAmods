package nu.metacraft.bosses.boss.attacks.target;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.bosses.boss.attacks.Attack;
import nu.metacraft.bosses.boss.attacks.AttackRegistry;

import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public class MoveToGround extends PositionTargetSelector {

	public static final MapCodec<MoveToGround> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					FloatProvider.codec(0, Float.MAX_VALUE).fieldOf("range").forGetter(m -> m.range)
			).apply(instance, MoveToGround::new)
	);

	private final FloatProvider range;

	public MoveToGround(FloatProvider range) {
		this.range = range;
	}

	@Override
	public Optional<Vec3> getTarget(Attack.BossContext<?> ctx) {
		float yaw = ctx.random().nextFloat() * 360 - 180;
		float pitch = ctx.random().nextFloat() * 45;
		var direction = Vec3.directionFromRotation(pitch, yaw);
		var offset = direction.scale(range.sample(ctx.random()));
		Vec3 pos = ctx.boss().position().add(offset);
		int groundY = ctx.getWorld().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(pos.x), Mth.floor(pos.z));
		if (pos.y < groundY) {
			pos = new Vec3(pos.x, groundY, pos.z);
		}
		return Optional.of(pos);
	}

	@Override
	public PositionTargetSelectorType getType() {
		return AttackRegistry.PTS.MOVE_TO_GROUND;
	}
}
