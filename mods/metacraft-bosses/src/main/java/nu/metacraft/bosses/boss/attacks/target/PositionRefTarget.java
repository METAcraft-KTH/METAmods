package nu.metacraft.bosses.boss.attacks.target;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.core.position_ref.PositionRef;
import nu.metacraft.core.registry.PositionRefRegistry;
import nu.metacraft.core.util.RefContext;
import nu.metacraft.bosses.boss.attacks.Attack;
import nu.metacraft.bosses.boss.attacks.AttackRegistry;

import java.util.Optional;
import net.minecraft.world.phys.Vec3;

public class PositionRefTarget extends PositionTargetSelector {

	public static final MapCodec<PositionRefTarget> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					PositionRefRegistry.CODEC.fieldOf("pos").forGetter(t -> t.position)
			).apply(instance, PositionRefTarget::new)
	);

	private final PositionRef position;

	public PositionRefTarget(PositionRef position) {
		this.position = position; //TODO Remove PositionTargetSelector and just use PositionRef directly.
	}

	@Override
	protected Optional<Vec3> getTarget(Attack.BossContext<?> ctx) {
		return position.get(
				new RefContext(Optional.of(ctx.boss()), ctx.getWorld(), ctx.random())
		);
	}

	@Override
	public PositionTargetSelectorType getType() {
		return AttackRegistry.PTS.POSITION_REF;
	}
}
