package nu.metacraft.bosses.boss.attacks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.intprovider.IntProvider;

import java.util.OptionalInt;

public class WithCustomDelay extends InstantAttack {

	public static final MapCodec<WithCustomDelay> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.lazyInitialized(() -> Attack.REGISTRY_CODEC).fieldOf("attack").forGetter(a -> a.attack),
					IntProvider.NON_NEGATIVE_CODEC.fieldOf("delay").forGetter(a -> a.delay)
			).apply(instance, WithCustomDelay::new)
	);

	private final Attack attack;
	private final IntProvider delay;

	public WithCustomDelay(Attack attack, IntProvider delay) {
		this.attack = attack;
		this.delay = delay;
	}

	@Override
	public void trigger(BossContext<?> ctx) {
		ctx.boss().addAttack(attack);
	}

	@Override
	public OptionalInt getDelayOverride(BossContext<?> ctx) {
		return OptionalInt.of(delay.get(ctx.random()));
	}

	@Override
	public AttackType getType() {
		return AttackRegistry.CUSTOM_DELAY;
	}
}
