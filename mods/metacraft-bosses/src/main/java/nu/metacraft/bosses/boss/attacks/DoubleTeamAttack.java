package nu.metacraft.bosses.boss.attacks;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.bosses.util.DoubleTeamHandler;

public class DoubleTeamAttack extends InstantAttack {

	public static final MapCodec<DoubleTeamAttack> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					DoubleTeamHandler.Settings.CODEC.forGetter(t -> t.settings)
			).apply(instance, DoubleTeamAttack::new)
	);

	private final DoubleTeamHandler.Settings settings;

	public DoubleTeamAttack(DoubleTeamHandler.Settings settings) {
		this.settings = settings;
	}

	@Override
	public void trigger(BossContext<?> ctx) {
		DoubleTeamHandler.applyToEntity(
				new DoubleTeamHandler(
						ctx.boss(), settings, 0, 0
				)
		);
	}

	@Override
	public AttackType getType() {
		return AttackRegistry.DOUBLE_TEAM;
	}
}
