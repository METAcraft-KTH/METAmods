package se.metacraft.bosses.boss.attacks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.pcollections.*;

import java.util.Arrays;
import java.util.List;

public class MultiAttack implements Attack {

	public static final MapCodec<MultiAttack> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
			Codec.lazyInitialized(() -> Attack.REGISTRY_CODEC).listOf().fieldOf("attacks").forGetter(a -> a.attacks.stream().toList())
		).apply(instance, MultiAttack::new)
	);

	private PSet<Attack> attacks;

	public MultiAttack(List<Attack> attacks) {
		this.attacks = HashTreePSet.from(attacks);
	}

	public MultiAttack(Attack... attacks) {
		this(Arrays.stream(attacks).toList());
	}

	@Override
	public void activate(BossContext<?> ctx) {
		attacks.forEach(attack -> {
			attack.activate(ctx);
			if (attack.isInstant()) {
				attacks = attacks.minus(attack);
			}
		});
	}

	@Override
	public void tick(BossContext<?> ctx) {
		attacks.forEach(attack -> attack.tick(ctx));
	}

	@Override
	public void deactivate(BossContext<?> ctx) {
		attacks.forEach(attack -> attack.deactivate(ctx));
	}

	@Override
	public void removeSubAttack(BossContext<?> ctx, Attack attack) {
		if (attacks.contains(attack)) {
			attack.deactivate(ctx);
			attacks = attacks.minus(attack);
		} else {
			attacks.forEach(a -> a.removeSubAttack(ctx, attack));
		}
		if (attacks.isEmpty()) {
			ctx.boss().removeAttack(this);
		}
	}

	@Override
	public AttackType getType() {
		return AttackRegistry.MULTI_ATTACK;
	}
}
