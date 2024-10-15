package se.datasektionen.mc.metacraft_core.entity.entities.player_mob;

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
import se.datasektionen.mc.metacraft_core.entity.ai.METAcraftMemoryModules;
import se.datasektionen.mc.metacraft_core.entity.ai.METAcraftSensorTypes;
import se.datasektionen.mc.metacraft_core.entity.ai.tasks.GoToMoveTarget;
import se.datasektionen.mc.metacraft_core.entity.ai.tasks.ImprovedRangedApproachTask;
import se.datasektionen.mc.metacraft_core.entity.ai.tasks.NeedToBreathe;
import se.datasektionen.mc.metacraft_core.entity.ai.tasks.SmartProjectileAttackTask;
import se.datasektionen.mc.metacraft_core.util.helper.TridentHelper;

import java.util.Optional;

public class PlayerBrain {

	protected static final ImmutableList<SensorType<? extends Sensor<? super PlayerMob>>> SENSOR_TYPES = ImmutableList.of(
			SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS,
			SensorType.NEAREST_ITEMS, SensorType.HURT_BY,
			METAcraftSensorTypes.LAST_KNOWN_OXYGEN
	);
	protected static final ImmutableList<MemoryModuleType<?>> MEMORY_MODULE_TYPES = ImmutableList.of(
			MemoryModuleType.LOOK_TARGET, MemoryModuleType.DOORS_TO_CLOSE, MemoryModuleType.MOBS,
			MemoryModuleType.VISIBLE_MOBS, MemoryModuleType.NEAREST_VISIBLE_PLAYER,
			MemoryModuleType.NEAREST_VISIBLE_TARGETABLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ADULT_PIGLINS,
			MemoryModuleType.NEARBY_ADULT_PIGLINS, MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM,
			MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS, MemoryModuleType.HURT_BY, MemoryModuleType.HURT_BY_ENTITY,
			MemoryModuleType.WALK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.ATTACK_TARGET,
			MemoryModuleType.ATTACK_COOLING_DOWN, MemoryModuleType.INTERACTION_TARGET, MemoryModuleType.PATH,
			MemoryModuleType.ANGRY_AT, MemoryModuleType.UNIVERSAL_ANGER, MemoryModuleType.AVOID_TARGET,
			MemoryModuleType.NEAREST_VISIBLE_NEMESIS, MemoryModuleType.RIDE_TARGET, MemoryModuleType.ATE_RECENTLY,
			METAcraftMemoryModules.NEAREST_OXYGEN, METAcraftMemoryModules.MOVE_TARGET, METAcraftMemoryModules.RECOVERING_BREATH
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

	private static void addIdleActivities(Brain<PlayerMob> brain) {
		brain.setTaskList(Activity.IDLE, 10, ImmutableList.of(
				UpdateAttackTargetTask.create(e -> true, PlayerBrain::getPreferredTarget), makeRandomWanderTask(),
				GoToMoveTarget.create(METAcraftMemoryModules.MOVE_TARGET, 0.6f, 0),
				ForgetTask.create(PlayerBrain::hasReachedMoveTarget, METAcraftMemoryModules.MOVE_TARGET)
		));
	}

	private static void addCoreActivities(Brain<PlayerMob> brain) {
		brain.setTaskList(Activity.CORE, 0, ImmutableList.of(
				new NeedToBreathe(1), new StayAboveWaterTask(0.5f) {
					@Override
					protected boolean shouldRun(ServerWorld serverWorld, MobEntity mobEntity) {
						return super.shouldRun(serverWorld, mobEntity) && !mobEntity.isSwimming();
					}
				},
				new LookAroundTask(45, 90), new MoveToTargetTask(), OpenDoorsTask.create(),
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

	private static boolean allowSetMovePos(PlayerMob player) {
		return !player.getBrain().hasMemoryModule(METAcraftMemoryModules.RECOVERING_BREATH) || !player.isSubmergedInWater();
	}
	
	private static void addFightActivities(PlayerMob player, Brain<PlayerMob> brain) {
		brain.setTaskList(Activity.FIGHT, 10, ImmutableList.of(
				ForgetAttackTargetTask.create(target -> !PlayerBrain.isPreferredAttackTarget(player, target)),
				TaskTriggerer.runIf(PlayerBrain::isHoldingCrossbow, AttackTask.create(5, 0.75f)),
				TaskTriggerer.runIf(
						PlayerBrain::allowSetMovePos,
						(SingleTickTask<MobEntity>) ImprovedRangedApproachTask.create(1.0f)
				), TaskTriggerer.runIf(
						PlayerBrain::shouldAttackPhysical,
						MeleeAttackTask.create(20)
				), new CrossbowAttackTask<>(),
				new SmartProjectileAttackTask<>(PlayerBrain::isSmartProjectileWeapon, 1, 20, 8)
		), MemoryModuleType.ATTACK_TARGET);
	}

	private static boolean shouldAttackPhysical(PlayerMob player) {
		var target = player.getBrain().getOptionalRegisteredMemory(MemoryModuleType.ATTACK_TARGET);
		if (target.isPresent()) {
			var result = TridentHelper.shouldThrowTrident(player, target.get());
			if (result.isPresent()) {
				return !result.get();
			}
		}
		return true;
	}

	private static boolean isHoldingCrossbow(LivingEntity player) {
		return player.isHolding(item -> item.getItem() instanceof CrossbowItem);
	}

	private static RandomTask<PlayerMob> makeRandomWanderTask() {
		return new RandomTask<>(ImmutableList.of(
				Pair.of(TaskTriggerer.runIf(PlayerBrain::canWander, StrollTask.create(0.6f)), 1),
				Pair.of(TaskTriggerer.runIf(PlayerBrain::canWander, GoTowardsLookTargetTask.create(0.6f, 3)), 1),
				Pair.of(new WaitTask(50, 100), 1)
		));
	}

	private static boolean canWander(PlayerMob player) {
		return player.canWander();
	}

	private static boolean isPreferredAttackTarget(PlayerMob player, LivingEntity target) {
		return getPreferredTarget(player).filter(preferredTarget -> preferredTarget == target).isPresent();
	}

	private static Optional<? extends LivingEntity> getPreferredTarget(PlayerMob player) {
		var angerTarget = LookTargetUtil.getEntity(player, MemoryModuleType.ANGRY_AT);
		if (angerTarget.isPresent() && Sensor.testAttackableTargetPredicateIgnoreVisibility(player, angerTarget.get())) {
			return angerTarget;
		}
		return Optional.empty();
	}

	protected static void onAttacked(PlayerMob player, LivingEntity attacker) {
		tryRevenge(player, attacker);
	}

	protected static void tryRevenge(PlayerMob player, LivingEntity target) {
		if (!Sensor.testAttackableTargetPredicateIgnoreVisibility(player, target)) {
			return;
		}
		if (LookTargetUtil.isNewTargetTooFar(player, target, 4.0)) {
			return;
		}
		becomeAngryWith(player, target);
	}


	protected static void becomeAngryWith(PlayerMob player, LivingEntity target) {
		if (!Sensor.testAttackableTargetPredicateIgnoreVisibility(player, target)) {
			return;
		}
		player.getBrain().remember(MemoryModuleType.ANGRY_AT, target.getUuid(), 600L);
	}

	protected static void tick(PlayerMob player) {
		Brain<PlayerMob> brain = player.getBrain();
		brain.tick((ServerWorld) player.getWorld(), player);
		var activity = brain.getFirstPossibleNonCoreActivity();
		if (activity.isPresent()) {
			brain.resetPossibleActivities(ImmutableList.of(Activity.FIGHT, Activity.IDLE));
		}
		var target = brain.getOptionalRegisteredMemory(MemoryModuleType.ATTACK_TARGET);
		player.setTarget(target.orElse(null));
		player.setAttacking(brain.hasMemoryModule(MemoryModuleType.ATTACK_TARGET));
	}
}
