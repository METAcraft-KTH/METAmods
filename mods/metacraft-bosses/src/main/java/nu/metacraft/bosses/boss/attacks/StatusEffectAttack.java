package nu.metacraft.bosses.boss.attacks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.loot.condition.*;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.provider.number.LootNumberProvider;
import net.minecraft.predicate.entity.EntityEffectPredicate;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.intprovider.IntProvider;
import nu.metacraft.lib.condition.METAcraftContexTypes;
import nu.metacraft.bosses.util.StatusEffectEntry;

import java.util.List;
import java.util.Optional;

public class StatusEffectAttack extends InstantAttack {

	protected final StatusEffectEntry effect;
	protected final LootCondition predicate;
	protected final boolean affectTargets;
	protected final boolean affectAllies;

	public static final MapCodec<StatusEffectAttack> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					StatusEffectEntry.CODEC.forGetter(a -> a.effect),
					LootCondition.CODEC.fieldOf("predicate").forGetter(a -> a.predicate),
					Codec.BOOL.optionalFieldOf("affect_targets", true).forGetter(a -> a.affectTargets),
					Codec.BOOL.optionalFieldOf("affect_allies", false).forGetter(a -> a.affectAllies)
			).apply(instance, StatusEffectAttack::new)
	);

	public static StatusEffectAttack create(
			RegistryEntry<StatusEffect> effect,
			IntProvider duration, IntProvider amplifier,
			EntityPredicate predicate, LootNumberProvider probability
	) {
		return new StatusEffectAttack(
				StatusEffectEntry.create(effect, duration, amplifier),
				AllOfLootCondition.create(
						List.of(
								new EntityPropertiesLootCondition(
										Optional.of(predicate),
										LootContext.EntityReference.THIS
								),
								new InvertedLootCondition(
									new EntityPropertiesLootCondition(
										Optional.of(
											EntityPredicate.Builder.create().effects(
													EntityEffectPredicate.Builder.create().addEffect(effect)
											).build()
										),
										LootContext.EntityReference.THIS
									)
								),
								new RandomChanceLootCondition(
										probability
								)
						)
				), true, false
		);
	}

	public StatusEffectAttack(
			StatusEffectEntry effect,
			LootCondition predicate,
			boolean affectTargets,
			boolean affectAllies
	) {
		this.effect = effect;
		this.predicate = predicate;
		this.affectTargets = affectTargets;
		this.affectAllies = affectAllies;
	}

	@Override
	public void trigger(Attack.BossContext<?> ctx) {
		if (affectTargets) {
			ctx.boss().getTargets(
					TypeFilter.instanceOf(LivingEntity.class),
					e -> predicate.test(METAcraftContexTypes.createTickContext((ServerWorld) e.getEntityWorld(), e, e.getRandom()))
			).forEach(target -> {
				target.addStatusEffect(
						effect.createEffect(target.getRandom())
				);
			});
		}
		if (affectAllies) {
			ctx.boss().getAllies(
					TypeFilter.instanceOf(LivingEntity.class),
					e -> predicate.test(METAcraftContexTypes.createTickContext((ServerWorld) e.getEntityWorld(), e, e.getRandom()))
			).forEach(target -> {
				target.addStatusEffect(
						effect.createEffect(target.getRandom())
				);
			});
		}
	}

	@Override
	public AttackType getType() {
		return AttackRegistry.STATUS_EFFECT;
	}
}
