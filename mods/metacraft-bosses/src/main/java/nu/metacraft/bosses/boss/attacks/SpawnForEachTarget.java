package nu.metacraft.bosses.boss.attacks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.valueproviders.IntProviders;
import nu.metacraft.lib.condition.METAcraftContexTypes;
import nu.metacraft.lib.util.helper.EntityHelper;

import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SpawnForEachTarget extends SpawnEntityAttackBase {

	public static final MapCodec<SpawnForEachTarget> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					EntityHelper.SpawnEntry.POOL_CODEC.fieldOf("entities").forGetter(attack -> attack.entities),
					EntityPredicate.CODEC.listOf().fieldOf("targets").forGetter(a -> a.targets),
					IntProviders.NON_NEGATIVE_CODEC.fieldOf("countPerTarget").forGetter(a -> a.countPerTarget),
					Codec.BOOL.fieldOf("spawnAroundTarget").forGetter(a -> a.spawnAroundTarget),
					LootItemCondition.DIRECT_CODEC.optionalFieldOf("targetSelectCondition").forGetter(a -> a.targetSelectCondition)
			).apply(instance, SpawnForEachTarget::new)
	);

	private final List<EntityPredicate> targets;
	private final IntProvider countPerTarget;
	private final boolean spawnAroundTarget;
	private final Optional<LootItemCondition> targetSelectCondition;

	public SpawnForEachTarget(
			WeightedList<EntityHelper.SpawnEntry> entities, List<EntityPredicate> targets, IntProvider countPerTarget, boolean spawnAroundTarget
	) {
		this(entities, targets, countPerTarget, spawnAroundTarget, Optional.empty());
	}

	public SpawnForEachTarget(
			WeightedList<EntityHelper.SpawnEntry> entities, List<EntityPredicate> targets, IntProvider countPerTarget, boolean spawnAroundTarget, Optional<LootItemCondition> targetSelectCondition
	) {
		super(entities);
		this.targets = targets;
		this.countPerTarget = countPerTarget;
		this.spawnAroundTarget = spawnAroundTarget;
		this.targetSelectCondition = targetSelectCondition;
	}

	@Override
	public void trigger(BossContext<?> ctx) {
		var targets = ctx.boss().getTargets(e -> this.targets.stream().anyMatch(
				predicate -> predicate.matches(ctx.getWorld(), ctx.boss().position(), e)
		));
		targets.forEach(target -> {
			int count = countPerTarget.sample(ctx.random());
			for (int i = 0; i < count; i++) {
				if (targetSelectCondition.map(
						cond -> cond.test(METAcraftContexTypes.createTickContext(ctx.getWorld(), target, ctx.random()))
				).orElse(true)) {
					spawnEntity(ctx, spawnAroundTarget ? target.position() : ctx.boss().position(), spawned -> Optional.of(target));
				}
			}
		});
	}

	@Override
	public AttackType getType() {
		return AttackRegistry.SPAWN_ENTITIES_FOR_EACH_TARGET;
	}
}
