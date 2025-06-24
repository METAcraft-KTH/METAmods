package se.metacraft.bosses.boss.attacks.target;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.math.Vec3d;
import se.metacraft.bosses.boss.attacks.Attack;
import se.metacraft.bosses.boss.attacks.AttackRegistry;

import java.util.Optional;

public class RandomPlayer extends PositionTargetSelector {

	private static final RandomPlayer INSTANCE = new RandomPlayer();

	public static final MapCodec<RandomPlayer> CODEC = MapCodec.unit(INSTANCE);

	public static RandomPlayer getInstance() {
		return INSTANCE;
	}

	private RandomPlayer() {}

	@Override
	public Optional<Vec3d> getTarget(Attack.BossContext<?> ctx) {
		var playerTargets = ctx.boss().getPlayerTargets();
		if (playerTargets.isEmpty()) return Optional.empty();
		return Optional.of(playerTargets.get(ctx.random().nextInt(playerTargets.size())).getBoundingBox().getCenter());
	}

	@Override
	public PositionTargetSelectorType getType() {
		return AttackRegistry.PTS.RANDOM_PLAYER;
	}
}
