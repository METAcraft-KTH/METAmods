package nu.metacraft.bosses.boss.attacks.target;

import com.mojang.serialization.MapCodec;
import nu.metacraft.bosses.boss.attacks.Attack;
import nu.metacraft.bosses.boss.attacks.AttackRegistry;

import java.util.Optional;
import net.minecraft.world.phys.Vec3;

public class RandomPlayer extends PositionTargetSelector {

	private static final RandomPlayer INSTANCE = new RandomPlayer();

	public static final MapCodec<RandomPlayer> CODEC = MapCodec.unit(INSTANCE);

	public static RandomPlayer getInstance() {
		return INSTANCE;
	}

	private RandomPlayer() {}

	@Override
	public Optional<Vec3> getTarget(Attack.BossContext<?> ctx) {
		var playerTargets = ctx.boss().getPlayerTargets();
		if (playerTargets.isEmpty()) return Optional.empty();
		return Optional.of(playerTargets.get(ctx.random().nextInt(playerTargets.size())).getBoundingBox().getCenter());
	}

	@Override
	public PositionTargetSelectorType getType() {
		return AttackRegistry.PTS.RANDOM_PLAYER;
	}
}
