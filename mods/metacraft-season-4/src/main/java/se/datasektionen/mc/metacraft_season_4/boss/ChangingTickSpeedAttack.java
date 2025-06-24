package se.datasektionen.mc.metacraft_season_4.boss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.world.tick.TickManager;
import se.metacraft.bosses.boss.attacks.Attack;
import se.metacraft.bosses.boss.attacks.AttackType;

public class ChangingTickSpeedAttack implements Attack {

	public static final MapCodec<ChangingTickSpeedAttack> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codecs.NON_NEGATIVE_INT.fieldOf("change_time").forGetter(a -> a.changeTime),
					Codec.floatRange(TickManager.MIN_TICK_RATE, Float.MAX_VALUE).fieldOf("target_rate").forGetter(a -> a.targetRate),
					Codecs.NON_NEGATIVE_INT.optionalFieldOf("progress", 0).forGetter(a -> a.progress)
			).apply(instance, ChangingTickSpeedAttack::new)
	);

	protected int progress;
	protected final int changeTime;
	protected final float targetRate;

	public ChangingTickSpeedAttack(int changeTime, float targetRate) {
		this(changeTime, targetRate, 0);
	}

	public ChangingTickSpeedAttack(int changeTime, float targetRate, int progress) {
		this.changeTime = changeTime;
		this.targetRate = targetRate;
		this.progress = progress;
	}

	@Override
	public void activate(BossContext<?> ctx) {

	}

	@Override
	public void tick(BossContext<?> ctx) {
		var tickManager = ctx.getWorld().getTickManager();
		float currentRate = tickManager.getTickRate();
		if (progress >= changeTime) {
			tickManager.setTickRate(targetRate);
			ctx.boss().removeAttack(this);
		} else {
			float rateDiff = targetRate - currentRate;
			float delta = (float) progress / changeTime;
			tickManager.setTickRate(currentRate + rateDiff * delta);
			progress++;
		}
	}

	@Override
	public void deactivate(BossContext<?> ctx) {

	}

	@Override
	public AttackType getType() {
		return Season4Attacks.TICK_SPEED_CHANGING;
	}

}
