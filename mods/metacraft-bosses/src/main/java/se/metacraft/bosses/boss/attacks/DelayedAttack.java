package se.metacraft.bosses.boss.attacks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.intprovider.IntProvider;

public class DelayedAttack implements Attack {

	public static final MapCodec<DelayedAttack> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					IntProvider.NON_NEGATIVE_CODEC.fieldOf("delay").forGetter(a -> a.delay),
					Codec.lazyInitialized(() -> Attack.REGISTRY_CODEC).fieldOf("attack").forGetter(a -> a.attack),
					Codec.INT.optionalFieldOf("time", 0).forGetter(a -> a.time)
			).apply(instance, DelayedAttack::new)
	);

	private final IntProvider delay;
	private final Attack attack;

	private int time;

	public DelayedAttack(IntProvider delay, Attack attack) {
		this.delay = delay;
		this.attack = attack;
	}

	public DelayedAttack(IntProvider delay, Attack attack, int time) {
		this(delay, attack);
		this.time = time;
	}

	@Override
	public void activate(BossContext<?> ctx) {
		time = delay.get(ctx.random());
	}

	@Override
	public void tick(BossContext<?> ctx) {
		if (time-- <= 0) {
			ctx.boss().removeAttack(this);
		}
	}

	@Override
	public void deactivate(BossContext<?> ctx) {
		ctx.boss().addAttack(attack);
	}

	@Override
	public AttackType getType() {
		return AttackRegistry.DELAYED_ATTACK;
	}
}
