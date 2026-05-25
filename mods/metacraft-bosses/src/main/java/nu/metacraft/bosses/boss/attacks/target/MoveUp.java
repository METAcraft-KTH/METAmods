package nu.metacraft.bosses.boss.attacks.target;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.valueproviders.FloatProviders;
import nu.metacraft.bosses.boss.attacks.Attack;
import nu.metacraft.bosses.boss.attacks.AttackRegistry;

import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public class MoveUp extends PositionTargetSelector {

	public static final MapCodec<MoveUp> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					FloatProviders.codec(0, Float.MAX_VALUE).fieldOf("range").forGetter(m -> m.range),
					Codec.DOUBLE.fieldOf("maxDistAboveGround").forGetter(m -> m.maxDistAboveGround)
			).apply(instance, MoveUp::new)
	);

	private final FloatProvider range;
	private final double maxDistAboveGround;

	public MoveUp(FloatProvider range, double maxDistAboveGround) {
		this.range = range;
		this.maxDistAboveGround = maxDistAboveGround;
	}

	@Override
	public Optional<Vec3> getTarget(Attack.BossContext<?> ctx) {
		float yaw = ctx.random().nextFloat() * 360 - 180;
		float pitch = -(ctx.random().nextFloat() * 45);
		var direction = Vec3.directionFromRotation(pitch, yaw);
		var offset = direction.scale(range.sample(ctx.random()));
		Vec3 pos = ctx.boss().position().add(offset);
		int groundY = ctx.getWorld().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(pos.x), Mth.floor(pos.z));
		if (pos.y - groundY > maxDistAboveGround) {
			pos = new Vec3(pos.x, groundY + maxDistAboveGround, pos.z);
		}
		return Optional.of(pos);
	}

	@Override
	public PositionTargetSelectorType getType() {
		return AttackRegistry.PTS.MOVE_UP;
	}
}
