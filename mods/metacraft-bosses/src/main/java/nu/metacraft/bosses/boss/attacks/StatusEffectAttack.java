package nu.metacraft.bosses.boss.attacks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.MobEffectsPredicate;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.AllOfCondition;
import net.minecraft.world.level.storage.loot.predicates.InvertedLootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemEntityPropertyCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import nu.metacraft.lib.condition.METAcraftContexTypes;
import nu.metacraft.bosses.util.StatusEffectEntry;

import java.util.List;
import java.util.Optional;

public class StatusEffectAttack extends InstantAttack {

	protected final StatusEffectEntry effect;
	protected final LootItemCondition predicate;
	protected final boolean affectTargets;
	protected final boolean affectAllies;

	public static final MapCodec<StatusEffectAttack> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					StatusEffectEntry.CODEC.forGetter(a -> a.effect),
					LootItemCondition.DIRECT_CODEC.fieldOf("predicate").forGetter(a -> a.predicate),
					Codec.BOOL.optionalFieldOf("affect_targets", true).forGetter(a -> a.affectTargets),
					Codec.BOOL.optionalFieldOf("affect_allies", false).forGetter(a -> a.affectAllies)
			).apply(instance, StatusEffectAttack::new)
	);

	public static StatusEffectAttack create(
			Holder<MobEffect> effect,
			IntProvider duration, IntProvider amplifier,
			EntityPredicate predicate, NumberProvider probability
	) {
		return new StatusEffectAttack(
				StatusEffectEntry.create(effect, duration, amplifier),
				AllOfCondition.allOf(
						List.of(
								new LootItemEntityPropertyCondition(
										Optional.of(predicate),
										LootContext.EntityTarget.THIS
								),
								new InvertedLootItemCondition(
									new LootItemEntityPropertyCondition(
										Optional.of(
											EntityPredicate.Builder.entity().effects(
													MobEffectsPredicate.Builder.effects().and(effect)
											).build()
										),
										LootContext.EntityTarget.THIS
									)
								),
								new LootItemRandomChanceCondition(
										probability
								)
						)
				), true, false
		);
	}

	public StatusEffectAttack(
			StatusEffectEntry effect,
			LootItemCondition predicate,
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
					EntityTypeTest.forClass(LivingEntity.class),
					e -> predicate.test(METAcraftContexTypes.createTickContext((ServerLevel) e.level(), e, e.getRandom()))
			).forEach(target -> {
				target.addEffect(
						effect.createEffect(target.getRandom())
				);
			});
		}
		if (affectAllies) {
			ctx.boss().getAllies(
					EntityTypeTest.forClass(LivingEntity.class),
					e -> predicate.test(METAcraftContexTypes.createTickContext((ServerLevel) e.level(), e, e.getRandom()))
			).forEach(target -> {
				target.addEffect(
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
