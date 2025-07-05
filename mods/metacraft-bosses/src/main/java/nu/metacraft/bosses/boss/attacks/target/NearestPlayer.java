package nu.metacraft.bosses.boss.attacks.target;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.math.Vec3d;
import nu.metacraft.bosses.boss.attacks.Attack;
import nu.metacraft.bosses.boss.attacks.AttackRegistry;

import java.util.Comparator;
import java.util.Optional;

public class NearestPlayer extends PositionTargetSelector {

	private static final NearestPlayer INSTANCE = new NearestPlayer();

	public static final MapCodec<NearestPlayer> CODEC = MapCodec.unit(INSTANCE);

	public static NearestPlayer getInstance() {
		return INSTANCE;
	}

	private NearestPlayer() {}

	@Override
	public Optional<Vec3d> getTarget(Attack.BossContext<?> ctx) {
		return ctx.boss().getPlayerTargets().stream().min(
				Comparator.comparingDouble(p -> p.squaredDistanceTo(ctx.boss()))
		).map(player -> player.getBoundingBox().getCenter());
	}

	@Override
	public PositionTargetSelectorType getType() {
		return AttackRegistry.PTS.NEAREST_PLAYER;
	}
}
