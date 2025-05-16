package se.metacraft.bosses.boss.attacks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.util.collection.DataPool;
import net.minecraft.util.math.intprovider.IntProvider;
import se.datasektionen.mc.metacraft_lib.condition.METAcraftContexTypes;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityHelper;

import java.util.List;
import java.util.Optional;

public class SpawnForEachTarget extends SpawnEntityAttackBase {

	public static final MapCodec<SpawnForEachTarget> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					EntityHelper.SpawnEntry.POOL_CODEC.fieldOf("entities").forGetter(attack -> attack.entities),
					EntityPredicate.CODEC.listOf().fieldOf("targets").forGetter(a -> a.targets),
					IntProvider.NON_NEGATIVE_CODEC.fieldOf("countPerTarget").forGetter(a -> a.countPerTarget),
					Codec.BOOL.fieldOf("spawnAroundTarget").forGetter(a -> a.spawnAroundTarget),
					LootCondition.CODEC.optionalFieldOf("targetSelectCondition").forGetter(a -> a.targetSelectCondition)
			).apply(instance, SpawnForEachTarget::new)
	);

	private final List<EntityPredicate> targets;
	private final IntProvider countPerTarget;
	private final boolean spawnAroundTarget;
	private final Optional<LootCondition> targetSelectCondition;

	public SpawnForEachTarget(
			DataPool<EntityHelper.SpawnEntry> entities, List<EntityPredicate> targets, IntProvider countPerTarget, boolean spawnAroundTarget
	) {
		this(entities, targets, countPerTarget, spawnAroundTarget, Optional.empty());
	}

	public SpawnForEachTarget(
			DataPool<EntityHelper.SpawnEntry> entities, List<EntityPredicate> targets, IntProvider countPerTarget, boolean spawnAroundTarget, Optional<LootCondition> targetSelectCondition
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
				predicate -> predicate.test(ctx.getWorld(), ctx.boss().getPos(), e)
		));
		targets.forEach(target -> {
			int count = countPerTarget.get(ctx.random());
			for (int i = 0; i < count; i++) {
				if (targetSelectCondition.map(
						cond -> cond.test(METAcraftContexTypes.createTickContext(ctx.getWorld(), target, ctx.random()))
				).orElse(true)) {
					spawnEntity(ctx, spawnAroundTarget ? target.getPos() : ctx.boss().getPos(), spawned -> Optional.of(target));
				}
			}
		});
	}

	@Override
	public AttackType getType() {
		return AttackRegistry.SPAWN_ENTITIES_FOR_EACH_TARGET;
	}
}
