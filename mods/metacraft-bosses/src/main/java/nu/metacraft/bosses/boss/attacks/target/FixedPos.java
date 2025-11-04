package nu.metacraft.bosses.boss.attacks.target;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.bosses.boss.attacks.Attack;
import nu.metacraft.bosses.boss.attacks.AttackRegistry;

import java.util.Optional;
import net.minecraft.world.phys.Vec3;

public class FixedPos extends PositionTargetSelector {

	public static final MapCodec<FixedPos> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Vec3.CODEC.fieldOf("pos").forGetter(m -> m.pos)
			).apply(instance, FixedPos::new)
	);

	private final Vec3 pos;

	public FixedPos(Vec3 pos) {
		this.pos = pos;
	}

	@Override
	public Optional<Vec3> getTarget(Attack.BossContext<?> ctx) {
		return Optional.of(pos);
	}

	@Override
	public PositionTargetSelectorType getType() {
		return AttackRegistry.PTS.FIXED_POS;
	}
}
