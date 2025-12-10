package nu.metacraft.lib.util.helper;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentTable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.AllOfCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.condition.METAcraftContextParameters;
import nu.metacraft.lib.condition.METAcraftContexTypes;
import nu.metacraft.lib.condition.conditions.NotInWall;
import nu.metacraft.lib.condition.conditions.ValidateSpawnPredicate;
import nu.metacraft.lib.condition.conditions.ValidateSpawnRestriction;
import nu.metacraft.lib.mixin.PrimedTntAccessor;
import nu.metacraft.lib.util.EntityTarget;
import nu.metacraft.lib.util.METACodecs;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public class EntityHelper {

	public static Optional<Entity> loadEntityWithPassengers(ValueInput nbt, Level world, EntitySpawnReason reason, BiFunction<Entity, ValueInput, Entity> entityProcessor) {
		return getEntityFromNBTSafely(nbt, world, reason).map(e -> entityProcessor.apply(e, nbt)).map(entity -> {
			var passengers = nbt.childrenListOrEmpty(Entity.TAG_PASSENGERS);
			for (var p : passengers) {
				loadEntityWithPassengers(p, world, reason, entityProcessor).ifPresent(
						passenger -> passenger.startRiding(entity, true, false)
				);
			}
			return entity;
		});
	}

	public static void initializeEntity(
			Entity entity, @Nullable ValueInput nbt,
			ServerLevelAccessor world, DifficultyInstance difficulty,
			EntitySpawnReason spawnReason, @Nullable SpawnGroupData entityData
	) {
		if (entity instanceof Mob mob) {
			mob.finalizeSpawn(world, difficulty, spawnReason, entityData);
			if (nbt != null) {
				try (var logging = LoggingErrorReporter.create(() -> "metacraft:EntityHelper#initializeEntity", METAcraftLib.LOGGER)) {
					var writeView = TagValueOutput.createWithContext(logging, entity.registryAccess());
					mob.saveWithoutId(writeView);
					CompoundTag data = writeView.buildResult();
					data.merge(ViewHelper.getNBT(nbt));
					var readView = TagValueInput.create(logging, entity.registryAccess(), data);
					mob.load(readView);
				}
			}
		}
	}

	public static Optional<Entity> getEntityFromNBTSafely(ValueInput nbt, Level world, EntitySpawnReason reason) {
		try {
			return EntityType.create(nbt, world, reason);
		} catch (RuntimeException runtimeException) {
			METAcraftLib.LOGGER.warn("Exception loading entity: ", runtimeException);
			return Optional.empty();
		}
	}

	public static void setOwner(Entity entity, Entity owner) {
		if (entity instanceof Projectile projectile) {
			projectile.setOwner(owner);
		}
		if (entity instanceof TamableAnimal tameable && owner instanceof Player p) {
			tameable.tame(p);
		}
		if (entity instanceof Vex vex && owner instanceof Mob mob) {
			vex.setOwner(mob);
		}
		if (entity instanceof EvokerFangs evokerFangs && owner instanceof LivingEntity living) {
			evokerFangs.setOwner(living);
		}
		if (entity instanceof AreaEffectCloud cloud && owner instanceof LivingEntity living) {
			cloud.setOwner(living);
		}
		if (entity instanceof PrimedTntAccessor tnt && owner instanceof LivingEntity living) {
			tnt.setOwner(EntityReference.of(living));
		}
		if (entity instanceof EntityTarget.CanSetOwner can) {
			can.setOwner(owner);
		}
	}

	public static void spawnEntity(
			SpawnEntry data, Predicate<EntityType<?>> canSpawnCheck1,
			Predicate<Entity> canSpawnCheck2, Vec3 around, ServerLevel world, RandomSource random,
			@Nullable Entity owner
	) {
		spawnEntity(data, canSpawnCheck1, canSpawnCheck2, around, world, random, owner, e -> Optional.empty());
	}

	public static Vec3 findPos(
			ServerLevel world, RandomSource random, EntityType<?> type, SpawnEntry.SpawnRules spawnRules, Vec3 around
	) {
		double x = around.x() + spawnRules.horizontalRange().sample(random);
		double preliminaryY = around.y() + spawnRules.verticalRange().sample(random);
		double z = around.z() + spawnRules.horizontalRange().sample(random);
		int maxYRange = (int) Math.round(new Vec3(x, preliminaryY, z).distanceTo(around));
		double y = findY(world, random, type, spawnRules, x, preliminaryY, z, maxYRange);
		return new Vec3(x, y, z);
	}

	public static void spawnEntity(
			SpawnEntry data,
			Predicate<EntityType<?>> canSpawnCheck1, Predicate<Entity> canSpawnCheck2,
			Vec3 around, ServerLevel world, RandomSource random,
			@Nullable Entity owner, Function<Entity, Optional<Entity>> getTarget
	) {
		try (var logging = LoggingErrorReporter.create(() -> "metacraft:EntityHelper#spawnEntity", METAcraftLib.LOGGER)) {
			var view = TagValueInput.create(logging, world.registryAccess(), data.entity());
			EntityType.by(view).ifPresent(type -> {
				if (!canSpawnCheck1.test(type)) return;
				var pos = findPos(world, random, type, data.spawnRules, around);
				if (canSpawn(world, random, type, data.spawnRules().condition(), data.spawnRules.spawnReason(), pos.x(), pos.y(), pos.z())) {
					EntityHelper.loadEntityWithPassengers(view, world, data.spawnRules().spawnReason(), (e, nbt) -> {
						if (owner != null) {
							EntityHelper.setOwner(e, owner);
						}
						if (e instanceof Mob mob) {
							if (data.initialize()) {
								EntityHelper.initializeEntity(
										mob, ViewHelper.getSize(nbt) > 1 ? nbt : null,
										world, world.getCurrentDifficultyAt(mob.blockPosition()),
										data.spawnRules().spawnReason(), null
								);
							}
							if (data.preventDespawn()) {
								mob.setPersistenceRequired();
							}
							data.equipment().ifPresent(mob::equip);
						}
						e.snapTo(pos.x(), pos.y(), pos.z(), random.nextFloat() * 360.0f, 0.0f);
						getTarget.apply(e).ifPresent(target -> {
							if (e instanceof Mob mob && target instanceof LivingEntity livingTarget) {
								mob.setTarget(livingTarget);
							} else if (e instanceof EntityTarget.CanSetTarget entity) {
								entity.setTarget(target);
							}
						});
						if (owner != null) {
							var team = owner.getTeam();
							if (team != null) {
								team.getScoreboard().addPlayerToTeam(e.getScoreboardName(), team);
							}
						}
						return e;
					}).ifPresent(entity -> {
						if (!canSpawnCheck2.test(entity)) return;
						world.tryAddFreshEntityWithPassengers(entity);
					});
				}
			});
		}
	}

	private static double findY(
			ServerLevel world, RandomSource random, EntityType<?> type, SpawnEntry.SpawnRules spawnRules,
			double x, double y, double z, int maxYOffset
	) {
		int maxY = Math.min(world.getMaxY(), (int) Math.round(y) + maxYOffset);
		int minY = Math.max(world.getMinY(), (int) Math.round(y) - maxYOffset);
		int yDown = (int) Math.round(y);
		int yUp = (int) Math.round(y);
		if (canSpawn(world, random, type, spawnRules.condition(), spawnRules.spawnReason(), x, y, z)) {
			return y;
		}
		while (yDown > minY && yUp < maxY) {
			if (canSpawn(world, random, type, spawnRules.condition(), spawnRules.spawnReason(), x, yUp, z)) {
				return yUp;
			}
			if (canSpawn(world, random, type, spawnRules.condition(), spawnRules.spawnReason(), x, yDown, z)) {
				return yDown;
			}
			yDown--;
			yUp++;
		}
		return y;
	}

	public static boolean canSpawn(
			ServerLevel world, RandomSource random, EntityType<?> type, Optional<LootItemCondition> condition,
			EntitySpawnReason spawnReason, double x, double y, double z
	) {
		return condition.map(c -> canSpawn(world, random, type, c, spawnReason, x, y, z)).orElse(true);
	}

	public static boolean canSpawn(
			ServerLevel world, RandomSource random, EntityType<?> type, LootItemCondition condition,
			EntitySpawnReason spawnReason, double x, double y, double z
	) {
		LootParams lootContextParameterSet = new LootParams.Builder(world).withParameter(
				LootContextParams.ORIGIN, new Vec3(x, y, z)
		).withParameter(
				METAcraftContextParameters.ENTITY_TYPE, type
		).withParameter(
				METAcraftContextParameters.BOUNDING_BOX, type.getSpawnAABB(x, y, z)
		).withParameter(
				METAcraftContextParameters.SPAWN_REASON, spawnReason
		).create(METAcraftContexTypes.SPAWN_ENTITY);

		return condition.test(
				new LootContext.Builder(lootContextParameterSet).withOptionalRandomSource(random).create(Optional.empty())
		);
	}

	public record SpawnEntry(CompoundTag entity, boolean initialize, boolean preventDespawn, SpawnRules spawnRules, Optional<EquipmentTable> equipment) {
		public static final Codec<SpawnEntry> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						CompoundTag.CODEC.fieldOf("entity").forGetter(SpawnEntry::entity),
						Codec.BOOL.optionalFieldOf("initialize", true).forGetter(SpawnEntry::initialize),
						Codec.BOOL.optionalFieldOf("prevent_despawn", false).forGetter(SpawnEntry::preventDespawn),
						SpawnRules.CODEC.optionalFieldOf("spawn_rules", new SpawnRules()).forGetter(SpawnEntry::spawnRules),
						EquipmentTable.CODEC.optionalFieldOf("equipment").forGetter(SpawnEntry::equipment)
				).apply(instance, SpawnEntry::new)
		);

		public static final Codec<WeightedList<SpawnEntry>> POOL_CODEC = Codec.withAlternative(
				WeightedList.nonEmptyCodec(CODEC), SpawnEntry.CODEC, WeightedList::of
		);

		public record SpawnRules(Optional<LootItemCondition> condition, EntitySpawnReason spawnReason, IntProvider horizontalRange, IntProvider verticalRange) {
			public SpawnRules() {
				this(Optional.empty(), EntitySpawnReason.MOB_SUMMONED, ConstantInt.of(0), ConstantInt.of(0));
			}
			public static final Codec<SpawnRules> CODEC = RecordCodecBuilder.create(
					instance -> instance.group(
							LootItemCondition.DIRECT_CODEC.optionalFieldOf("condition").forGetter(SpawnRules::condition),
							METACodecs.SPAWN_REASON_CODEC.optionalFieldOf("spawn_reason", EntitySpawnReason.MOB_SUMMONED).forGetter(SpawnRules::spawnReason),
							IntProvider.CODEC.optionalFieldOf("horizontal_range", ConstantInt.of(0)).forGetter(SpawnRules::horizontalRange),
							IntProvider.CODEC.optionalFieldOf("vertical_range", ConstantInt.of(0)).forGetter(SpawnRules::verticalRange)
					).apply(instance, SpawnRules::new)
			);
		}

		public static CompoundTag createNBTFromMap(Map<String, Object> map) {
			return CompoundTag.CODEC.parse(JavaOps.INSTANCE, map).resultOrPartial(METAcraftLib.LOGGER::error).orElse(new CompoundTag());
		}

		public static <T extends Entity> CompoundTag createEntityNBTFrom(EntityType<T> type) {
			return createEntityNBTFrom(type, new CompoundTag());
		}

		public static <T extends Entity> CompoundTag createEntityNBTFrom(EntityType<T> type, CompoundTag data) {
			Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
			data.putString("id", id.toString());
			return data;
		}

		public static EntityHelper.SpawnEntry createEntry(
				CompoundTag entity, int horizontalRange, int verticalRange
		) {
			return createEntry(entity, true, false, horizontalRange, verticalRange);
		}

		public static EntityHelper.SpawnEntry createEntry(
				CompoundTag entity, Optional<LootItemCondition> spawnCondition, EntitySpawnReason spawnReason, int horizontalRange, int verticalRange
		) {
			return new EntityHelper.SpawnEntry(
					entity, true, false,
					new EntityHelper.SpawnEntry.SpawnRules(
							spawnCondition, spawnReason,
							UniformInt.of(-horizontalRange, horizontalRange),
							UniformInt.of(verticalRange, verticalRange)
					),
					Optional.empty()
			);
		}

		public static EntityHelper.SpawnEntry createEntry(
				CompoundTag entity, boolean initialize, boolean preventDespawn, int horizontalRange, int verticalRange
		) {
			return createEntry(
					entity, initialize, preventDespawn, Optional.of(AllOfCondition.allOf(
							List.of(
									NotInWall.getInstance(), ValidateSpawnRestriction.getInstance(),
									new ValidateSpawnPredicate(Optional.empty())
							)
					)), EntitySpawnReason.TRIAL_SPAWNER, horizontalRange, verticalRange
			);
		}

		public static EntityHelper.SpawnEntry createEntry(
				CompoundTag entity, boolean initialize, boolean preventDespawn,
				Optional<LootItemCondition> spawnCondition, EntitySpawnReason spawnReason, int horizontalRange, int verticalRange
		) {
			return new EntityHelper.SpawnEntry(
					entity, initialize, preventDespawn,
					new EntityHelper.SpawnEntry.SpawnRules(
							spawnCondition, spawnReason,
							UniformInt.of(-horizontalRange, horizontalRange),
							UniformInt.of(verticalRange, verticalRange)
					),
					Optional.empty()
			);
		}
	}

}
