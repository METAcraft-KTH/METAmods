package nu.metacraft.core.entity.entities.player_mob;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.*;
import net.minecraft.entity.ai.brain.sensor.Sensor;
import net.minecraft.entity.ai.brain.sensor.SensorType;
import net.minecraft.entity.ai.brain.task.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import nu.metacraft.core.entity.ai.METAcraftMemoryModules;
import nu.metacraft.core.entity.ai.METAcraftSensorTypes;
import nu.metacraft.core.entity.ai.tasks.*;
import nu.metacraft.core.util.helper.TridentHelper;

import java.util.Optional;

public class PlayerBrain {

	public static final ImmutableList<SensorType<? extends Sensor<? super PlayerMob>>> SENSOR_TYPES = ImmutableList.of(
			SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS,
			SensorType.NEAREST_ITEMS, SensorType.HURT_BY,
			METAcraftSensorTypes.LAST_KNOWN_OXYGEN
	);
	public static final ImmutableList<MemoryModuleType<?>> MEMORY_MODULE_TYPES = ImmutableList.of(
			MemoryModuleType.LOOK_TARGET, MemoryModuleType.DOORS_TO_CLOSE, MemoryModuleType.MOBS, MemoryModuleType.NEAREST_ATTACKABLE,
			MemoryModuleType.VISIBLE_MOBS, MemoryModuleType.NEAREST_VISIBLE_PLAYER,
			MemoryModuleType.NEAREST_VISIBLE_TARGETABLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ADULT_PIGLINS,
			MemoryModuleType.NEARBY_ADULT_PIGLINS, MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM,
			MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS, MemoryModuleType.HURT_BY, MemoryModuleType.HURT_BY_ENTITY,
			MemoryModuleType.WALK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.ATTACK_TARGET,
			MemoryModuleType.ATTACK_COOLING_DOWN, MemoryModuleType.INTERACTION_TARGET, MemoryModuleType.PATH,
			MemoryModuleType.ANGRY_AT, MemoryModuleType.UNIVERSAL_ANGER, MemoryModuleType.AVOID_TARGET,
			MemoryModuleType.NEAREST_VISIBLE_NEMESIS, MemoryModuleType.RIDE_TARGET, MemoryModuleType.ATE_RECENTLY,
			METAcraftMemoryModules.NEAREST_OXYGEN, METAcraftMemoryModules.MOVE_TARGET, METAcraftMemoryModules.RECOVERING_BREATH,
			METAcraftMemoryModules.IS_SMART_SHOOTING
	);

	protected static Brain.Profile<PlayerMob> createBrainProfile() {
		return Brain.createProfile(MEMORY_MODULE_TYPES, SENSOR_TYPES);
	}

	protected static Brain<?> create(PlayerMob player, Brain<PlayerMob> brain) {
		addIdleActivities(brain);
		addCoreActivities(brain);
		addFightActivities(player, brain);
		brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
		brain.setDefaultActivity(Activity.IDLE);
		brain.resetPossibleActivities();
		return brain;
	}

	protected static void addIdleActivities(Brain<PlayerMob> brain) {
		brain.setTaskList(Activity.IDLE, 10, ImmutableList.of(
				UpdateAttackTargetTask.create((world, e) -> true, PlayerBrain::getPreferredTarget), makeRandomWanderTask(),
				GoToMoveTarget.create(METAcraftMemoryModules.MOVE_TARGET, 0.6f, 0),
				ForgetTask.create(PlayerBrain::hasReachedMoveTarget, METAcraftMemoryModules.MOVE_TARGET)
		));
	}

	protected static void addCoreActivities(Brain<PlayerMob> brain) {
		brain.setTaskList(Activity.CORE, 0, ImmutableList.of(
				new NeedToBreathe(1), new StayAboveWaterTask<>(0.5f) {
					@Override
					protected boolean shouldRun(ServerWorld serverWorld, MobEntity mobEntity) {
						return super.shouldRun(serverWorld, mobEntity) && !mobEntity.isSwimming();
					}
				},
				new UpdateLookControlTask(45, 90), new MoveToTargetTask(), OpenDoorsTask.create(),
				DefeatTargetTask.create(0, (player, target) -> false), ForgetAngryAtTargetTask.create()
		));
	}

	protected static boolean isSmartProjectileWeapon(ItemStack stack) {
		return stack.getItem() instanceof BowItem || stack.getItem() instanceof TridentItem;
	}

	protected static boolean hasReachedMoveTarget(PlayerMob player) {
		var target = player.getBrain().getOptionalRegisteredMemory(METAcraftMemoryModules.MOVE_TARGET);
		return target.filter(pos -> player.getBlockPos().isWithinDistance(pos.pos(), 1)).isPresent();
	}

	protected static boolean allowSetMovePos(PlayerMob player) {
		return !player.getBrain().hasMemoryModule(METAcraftMemoryModules.RECOVERING_BREATH) || !player.isSubmergedInWater();
	}
	
	private static void addFightActivities(PlayerMob player, Brain<PlayerMob> brain) {
		brain.setTaskList(Activity.FIGHT, 10, ImmutableList.of(
				ForgetAttackTargetTask.create((world, target) -> !PlayerBrain.isPreferredAttackTarget(world, player, target)),
				TaskTriggerer.runIf(PlayerBrain::isHoldingCrossbow, AttackTask.create(5, 0.75f)),
				TaskTriggerer.runIf(
						PlayerBrain::allowSetMovePos,
						(SingleTickTask<MobEntity>) ImprovedRangedApproachTask.create(1.0f)
				), TaskTriggerer.runIf(
						PlayerBrain::shouldAttackPhysical,
						MeleeAttackTask.create(20)
				), new CrossbowAttackTask<>(),
				new SmartShootAttackTask<>(PlayerBrain::isSmartProjectileWeapon, 20),
				new SmartStrafeAttackTask<>(1, 8)
		), MemoryModuleType.ATTACK_TARGET);
	}

	protected static boolean shouldAttackPhysical(PlayerMob player) {
		var target = player.getBrain().getOptionalRegisteredMemory(MemoryModuleType.ATTACK_TARGET);
		if (target.isPresent()) {
			var result = TridentHelper.shouldThrowTrident(player, target.get());
			if (result.isPresent()) {
				return !result.get();
			}
		}
		return true;
	}

	protected static boolean isHoldingCrossbow(LivingEntity player) {
		return player.isHolding(item -> item.getItem() instanceof CrossbowItem);
	}

	private static RandomTask<PlayerMob> makeRandomWanderTask() {
		return new RandomTask<>(ImmutableList.of(
				Pair.of(TaskTriggerer.runIf(PlayerBrain::canWander, StrollTask.create(0.6f)), 1),
				Pair.of(TaskTriggerer.runIf(PlayerBrain::canWander, GoToLookTargetTask.create(0.6f, 3)), 1),
				Pair.of(new WaitTask(50, 100), 1)
		));
	}

	private static boolean canWander(PlayerMob player) {
		return player.canWander();
	}

	protected static boolean isPreferredAttackTarget(ServerWorld world, PlayerMob player, LivingEntity target) {
		return getPreferredTarget(world, player).filter(preferredTarget -> preferredTarget == target).isPresent();
	}

	@SuppressWarnings({"OptionalAssignedToNull"})
	private static Optional<? extends LivingEntity> getPreferredTarget(ServerWorld world, PlayerMob player) {
		var angerTarget = TargetUtil.getEntity(player, MemoryModuleType.ANGRY_AT);
		if (angerTarget.isPresent() && Sensor.testAttackableTargetPredicateIgnoreVisibility(world, player, angerTarget.get())) {
			return angerTarget;
		}
		var nearest = player.getBrain().getOptionalMemory(MemoryModuleType.NEAREST_ATTACKABLE);
		if (nearest != null) {
			return nearest;
		}
		return Optional.empty();
	}

	protected static void onAttacked(ServerWorld world, PlayerMob player, LivingEntity attacker) {
		tryRevenge(world, player, attacker);
	}

	protected static void tryRevenge(ServerWorld world, PlayerMob player, LivingEntity target) {
		if (!Sensor.testAttackableTargetPredicateIgnoreVisibility(world, player, target)) {
			return;
		}
		if (TargetUtil.isNewTargetTooFar(player, target, 4.0)) {
			return;
		}
		becomeAngryWith(world, player, target);
	}


	protected static void becomeAngryWith(ServerWorld world, PlayerMob player, LivingEntity target) {
		if (!Sensor.testAttackableTargetPredicateIgnoreVisibility(world, player, target)) {
			return;
		}
		player.getBrain().remember(MemoryModuleType.ANGRY_AT, target.getUuid(), 600L);
	}

	protected static void tick(ServerWorld world, PlayerMob player) {
		Brain<PlayerMob> brain = player.getBrain();
		brain.tick(world, player);
		var activity = brain.getFirstPossibleNonCoreActivity();
		if (activity.isPresent()) {
			brain.resetPossibleActivities(ImmutableList.of(Activity.FIGHT, Activity.IDLE));
		}
		var target = brain.getOptionalRegisteredMemory(MemoryModuleType.ATTACK_TARGET);
		player.setTarget(target.orElse(null));
		player.setAttacking(brain.hasMemoryModule(MemoryModuleType.ATTACK_TARGET));
	}
}
