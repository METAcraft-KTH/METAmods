package nu.metacraft.mob_modifiers.mixin;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import nu.metacraft.mob_modifiers.util.Particles;
import nu.metacraft.mob_modifiers.METAcraftMobModifiers;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.mob_modifiers.extensions.ChickenExtensions;
import nu.metacraft.mob_modifiers.extensions.LivingEntityExtensions;
import nu.metacraft.mob_modifiers.entity.EntityParameters;

import java.util.Optional;
import java.util.Set;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity implements LivingEntityExtensions {

	@Unique
	private static final String ANGRY = "AnimalAngry";

	@Unique
	private static final String HAS_ANGER_PARTICLES = "AngerParticles";

	public LivingEntityMixin(EntityType<?> type, Level world) {
		super(type, world);
	}

	@Shadow public abstract @Nullable LivingEntity getLastHurtByMob();

	@Shadow protected Brain<?> brain;

	@Shadow public abstract @Nullable AttributeInstance getAttribute(Holder<Attribute> attribute);

	@Unique
	private boolean isHostile;

	@Unique
	private boolean hasAngerParticles;


	@Inject(method = "die", at = @At("RETURN"))
	public void onDeath(DamageSource damageSource, CallbackInfo ci) {
		if ((Object) this instanceof Chicken && ((ChickenExtensions) this).metacraft_lib$isCucco()) {
			((ChickenExtensions) this).metacraft_lib$setReinforcementCount(
					((ChickenExtensions) this).metacraft_lib$getReinforcementCount() * 2
			);
			this.getAttribute(Attributes.FOLLOW_RANGE).addTransientModifier(
					new AttributeModifier(METAcraftMobModifiers.getID("double_range"), 2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
			);
			if (this.getLastHurtByMob() != null) {
				((ChickenExtensions) this).metacraft_lib$makeNearbyChickensAngry();
			}
		}
	}

	@Unique
	private int particleDelay = 0;

	@Inject(method = "tick", at = @At("RETURN"))
	public void tick(CallbackInfo ci) {
		if (!level().isClientSide() && hasAngerParticles) {
			if (particleDelay <= 0) {
				Particles.spawnAngerParticles(this, random);
				particleDelay = 10 + random.nextInt(30);
			} else {
				particleDelay--;
			}
		}
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	public void toNBT(ValueOutput nbt, CallbackInfo ci) {
		nbt.putBoolean(EntityParameters.IS_HOSTILE, isHostile);
		nbt.putBoolean(HAS_ANGER_PARTICLES, hasAngerParticles);
	}

	@Unique
	private static final String FORGET_ATTACK_TARGET_TASK = StopAttackingIfTargetInvalid.class.getName();

	@Unique
	private static final String UPDATE_ATTACK_TARGET_TASK = StartAttacking.class.getName();

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	public void fromNBT(ValueInput nbt, CallbackInfo ci) {
		isHostile = nbt.getBooleanOr(EntityParameters.IS_HOSTILE, false);
		hasAngerParticles = nbt.getBooleanOr(HAS_ANGER_PARTICLES, false);
		if (nbt.getBooleanOr(ANGRY, false)) {
			isHostile = true;
			hasAngerParticles = true;
		}
		if (isHostile && (Object) this instanceof Mob) {
			((BrainAccessor) this.brain).getAvailableBehaviorsByPriority().forEach((id, tasks) -> {
				tasks.forEach((activity, taskSet) -> {
					taskSet.removeIf(task -> {
						return task.debugString().contains(FORGET_ATTACK_TARGET_TASK) || task.debugString().contains(UPDATE_ATTACK_TARGET_TASK);
					});
				});
			});
			var startAttacking = ImmutableList.of(
					Pair.of(
							0,
							StartAttacking.create(LivingEntityMixin::getTarget)
					)
			);
			((Brain<? extends Mob>) this.brain).addActivity(
					Activity.IDLE, startAttacking,
					Set.of(), Set.of()
			);
			((Brain<? extends Mob>) this.brain).addActivity(
					Activity.FIGHT, startAttacking,
					Set.of(), Set.of()
			);
			((Brain<? extends Mob>) this.brain).addActivity(
					Activity.FIGHT,
					ImmutableList.of(
							Pair.of(
									0,
									StopAttackingIfTargetInvalid.create(
											(world, entity) -> getTarget(world, (LivingEntity) (Object) this).filter(
													target -> target == entity
											).isEmpty()
									)
							)
					),
					Set.of(),
					Set.of(MemoryModuleType.ATTACK_TARGET)
			);
		}
	}

	@ModifyReturnValue(method = "createLivingAttributes", at = @At("RETURN"))
	private static AttributeSupplier.Builder createMobAttributes(AttributeSupplier.Builder original) {
		return original.add(Attributes.ATTACK_DAMAGE, 5);
	}

	@Unique
	private static <T> Optional<T> getMemory(LivingEntity entity, MemoryModuleType<T> memoryModuleType) {
		return Optional.ofNullable(entity.getBrain().getMemoryInternal(memoryModuleType)).flatMap(o -> o);
	}

	@Unique
	private static Optional<? extends LivingEntity> getTarget(ServerLevel world, LivingEntity entity) {
		Optional<LivingEntity> angryAt = getMemory(entity, MemoryModuleType.ANGRY_AT).map(
			uuid -> {
				var target = ((ServerLevel) entity.level()).getEntity(uuid);
				if (target instanceof LivingEntity living) {
					return living;
				} else {
					return null;
				}
			}
		);
		if (angryAt.isPresent() && Sensor.isEntityAttackableIgnoringLineOfSight(world, entity, angryAt.get())) {
			return angryAt;
		}
		Optional<? extends LivingEntity> foundTarget = getMemory(
				entity, MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER
		).filter(
				target -> target.closerThan(entity, entity.getAttributeValue(Attributes.FOLLOW_RANGE))
		);
		if (foundTarget.isPresent()) {
			return foundTarget;
		}
		return getMemory(entity, MemoryModuleType.NEAREST_VISIBLE_NEMESIS);
	}

	@Override
	public boolean metacraft_lib$isHostile() {
		return isHostile;
	}

	@Override
	public void metacraft_lib$setHostile(boolean hostile) {
		this.isHostile = hostile;
	}

}
