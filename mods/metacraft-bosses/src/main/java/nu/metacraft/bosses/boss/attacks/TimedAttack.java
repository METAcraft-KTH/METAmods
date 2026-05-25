package nu.metacraft.bosses.boss.attacks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;

public class TimedAttack implements Attack {

	public static final MapCodec<TimedAttack> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.lazyInitialized(() -> Attack.REGISTRY_CODEC).fieldOf("attack").forGetter(a -> a.attack),
					IntProviders.POSITIVE_CODEC.fieldOf("timeGetter").forGetter(a -> a.timeGetter),
					Codec.INT.fieldOf("time").orElse(0).forGetter(a -> a.time)
			).apply(instance, TimedAttack::new)
	);

	private final Attack attack;
	private final IntProvider timeGetter;
	private int time;

	public TimedAttack(Attack attack, IntProvider time) {
		this.attack = attack;
		this.timeGetter = time;
	}

	public TimedAttack(Attack attack, IntProvider time, int t) {
		this(attack, time);
		this.time = t;
	}

	@Override
	public void activate(BossContext<?> ctx) {
		attack.activate(ctx);
		time = timeGetter.sample(ctx.random());
	}

	@Override
	public void tick(BossContext<?> ctx) {
		attack.tick(ctx);
		if (time-- < 0) {
			ctx.boss().removeAttack(this);
		}
	}

	@Override
	public void deactivate(BossContext<?> ctx) {
		attack.deactivate(ctx);
	}

	@Override
	public void removeSubAttack(BossContext<?> ctx, Attack attack) {
		this.attack.removeSubAttack(ctx, attack);
		if (this.attack == attack) {
			ctx.boss().removeAttack(this);
		}
	}

	@Override
	public AttackType getType() {
		return AttackRegistry.TIMED;
	}
}
