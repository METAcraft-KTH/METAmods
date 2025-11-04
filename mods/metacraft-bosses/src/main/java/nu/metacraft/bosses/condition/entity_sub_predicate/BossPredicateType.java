package nu.metacraft.bosses.condition.entity_sub_predicate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.bosses.boss.AutoAttackingBoss;
import nu.metacraft.bosses.boss.Boss;
import nu.metacraft.bosses.boss.attacks.Attack;
import nu.metacraft.bosses.boss.attacks.AttackRegistry;
import nu.metacraft.bosses.boss.attacks.AttackType;

import java.util.List;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.EntitySubPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

public record BossPredicateType(
		List<EntityEntry> targetCounts,
		List<EntityEntry> allyCounts,
		List<ResourceKey<AttackType>> activeAttacks,
		List<ResourceKey<AttackType>> inactiveAttacks
) implements EntitySubPredicate {

	private static final Codec<ResourceKey<AttackType>> ATTACK_TYPE_CODEC = ResourceKey.codec(AttackRegistry.REGISTRY.key());
	private static final Codec<List<ResourceKey<AttackType>>> ATTACK_TYPE_LIST_CODEC = Codec.withAlternative(
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
	public MapCodec<? extends EntitySubPredicate> codec() {
		return BossSubPredicates.BOSS_PREDICATE;
	}

	private static ResourceKey<AttackType> getKey(Attack attack) {
		return AttackRegistry.REGISTRY.getResourceKey(attack.getType()).orElseThrow();
	}

	@Override
	public boolean matches(Entity entity, ServerLevel world, @Nullable Vec3 pos) {
		if (entity instanceof Boss b) {
			for (var t : targetCounts) {
				var allies = b.getTargets(
						EntityTypeTest.forClass(Entity.class),
						e -> t.predicate.matches(world, e.position(), e)
				);
				if (!t.count.matches(allies.size())) {
					return false;
				}
			}
			for (var t : allyCounts) {
				var allies = b.getAllies(
						EntityTypeTest.forClass(Entity.class),
						e -> t.predicate.matches(world, e.position(), e)
				);
				if (!t.count.matches(allies.size())) {
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

	public record EntityEntry(MinMaxBounds.Ints count, EntityPredicate predicate) {

		private static final EntityPredicate ANY = EntityPredicate.Builder.entity().build();

		public static final Codec<EntityEntry> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						MinMaxBounds.Ints.CODEC.fieldOf("count").forGetter(EntityEntry::count),
						EntityPredicate.CODEC.optionalFieldOf(
								"predicate", ANY
						).forGetter(EntityEntry::predicate)
				).apply(instance, EntityEntry::new)
		);

		public static final Codec<EntityEntry> SIMPLE_CODEC = Codec.withAlternative(
				CODEC, MinMaxBounds.Ints.CODEC, range -> new EntityEntry(range, ANY)
		);

		public static final Codec<List<EntityEntry>> LIST_CODEC = Codec.withAlternative(
				SIMPLE_CODEC.listOf(), SIMPLE_CODEC, List::of
		);
	}
}
