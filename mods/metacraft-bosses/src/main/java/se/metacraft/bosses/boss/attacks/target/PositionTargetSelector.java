package se.metacraft.bosses.boss.attacks.target;

import com.mojang.serialization.Codec;
import net.minecraft.util.math.Vec3d;
import se.metacraft.bosses.boss.attacks.Attack;
import se.metacraft.bosses.boss.attacks.AttackRegistry;

import java.util.Optional;

public abstract class PositionTargetSelector {
	public static final Codec<PositionTargetSelector> REGISTRY_CODEC = AttackRegistry.PTS.REGISTRY.getCodec().dispatch(
			PositionTargetSelector::getType, PositionTargetSelectorType::codec
	);

	protected abstract Optional<Vec3d> getTarget(Attack.BossContext<?> ctx);

	public Optional<Vec3d> getTargetInWorld(Attack.BossContext<?> ctx) {
		return getTarget(ctx).map(
				pos -> {
					if (pos.getY() < ctx.getWorld().getBottomY()) {
						return new Vec3d(pos.getX(), ctx.getWorld().getBottomY(), pos.getZ());
					}
					if (pos.getY() > ctx.getWorld().getTopYInclusive()) {
						return new Vec3d(pos.getX(), ctx.getWorld().getTopYInclusive(), pos.getZ());
					}
					return pos;
				}
		);
	}

	public abstract PositionTargetSelectorType getType();
}
