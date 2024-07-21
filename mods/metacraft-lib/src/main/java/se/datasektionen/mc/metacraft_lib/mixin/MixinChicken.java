package se.datasektionen.mc.metacraft_lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_lib.extensions.ChickenExtensions;
import se.datasektionen.mc.metacraft_lib.entity.goals.CuccoChickenAttackGoal;
import se.datasektionen.mc.metacraft_lib.entity.goals.CuccoRevengeGoal;
import se.datasektionen.mc.metacraft_lib.util.Particles;

@Mixin(ChickenEntity.class)
public abstract class MixinChicken extends AnimalEntity implements ChickenExtensions {

	@Unique
	private static final String CUCCO = "Cucco";

	@Unique
	private static final String REINFORCEMENT_COUNT = "ReinforcementCount";

	@Unique
	private boolean isCucco = false;

	@Unique
	private int reinforcementCount = getRandom().nextInt(20);

	@Unique
	private CuccoRevengeGoal cuccoRevengeGoal;

	protected MixinChicken(EntityType<? extends AnimalEntity> entityType, World world) {
		super(entityType, world);
	}

	@ModifyReturnValue(method = "createChickenAttributes", at = @At("RETURN"))
	private static DefaultAttributeContainer.Builder initAttributes(DefaultAttributeContainer.Builder builder) {
		return builder.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5);
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("RETURN"))
	public void toNBT(NbtCompound nbt, CallbackInfo ci) {
		nbt.putBoolean(CUCCO, isCucco);
		nbt.putInt(REINFORCEMENT_COUNT, reinforcementCount);
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("RETURN"))
	public void fromNBT(NbtCompound nbt, CallbackInfo ci) {
		isCucco = nbt.getBoolean(CUCCO);
		if (nbt.contains(REINFORCEMENT_COUNT)) {
			reinforcementCount = nbt.getInt(REINFORCEMENT_COUNT);
		}
	}

	@Inject(method = "initGoals", at = @At("HEAD"))
	protected void initGoals(CallbackInfo ci) {
		this.goalSelector.add(1, new CuccoChickenAttackGoal((ChickenEntity) (Object) this));
		cuccoRevengeGoal = new CuccoRevengeGoal((ChickenEntity) (Object) this);
		this.targetSelector.add(1, cuccoRevengeGoal);
	}

	@Unique
	private int particleDelay = 0;

	@Inject(method = "tickMovement", at = @At("RETURN"))
	public void tick(CallbackInfo ci) {
		if (!getWorld().isClient() && isCucco) {
			if (particleDelay <= 0) {
				Particles.spawnAngerParticles(this, random);
				particleDelay = 10 + random.nextInt(30);
			} else {
				particleDelay--;
			}
		}
	}

	@Override
	public boolean metacraft_lib$isCucco() {
		return isCucco;
	}

	@Override
	public void metacraft_lib$setCucco(boolean isCucco) {
		this.isCucco = isCucco;
	}

	@Override
	public int metacraft_lib$getReinforcementCount() {
		return reinforcementCount;
	}

	@Override
	public void metacraft_lib$setReinforcementCount(int count) {
		reinforcementCount = count;
	}


	@Override
	public void metacraft_lib$makeNearbyChickensAngry() {
		if (cuccoRevengeGoal != null) {
			cuccoRevengeGoal.callSameTypeForRevenge();
		}
	}
}
