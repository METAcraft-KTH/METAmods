package se.datasektionen.mc.metacraft_lib.util.helper;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.EvokerFangsEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DataPool;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;
import se.datasektionen.mc.metacraft_lib.condition.METAcraftContextParameters;
import se.datasektionen.mc.metacraft_lib.condition.METAcraftContexTypes;
import se.datasektionen.mc.metacraft_lib.mixin.AccessorTntEntity;
import se.datasektionen.mc.metacraft_lib.util.EntityTarget;
import se.datasektionen.mc.metacraft_lib.util.ExtraCodecs;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public class EntityHelper {

	public static Optional<Entity> loadEntityWithPassengers(NbtCompound nbt, World world, BiFunction<Entity, NbtCompound, Entity> entityProcessor) {
		return getEntityFromNBTSafely(nbt, world).map(e -> entityProcessor.apply(e, nbt)).map(entity -> {
			if (nbt.contains("Passengers", NbtElement.LIST_TYPE)) {
				NbtList passengers = nbt.getList("Passengers", NbtElement.COMPOUND_TYPE);
				for (int i = 0; i < passengers.size(); ++i) {
					loadEntityWithPassengers(passengers.getCompound(i), world, entityProcessor).ifPresent(
							passenger -> passenger.startRiding(entity, true)
					);
				}
			}
			return entity;
		});
	}

	public static Optional<Entity> getEntityFromNBTSafely(NbtCompound nbt, World world) {
		try {
			return EntityType.getEntityFromNbt(nbt, world);
		} catch (RuntimeException runtimeException) {
			METAcraftLib.LOGGER.warn("Exception loading entity: ", runtimeException);
			return Optional.empty();
		}
	}

	public static void setOwner(Entity entity, Entity owner) {
		if (entity instanceof ProjectileEntity projectile) {
			projectile.setOwner(owner);
		}
		if (entity instanceof TameableEntity tameable && owner instanceof PlayerEntity p) {
			tameable.setOwner(p);
		}
		if (entity instanceof VexEntity vex && owner instanceof MobEntity mob) {
			vex.setOwner(mob);
		}
		if (entity instanceof EvokerFangsEntity evokerFangs && owner instanceof LivingEntity living) {
			evokerFangs.setOwner(living);
		}
		if (entity instanceof AreaEffectCloudEntity cloud && owner instanceof LivingEntity living) {
			cloud.setOwner(living);
		}
		if (entity instanceof AccessorTntEntity tnt && owner instanceof LivingEntity living) {
			tnt.setCausingEntity(living);
		}
		if (entity instanceof EntityTarget.CanSetOwner can) {
			can.setOwner(owner);
		}
	}

	public static void spawnEntity(
			SpawnEntry data, Predicate<EntityType<?>> canSpawnCheck1,
			Predicate<Entity> canSpawnCheck2, Vec3d around, ServerWorld world, Random random,
			@Nullable Entity owner
	) {
		spawnEntity(data, canSpawnCheck1, canSpawnCheck2, around, world, random, owner, e -> Optional.empty());
	}

	public static void spawnEntity(
			SpawnEntry data,
			Predicate<EntityType<?>> canSpawnCheck1, Predicate<Entity> canSpawnCheck2,
			Vec3d around, ServerWorld world, Random random,
			@Nullable Entity owner, Function<Entity, Optional<Entity>> getTarget
	) {
		EntityType.fromNbt(data.entity()).ifPresent(type -> {
			if (!canSpawnCheck1.test(type)) return;
			double x = around.getX() + data.spawnRules().horizontalRange().get(random);
			double preliminaryY = around.getY() + data.spawnRules().verticalRange().get(random);
			double z = around.getZ() + data.spawnRules().horizontalRange().get(random);
			int maxYRange = (int) Math.round(new Vec3d(x, preliminaryY, z).distanceTo(around));
			double y = findY(world, random, type, data, x, preliminaryY, z, maxYRange);
			if (canSpawn(world, random, type, data.spawnRules().condition(), data.spawnRules.spawnReason(), x, y, z)) {
				EntityHelper.loadEntityWithPassengers(data.entity(), world, (e, nbt) -> {
					if (owner != null) {
						EntityHelper.setOwner(e, owner);
					}
					if (e instanceof MobEntity mob) {
						if (data.initialize()) {
							mob.initialize(world, world.getLocalDifficulty(mob.getBlockPos()), data.spawnRules().spawnReason(), null);
							if (nbt.getSize() != 1) {
								mob.readNbt(nbt); //If we supply custom nbt we must read again after initialize to prevent initialize from erasing data we set explicitly.
							}
						}
						if (data.preventDespawn()) {
							mob.setPersistent();
						}
						data.equipment().ifPresent(mob::setEquipmentFromTable);
					}
					e.refreshPositionAndAngles(x, y, z, random.nextFloat() * 360.0f, 0.0f);
					getTarget.apply(e).ifPresent(target -> {
						if (e instanceof MobEntity mob && target instanceof LivingEntity livingTarget) {
							mob.setTarget(livingTarget);
						} else if (e instanceof EntityTarget.CanSetTarget entity) {
							entity.setTarget(target);
						}
					});
					if (owner != null) {
						var team = owner.getScoreboardTeam();
						if (team != null) {
							team.getScoreboard().addScoreHolderToTeam(e.getNameForScoreboard(), team);
						}
					}
					return e;
				}).ifPresent(entity -> {
					if (!canSpawnCheck2.test(entity)) return;
					world.spawnNewEntityAndPassengers(entity);
				});
			}
		});
	}

	private static double findY(
			ServerWorld world, Random random, EntityType<?> type, SpawnEntry entry,
			double x, double y, double z, int maxYOffset
	) {
		int maxY = Math.min(world.getTopY(), (int) Math.round(y) + maxYOffset);
		int minY = Math.max(world.getBottomY(), (int) Math.round(y) - maxYOffset);
		int yDown = (int) Math.round(y);
		int yUp = (int) Math.round(y);
		if (canSpawn(world, random, type, entry.spawnRules().condition(), entry.spawnRules().spawnReason(), x, y, z)) {
			return y;
		}
		while (yDown > minY && yUp < maxY) {
			if (canSpawn(world, random, type, entry.spawnRules().condition(), entry.spawnRules().spawnReason(), x, yUp, z)) {
				return yUp;
			}
			if (canSpawn(world, random, type, entry.spawnRules().condition(), entry.spawnRules().spawnReason(), x, yDown, z)) {
				return yDown;
			}
			yDown--;
			yUp++;
		}
		return y;
	}

	public static boolean canSpawn(
			ServerWorld world, Random random, EntityType<?> type, Optional<LootCondition> condition,
			SpawnReason spawnReason, double x, double y, double z
	) {
		return condition.map(c -> canSpawn(world, random, type, c, spawnReason, x, y, z)).orElse(true);
	}

	public static boolean canSpawn(
			ServerWorld world, Random random, EntityType<?> type, LootCondition condition,
			SpawnReason spawnReason, double x, double y, double z
	) {
		LootContextParameterSet lootContextParameterSet = new LootContextParameterSet.Builder(world).add(
				LootContextParameters.ORIGIN, new Vec3d(x, y, z)
		).add(
				METAcraftContextParameters.ENTITY_TYPE, type
		).add(
				METAcraftContextParameters.BOUNDING_BOX, type.getSpawnBox(x, y, z)
		).add(
				METAcraftContextParameters.SPAWN_REASON, spawnReason
		).build(METAcraftContexTypes.SPAWN_ENTITY);

		return condition.test(
				new LootContext.Builder(lootContextParameterSet).random(random).build(Optional.empty())
		);
	}

	public record SpawnEntry(NbtCompound entity, boolean initialize, boolean preventDespawn, SpawnRules spawnRules, Optional<EquipmentTable> equipment) {
		public static final Codec<SpawnEntry> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						NbtCompound.CODEC.fieldOf("entity").forGetter(SpawnEntry::entity),
						Codec.BOOL.optionalFieldOf("initialize", true).forGetter(SpawnEntry::initialize),
						Codec.BOOL.optionalFieldOf("prevent_despawn", false).forGetter(SpawnEntry::preventDespawn),
						SpawnRules.CODEC.optionalFieldOf("spawn_rules", new SpawnRules()).forGetter(SpawnEntry::spawnRules),
						EquipmentTable.CODEC.optionalFieldOf("equipment").forGetter(SpawnEntry::equipment)
				).apply(instance, SpawnEntry::new)
		);

		public static final Codec<DataPool<SpawnEntry>> POOL_CODEC = Codec.withAlternative(
				DataPool.createEmptyAllowedCodec(CODEC),
				SpawnEntry.CODEC, DataPool::of
		);

		public record SpawnRules(Optional<LootCondition> condition, SpawnReason spawnReason, IntProvider horizontalRange, IntProvider verticalRange) {
			public SpawnRules() {
				this(Optional.empty(), SpawnReason.MOB_SUMMONED, ConstantIntProvider.create(0), ConstantIntProvider.create(0));
			}
			public static final Codec<SpawnRules> CODEC = RecordCodecBuilder.create(
					instance -> instance.group(
							LootCondition.CODEC.optionalFieldOf("condition").forGetter(SpawnRules::condition),
							ExtraCodecs.SPAWN_REASON_CODEC.optionalFieldOf("spawn_reason", SpawnReason.MOB_SUMMONED).forGetter(SpawnRules::spawnReason),
							IntProvider.VALUE_CODEC.optionalFieldOf("horizontal_range", ConstantIntProvider.create(0)).forGetter(SpawnRules::horizontalRange),
							IntProvider.VALUE_CODEC.optionalFieldOf("vertical_range", ConstantIntProvider.create(0)).forGetter(SpawnRules::verticalRange)
					).apply(instance, SpawnRules::new)
			);
		}
	}

}
