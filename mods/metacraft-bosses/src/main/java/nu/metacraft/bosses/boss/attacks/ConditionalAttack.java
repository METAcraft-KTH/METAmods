package nu.metacraft.bosses.boss.attacks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.OptionalInt;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class ConditionalAttack extends InstantAttack {

	public static final MapCodec<ConditionalAttack> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.lazyInitialized(() -> Attack.REGISTRY_CODEC).fieldOf("attack").forGetter(a -> a.attack),
					LootItemCondition.DIRECT_CODEC.fieldOf("condition").forGetter(a -> a.condition)
			).apply(instance, ConditionalAttack::new)
	);

	private final Attack attack;
	private final LootItemCondition condition;

	public ConditionalAttack(Attack attack, LootItemCondition condition) {
		this.attack = attack;
		this.condition = condition;
	}

	@Override
	public OptionalInt getDelayOverride(BossContext<?> ctx) {
		return condition.test(ctx.toVanillaContext()) ? OptionalInt.empty() : OptionalInt.of(0);
	}

	@Override
	public void trigger(BossContext<?> ctx) {
		if (condition.test(ctx.toVanillaContext())) {
			ctx.boss().addAttack(attack);
		}
	}

	@Override
	public AttackType getType() {
		return AttackRegistry.CONDITIONAL;
	}
}
