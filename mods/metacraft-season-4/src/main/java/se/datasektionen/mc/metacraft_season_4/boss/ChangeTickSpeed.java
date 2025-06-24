package se.datasektionen.mc.metacraft_season_4.boss;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.floatprovider.FloatProvider;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.world.tick.TickManager;
import se.metacraft.bosses.boss.attacks.AttackType;
import se.metacraft.bosses.boss.attacks.InstantAttack;

public class ChangeTickSpeed extends InstantAttack {

	public static final MapCodec<ChangeTickSpeed> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					IntProvider.NON_NEGATIVE_CODEC.fieldOf("change_time").forGetter(a -> a.changeTime),
					FloatProvider.createValidatedCodec(TickManager.MIN_TICK_RATE, Float.MAX_VALUE).fieldOf("target_rate").forGetter(a -> a.targetRate)
			).apply(instance, ChangeTickSpeed::new)
	);

	private final IntProvider changeTime;
	private final FloatProvider targetRate;

	public ChangeTickSpeed(IntProvider changeTime, FloatProvider targetRate) {
		this.changeTime = changeTime;
		this.targetRate = targetRate;
	}

	@Override
	public void trigger(BossContext<?> ctx) {
		ctx.boss().addAttack(
				new ChangingTickSpeedAttack(
						changeTime.get(ctx.random()),
						targetRate.get(ctx.random())
				)
		);
	}

	@Override
	public AttackType getType() {
		return Season4Attacks.TICK_SPEED_CHANGE;
	}
}
