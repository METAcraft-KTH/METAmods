package se.datasektionen.mc.metacraft_lib.mixin;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.sensor.Sensor;
import net.minecraft.entity.ai.brain.task.ForgetAttackTargetTask;
import net.minecraft.entity.ai.brain.task.UpdateAttackTargetTask;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;
import se.datasektionen.mc.metacraft_lib.extensions.ChickenExtensions;
import se.datasektionen.mc.metacraft_lib.extensions.LivingEntityExtensions;
import se.datasektionen.mc.metacraft_lib.entity.EntityParameters;
import se.datasektionen.mc.metacraft_lib.util.Particles;

import java.util.Optional;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity implements LivingEntityExtensions {

	@Unique
	private static final String ANGRY = "AnimalAngry";

	@Unique
	private static final String HAS_ANGER_PARTICLES = "AngerParticles";

	public MixinLivingEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@Shadow public abstract @Nullable LivingEntity getAttacker();

	@Shadow protected Brain<?> brain;

	@Shadow public abstract @Nullable EntityAttributeInstance getAttributeInstance(RegistryEntry<EntityAttribute> attribute);

	@Unique
	private boolean isHostile;

	@Unique
	private boolean hasAngerParticles;


	@Inject(method = "onDeath", at = @At("RETURN"))
	public void onDeath(DamageSource damageSource, CallbackInfo ci) {
		if ((Object) this instanceof ChickenEntity && ((ChickenExtensions) this).metacraft_lib$isCucco()) {
			((ChickenExtensions) this).metacraft_lib$setReinforcementCount(
					((ChickenExtensions) this).metacraft_lib$getReinforcementCount() * 2
			);
			this.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE).addTemporaryModifier(
					new EntityAttributeModifier(METAcraftLib.getID("double_range"), 2, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
			);
			if (this.getAttacker() != null) {
				((ChickenExtensions) this).metacraft_lib$makeNearbyChickensAngry();
			}
		}
	}

	@Unique
	private int particleDelay = 0;

	@Inject(method = "tick", at = @At("RETURN"))
	public void tick(CallbackInfo ci) {
		if (!getWorld().isClient() && hasAngerParticles) {
			if (particleDelay <= 0) {
				Particles.spawnAngerParticles(this, random);
				particleDelay = 10 + random.nextInt(30);
			} else {
				particleDelay--;
			}
		}
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("RETURN"))
	public void toNBT(NbtCompound nbt, CallbackInfo ci) {
		nbt.putBoolean(EntityParameters.IS_HOSTILE, isHostile);
		nbt.putBoolean(HAS_ANGER_PARTICLES, hasAngerParticles);
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("RETURN"))
	public void fromNBT(NbtCompound nbt, CallbackInfo ci) {
		isHostile = nbt.getBoolean(EntityParameters.IS_HOSTILE);
		hasAngerParticles = nbt.getBoolean(HAS_ANGER_PARTICLES);
		if (nbt.contains(ANGRY)) {
			isHostile = nbt.getBoolean(ANGRY);
			hasAngerParticles = nbt.getBoolean(ANGRY);
		}
		if (isHostile && (Object) this instanceof MobEntity) {
			((AccessorBrain) this.brain).getTasks().forEach((id, tasks) -> {
				tasks.forEach((activity, taskSet) -> {
					taskSet.removeIf(task -> {
						return task.getName().contains("ForgetAttackTargetTask") || task.getName().contains("UpdateAttackTargetTask");
					});
				});
			});
			((Brain<? extends MobEntity>) this.brain).setTaskList(
					Activity.IDLE, 0,
					ImmutableList.of(
							UpdateAttackTargetTask.create(MixinLivingEntity::getTarget)
					)
			);
			((Brain<? extends MobEntity>) this.brain).setTaskList(
					Activity.FIGHT, 0,
					ImmutableList.of(
							ForgetAttackTargetTask.create(
									entity -> getTarget((LivingEntity) (Object) this).filter(
											target -> target == entity
									).isEmpty()
							)
					), MemoryModuleType.ATTACK_TARGET
			);
		}
	}

	@ModifyReturnValue(method = "createLivingAttributes", at = @At("RETURN"))
	private static DefaultAttributeContainer.Builder createMobAttributes(DefaultAttributeContainer.Builder original) {
		return original.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5);
	}

	@Unique
	private static <T> Optional<T> getMemory(LivingEntity entity, MemoryModuleType<T> memoryModuleType) {
		return Optional.ofNullable(entity.getBrain().getOptionalMemory(memoryModuleType)).flatMap(o -> o);
	}

	@Unique
	private static Optional<? extends LivingEntity> getTarget(LivingEntity entity) {
		Optional<LivingEntity> angryAt = getMemory(entity, MemoryModuleType.ANGRY_AT).map(
			uuid -> {
				var target = ((ServerWorld) entity.getWorld()).getEntity(uuid);
				if (target instanceof LivingEntity living) {
					return living;
				} else {
					return null;
				}
			}
		);
		if (angryAt.isPresent() && Sensor.testAttackableTargetPredicateIgnoreVisibility(entity, angryAt.get())) {
			return angryAt;
		}
		Optional<? extends LivingEntity> foundTarget = getMemory(
				entity, MemoryModuleType.NEAREST_VISIBLE_TARGETABLE_PLAYER
		).filter(
				target -> target.isInRange(entity, entity.getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE))
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

}
