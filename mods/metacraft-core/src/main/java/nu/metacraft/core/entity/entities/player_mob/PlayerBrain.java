package nu.metacraft.core.entity.entities.player_mob;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BackUpIfTooClose;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.CrossbowAttack;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.EraseMemoryIf;
import net.minecraft.world.entity.ai.behavior.InteractWithDoor;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.MeleeAttack;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.RandomStroll;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromLookTarget;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.StartCelebratingIfTargetDead;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.entity.ai.behavior.StopBeingAngryIfTargetDead;
import net.minecraft.world.entity.ai.behavior.Swim;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
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
			MemoryModuleType.LOOK_TARGET, MemoryModuleType.DOORS_TO_CLOSE, MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_ATTACKABLE,
			MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_PLAYER,
			MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ADULT_PIGLINS,
			MemoryModuleType.NEARBY_ADULT_PIGLINS, MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM,
			MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS, MemoryModuleType.HURT_BY, MemoryModuleType.HURT_BY_ENTITY,
			MemoryModuleType.WALK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.ATTACK_TARGET,
			MemoryModuleType.ATTACK_COOLING_DOWN, MemoryModuleType.INTERACTION_TARGET, MemoryModuleType.PATH,
			MemoryModuleType.ANGRY_AT, MemoryModuleType.UNIVERSAL_ANGER, MemoryModuleType.AVOID_TARGET,
			MemoryModuleType.NEAREST_VISIBLE_NEMESIS, MemoryModuleType.RIDE_TARGET, MemoryModuleType.ATE_RECENTLY,
			METAcraftMemoryModules.NEAREST_OXYGEN, METAcraftMemoryModules.MOVE_TARGET, METAcraftMemoryModules.RECOVERING_BREATH,
			METAcraftMemoryModules.IS_SMART_SHOOTING
	);

	protected static Brain.Provider<PlayerMob> createBrainProfile() {
		return Brain.provider(MEMORY_MODULE_TYPES, SENSOR_TYPES);
	}

	protected static Brain<?> create(PlayerMob player, Brain<PlayerMob> brain) {
		addIdleActivities(brain);
		addCoreActivities(brain);
		addFightActivities(player, brain);
		brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
		brain.setDefaultActivity(Activity.IDLE);
		brain.useDefaultActivity();
		return brain;
	}

	protected static void addIdleActivities(Brain<PlayerMob> brain) {
		brain.addActivity(Activity.IDLE, 10, ImmutableList.of(
				StartAttacking.create((world, e) -> true, PlayerBrain::getPreferredTarget), makeRandomWanderTask(),
				GoToMoveTarget.create(METAcraftMemoryModules.MOVE_TARGET, 0.6f, 0),
				EraseMemoryIf.create(PlayerBrain::hasReachedMoveTarget, METAcraftMemoryModules.MOVE_TARGET)
		));
	}

	protected static void addCoreActivities(Brain<PlayerMob> brain) {
		brain.addActivity(Activity.CORE, 0, ImmutableList.of(
				new NeedToBreathe(1), new Swim<>(0.5f) {
					@Override
					protected boolean checkExtraStartConditions(ServerLevel serverWorld, Mob mobEntity) {
						return super.checkExtraStartConditions(serverWorld, mobEntity) && !mobEntity.isSwimming();
					}
				},
				new LookAtTargetSink(45, 90), new MoveToTargetSink(), InteractWithDoor.create(),
				StartCelebratingIfTargetDead.create(0, (player, target) -> false), StopBeingAngryIfTargetDead.create()
		));
	}

	protected static boolean isSmartProjectileWeapon(ItemStack stack) {
		return stack.getItem() instanceof BowItem || stack.getItem() instanceof TridentItem;
	}

	protected static boolean hasReachedMoveTarget(PlayerMob player) {
		var target = player.getBrain().getMemory(METAcraftMemoryModules.MOVE_TARGET);
		return target.filter(pos -> player.blockPosition().closerThan(pos.pos(), 1)).isPresent();
	}

	protected static boolean allowSetMovePos(PlayerMob player) {
		return !player.getBrain().hasMemoryValue(METAcraftMemoryModules.RECOVERING_BREATH) || !player.isUnderWater();
	}
	
	private static void addFightActivities(PlayerMob player, Brain<PlayerMob> brain) {
		brain.addActivityAndRemoveMemoryWhenStopped(Activity.FIGHT, 10, ImmutableList.of(
				StopAttackingIfTargetInvalid.create((world, target) -> !PlayerBrain.isPreferredAttackTarget(world, player, target)),
				BehaviorBuilder.triggerIf(PlayerBrain::isHoldingCrossbow, BackUpIfTooClose.create(5, 0.75f)),
				BehaviorBuilder.triggerIf(
						PlayerBrain::allowSetMovePos,
						(OneShot<Mob>) ImprovedRangedApproachTask.create(1.0f)
				), BehaviorBuilder.triggerIf(
						PlayerBrain::shouldAttackPhysical,
						MeleeAttack.create(20)
				), new CrossbowAttack<>(),
				new SmartShootAttackTask<>(PlayerBrain::isSmartProjectileWeapon, 20),
				new SmartStrafeAttackTask<>(1, 8)
		), MemoryModuleType.ATTACK_TARGET);
	}

	protected static boolean shouldAttackPhysical(PlayerMob player) {
		var target = player.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);
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

	private static RunOne<PlayerMob> makeRandomWanderTask() {
		return new RunOne<>(ImmutableList.of(
				Pair.of(BehaviorBuilder.triggerIf(PlayerBrain::canWander, RandomStroll.stroll(0.6f)), 1),
				Pair.of(BehaviorBuilder.triggerIf(PlayerBrain::canWander, SetWalkTargetFromLookTarget.create(0.6f, 3)), 1),
				Pair.of(new DoNothing(50, 100), 1)
		));
	}

	private static boolean canWander(PlayerMob player) {
		return player.canWander();
	}

	protected static boolean isPreferredAttackTarget(ServerLevel world, PlayerMob player, LivingEntity target) {
		return getPreferredTarget(world, player).filter(preferredTarget -> preferredTarget == target).isPresent();
	}

	@SuppressWarnings({"OptionalAssignedToNull"})
	private static Optional<? extends LivingEntity> getPreferredTarget(ServerLevel world, PlayerMob player) {
		var angerTarget = BehaviorUtils.getLivingEntityFromUUIDMemory(player, MemoryModuleType.ANGRY_AT);
		if (angerTarget.isPresent() && Sensor.isEntityAttackableIgnoringLineOfSight(world, player, angerTarget.get())) {
			return angerTarget;
		}
		var nearest = player.getBrain().getMemoryInternal(MemoryModuleType.NEAREST_ATTACKABLE);
		if (nearest != null) {
			return nearest;
		}
		return Optional.empty();
	}

	protected static void onAttacked(ServerLevel world, PlayerMob player, LivingEntity attacker) {
		tryRevenge(world, player, attacker);
	}

	protected static void tryRevenge(ServerLevel world, PlayerMob player, LivingEntity target) {
		if (!Sensor.isEntityAttackableIgnoringLineOfSight(world, player, target)) {
			return;
		}
		if (BehaviorUtils.isOtherTargetMuchFurtherAwayThanCurrentAttackTarget(player, target, 4.0)) {
			return;
		}
		becomeAngryWith(world, player, target);
	}


	protected static void becomeAngryWith(ServerLevel world, PlayerMob player, LivingEntity target) {
		if (!Sensor.isEntityAttackableIgnoringLineOfSight(world, player, target)) {
			return;
		}
		player.getBrain().setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, target.getUUID(), 600L);
	}

	protected static void tick(ServerLevel world, PlayerMob player) {
		Brain<PlayerMob> brain = player.getBrain();
		brain.tick(world, player);
		var activity = brain.getActiveNonCoreActivity();
		if (activity.isPresent()) {
			brain.setActiveActivityToFirstValid(ImmutableList.of(Activity.FIGHT, Activity.IDLE));
		}
		var target = brain.getMemory(MemoryModuleType.ATTACK_TARGET);
		player.setTarget(target.orElse(null));
		player.setAggressive(brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET));
	}
}
