package nu.metacraft.bosses.condition.entity_sub_predicate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.predicate.NumberRange;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.predicate.entity.EntitySubPredicate;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.bosses.boss.AutoAttackingBoss;
import nu.metacraft.bosses.boss.Boss;
import nu.metacraft.bosses.boss.attacks.Attack;
import nu.metacraft.bosses.boss.attacks.AttackRegistry;
import nu.metacraft.bosses.boss.attacks.AttackType;

import java.util.List;

public record BossPredicateType(
		List<EntityEntry> targetCounts,
		List<EntityEntry> allyCounts,
		List<RegistryKey<AttackType>> activeAttacks,
		List<RegistryKey<AttackType>> inactiveAttacks
) implements EntitySubPredicate {

	private static final Codec<RegistryKey<AttackType>> ATTACK_TYPE_CODEC = RegistryKey.createCodec(AttackRegistry.REGISTRY.getKey());
	private static final Codec<List<RegistryKey<AttackType>>> ATTACK_TYPE_LIST_CODEC = Codec.withAlternative(
			ATTACK_TYPE_CODEC.listOf(), ATTACK_TYPE_CODEC, List::of
	);

	public static final MapCodec<BossPredicateType> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					EntityEntry.LIST_CODEC.optionalFieldOf("targets", List.of()).forGetter(BossPredicateType::targetCounts),
					EntityEntry.LIST_CODEC.optionalFieldOf("allies", List.of()).forGetter(BossPredicateType::allyCounts),
					ATTACK_TYPE_LIST_CODEC.optionalFieldOf("active_attacks", List.of()).forGetter(BossPredicateType::activeAttacks),
					ATTACK_TYPE_LIST_CODEC.optionalFieldOf("inactive_attacks", List.of()).forGetter(BossPredicateType::inactiveAttacks)
			).apply(instance, BossPredicateType::new)
	);

	@Override
	public MapCodec<? extends EntitySubPredicate> getCodec() {
		return BossSubPredicates.BOSS_PREDICATE;
	}

	private static RegistryKey<AttackType> getKey(Attack attack) {
		return AttackRegistry.REGISTRY.getKey(attack.getType()).orElseThrow();
	}

	@Override
	public boolean test(Entity entity, ServerWorld world, @Nullable Vec3d pos) {
		if (entity instanceof Boss b) {
			for (var t : targetCounts) {
				var allies = b.getTargets(
						TypeFilter.instanceOf(Entity.class),
						e -> t.predicate.test(world, e.getEntityPos(), e)
				);
				if (!t.count.test(allies.size())) {
					return false;
				}
			}
			for (var t : allyCounts) {
				var allies = b.getAllies(
						TypeFilter.instanceOf(Entity.class),
						e -> t.predicate.test(world, e.getEntityPos(), e)
				);
				if (!t.count.test(allies.size())) {
					return false;
				}
			}
			if (activeAttacks.isEmpty() && inactiveAttacks.isEmpty()) {
				return true;
			}

			if (entity instanceof AutoAttackingBoss ab) {
				for (var activeAttack : activeAttacks) {
					if (!ab.getAttacks().hasActiveAttack(attack -> getKey(attack) == activeAttack)) {
						return false;
					}
				}
				for (var inactiveAttack : inactiveAttacks) {
					if (ab.getAttacks().hasActiveAttack(attack -> getKey(attack) == inactiveAttack)) {
						return false;
					}
				}

				return true;
			}
		}
		return false;
	}

	public record EntityEntry(NumberRange.IntRange count, EntityPredicate predicate) {

		private static final EntityPredicate ANY = EntityPredicate.Builder.create().build();

		public static final Codec<EntityEntry> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						NumberRange.IntRange.CODEC.fieldOf("count").forGetter(EntityEntry::count),
						EntityPredicate.CODEC.optionalFieldOf(
								"predicate", ANY
						).forGetter(EntityEntry::predicate)
				).apply(instance, EntityEntry::new)
		);

		public static final Codec<EntityEntry> SIMPLE_CODEC = Codec.withAlternative(
				CODEC, NumberRange.IntRange.CODEC, range -> new EntityEntry(range, ANY)
		);

		public static final Codec<List<EntityEntry>> LIST_CODEC = Codec.withAlternative(
				SIMPLE_CODEC.listOf(), SIMPLE_CODEC, List::of
		);
	}
}
