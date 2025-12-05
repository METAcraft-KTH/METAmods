package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.lib.extensions.ChickenExtensions;
import nu.metacraft.lib.entity.goals.CuccoChickenAttackGoal;
import nu.metacraft.lib.entity.goals.CuccoRevengeGoal;
import nu.metacraft.lib.util.Particles;

@Mixin(Chicken.class)
public abstract class ChickenMixin extends Animal implements ChickenExtensions {

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

	protected ChickenMixin(EntityType<? extends Animal> entityType, Level world) {
		super(entityType, world);
	}

	@ModifyReturnValue(method = "createAttributes", at = @At("RETURN"))
	private static AttributeSupplier.Builder initAttributes(AttributeSupplier.Builder builder) {
		return builder.add(Attributes.ATTACK_DAMAGE, 5);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	public void toNBT(ValueOutput nbt, CallbackInfo ci) {
		nbt.putBoolean(CUCCO, isCucco);
		nbt.putInt(REINFORCEMENT_COUNT, reinforcementCount);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	public void fromNBT(ValueInput nbt, CallbackInfo ci) {
		isCucco = nbt.getBooleanOr(CUCCO, false);
		reinforcementCount = nbt.getInt(REINFORCEMENT_COUNT).orElseGet(() -> getRandom().nextInt(20));
	}

	@Inject(method = "registerGoals", at = @At("HEAD"))
	protected void initGoals(CallbackInfo ci) {
		this.goalSelector.addGoal(1, new CuccoChickenAttackGoal((Chicken) (Object) this));
		cuccoRevengeGoal = new CuccoRevengeGoal((Chicken) (Object) this);
		this.targetSelector.addGoal(1, cuccoRevengeGoal);
	}

	@Unique
	private int particleDelay = 0;

	@Inject(method = "aiStep", at = @At("RETURN"))
	public void tick(CallbackInfo ci) {
		if (!level().isClientSide() && isCucco) {
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
			cuccoRevengeGoal.alertOthers();
		}
	}
}
