package se.metacraft.bosses.boss.attacks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.collection.DataPool;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.floatprovider.FloatProvider;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.world.RaycastContext;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityHelper;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

public class SpawnSpecifiedEntities extends SpawnEntityAttackBase {

	public static final MapCodec<SpawnSpecifiedEntities> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					EntityHelper.SpawnEntry.POOL_CODEC.fieldOf("entities").forGetter(attack -> attack.entities),
					IntProvider.NON_NEGATIVE_CODEC.fieldOf("toSpawn").forGetter(a -> a.toSpawn),
					FloatProvider.VALUE_CODEC.fieldOf("playerScaleFactor").forGetter(a -> a.targetScaleFactor),
					EntityPredicate.CODEC.listOf().fieldOf("targets").forGetter(a -> a.targets),
					Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("maxDistanceToTarget").forGetter(a -> a.maxDistanceToTarget),
					Codec.BOOL.fieldOf("mustSeeTarget").forGetter(a -> a.mustSeeTarget),
					Codecs.rangedInt(0, Integer.MAX_VALUE).optionalFieldOf("maxAllies").forGetter(a -> a.maxAllies),
					AllyEntry.CODEC.listOf().optionalFieldOf("specificMaxAllies", List.of()).forGetter(a -> a.specificMaxAllies)
			).apply(instance, SpawnSpecifiedEntities::new)
	);

	private final IntProvider toSpawn;
	private final FloatProvider targetScaleFactor;
	private final List<EntityPredicate> targets;
	private final double maxDistanceToTarget;
	private final boolean mustSeeTarget;
	private final Optional<Integer> maxAllies;
	private final List<AllyEntry> specificMaxAllies;

	public SpawnSpecifiedEntities(
			DataPool<EntityHelper.SpawnEntry> entities, IntProvider toSpawn, FloatProvider targetScaleFactor, List<EntityPredicate> targets,
			double maxDistanceToTarget, boolean mustSeeTarget, Optional<Integer> maxAllies
	) {
		this(entities, toSpawn, targetScaleFactor, targets, maxDistanceToTarget, mustSeeTarget, maxAllies, List.of());
	}

	public SpawnSpecifiedEntities(
			DataPool<EntityHelper.SpawnEntry> entities, IntProvider toSpawn, FloatProvider targetScaleFactor, List<EntityPredicate> targets,
			double maxDistanceToTarget, boolean mustSeeTarget, Optional<Integer> maxAllies, List<AllyEntry> specificMaxAllies
	) {
		super(entities);
		this.toSpawn = toSpawn;
		this.targetScaleFactor = targetScaleFactor;
		this.targets = targets;
		this.maxDistanceToTarget = mustSeeTarget ? Math.min(maxDistanceToTarget, 128) : maxDistanceToTarget;
		this.mustSeeTarget = mustSeeTarget;
		this.maxAllies = maxAllies;
		this.specificMaxAllies = specificMaxAllies;
	}

	private IntSupplier getAllyCount(BossContext<?> ctx) {
		return new IntSupplier() {

			private OptionalInt allies = OptionalInt.empty();

			@Override
			public int getAsInt() {
				return allies.orElseGet(() -> {
					allies = OptionalInt.of(ctx.boss().getAllies().size());
					return allies.getAsInt();
				});
			}
		};
	}

	@Override
	public void trigger(BossContext<?> ctx) {
		var allyCount = getAllyCount(ctx);
		if (maxAllies.map(maxAllies -> allyCount.getAsInt() >= maxAllies).orElse(false)) {
			return;
		}
		var targets = ctx.boss().getTargets(e -> this.targets.stream().anyMatch(
				predicate -> predicate.test(ctx.getWorld(), ctx.boss().getPos(), e)
		));
		int initialAmount = toSpawn.get(ctx.random()) +
				Math.round(
						Math.max(targets.size() - 1, 0) * targetScaleFactor.get(ctx.random())
				);
		int amount = maxAllies.map(
				maxAllies -> Math.clamp(initialAmount, 0, maxAllies - allyCount.getAsInt())
		).orElse(initialAmount);
		while (amount-- > 0) {
			spawnEntity(ctx, ctx.boss().getPos(), type -> {
				for (var entry : specificMaxAllies) {
					var count = ctx.boss().getAllies(
							TypeFilter.instanceOf(Entity.class),
							entity -> entry.getEntityPredicate(ctx.getWorld(), ctx.boss().getPos()).test(entity)
					).size();
					if (count > entry.count) {
						return false;
					}
				}
				return true;
			}, e -> true, spawned -> {
				var actualTargets = targets.stream().filter(
						target -> spawned.distanceTo(target) < maxDistanceToTarget
				).filter(
						target -> spawned.getWorld().raycast(new RaycastContext(
								spawned.getBoundingBox().getCenter(), target.getBoundingBox().getCenter(), RaycastContext.ShapeType.COLLIDER,
								RaycastContext.FluidHandling.NONE, ShapeContext.of(spawned)
						)).getType() == HitResult.Type.MISS
				).toList();
				if (!actualTargets.isEmpty()) {
					return Optional.of(actualTargets.get(ctx.random().nextInt(actualTargets.size())));
				} else {
					return Optional.empty();
				}
			});
		}
	}

	@Override
	public AttackType getType() {
		return AttackRegistry.SPAWN_ENTITIES;
	}

	public record AllyEntry(List<EntityPredicate> entities, int count) {
		public static final Codec<AllyEntry> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						EntityPredicate.CODEC.listOf().fieldOf("entities").forGetter(AllyEntry::entities),
						Codec.intRange(0, Integer.MAX_VALUE).fieldOf("count").forGetter(AllyEntry::count)
				).apply(instance, AllyEntry::new)
		);

		public Predicate<Entity> getEntityPredicate(ServerPlayerEntity player) {
			return entities.stream().reduce(
					e -> false, (p1, p2) -> entity -> p1.test(entity) || p2.test(player, entity), Predicate::or
			);
		}

		public Predicate<Entity> getEntityPredicate(ServerWorld world, Vec3d pos) {
			return entities.stream().reduce(
					e -> false, (p1, p2) -> entity -> p1.test(entity) || p2.test(world, pos, entity), Predicate::or
			);
		}

		public Predicate<EntityType<?>> getTypePredicate() {
			return entities.stream().map(e -> e.type().<Predicate<EntityType<?>>>map(t -> t::matches).orElse(e2 -> true)).reduce(
					e -> false, Predicate::or, Predicate::or
			);
		}
	}
}
