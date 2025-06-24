package se.metacraft.bosses.boss.attacks.target;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.Vec3d;
import se.metacraft.bosses.boss.attacks.Attack;
import se.metacraft.bosses.boss.attacks.AttackRegistry;

import java.util.Optional;

public class FixedPos extends PositionTargetSelector {

	public static final MapCodec<FixedPos> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Vec3d.CODEC.fieldOf("pos").forGetter(m -> m.pos)
			).apply(instance, FixedPos::new)
	);

	private final Vec3d pos;

	public FixedPos(Vec3d pos) {
		this.pos = pos;
	}

	@Override
	public Optional<Vec3d> getTarget(Attack.BossContext<?> ctx) {
		return Optional.of(pos);
	}

	@Override
	public PositionTargetSelectorType getType() {
		return AttackRegistry.PTS.FIXED_POS;
	}
}
