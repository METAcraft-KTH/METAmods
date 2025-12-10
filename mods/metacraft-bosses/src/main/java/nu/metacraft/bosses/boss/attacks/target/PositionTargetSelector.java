package nu.metacraft.bosses.boss.attacks.target;

import com.mojang.serialization.Codec;
import nu.metacraft.bosses.boss.attacks.Attack;
import nu.metacraft.bosses.boss.attacks.AttackRegistry;

import java.util.Optional;
import net.minecraft.world.phys.Vec3;

public abstract class PositionTargetSelector {
	public static final Codec<PositionTargetSelector> REGISTRY_CODEC = AttackRegistry.PTS.REGISTRY.byNameCodec().dispatch(
			PositionTargetSelector::getType, PositionTargetSelectorType::codec
	);

	protected abstract Optional<Vec3> getTarget(Attack.BossContext<?> ctx);

	public Optional<Vec3> getTargetInWorld(Attack.BossContext<?> ctx) {
		return getTarget(ctx).map(
				pos -> {
					if (pos.y() < ctx.getWorld().getMinY()) {
						return new Vec3(pos.x(), ctx.getWorld().getMinY(), pos.z());
					}
					if (pos.y() > ctx.getWorld().getMaxY()) {
						return new Vec3(pos.x(), ctx.getWorld().getMaxY(), pos.z());
					}
					return pos;
				}
		);
	}

	public abstract PositionTargetSelectorType getType();
}
