package nu.metacraft.lib.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.lib.extensions.HostileEntityExtensions;
import nu.metacraft.lib.entity.EntityParameters;
import nu.metacraft.lib.entity.goals.HostileMobAttackGoal;
import nu.metacraft.lib.entity.goals.HostileMobTargetGoal;

@Mixin(MobEntity.class)
public abstract class MixinMobEntity extends LivingEntity {

	@Shadow @Final protected GoalSelector goalSelector;

	@Shadow @Final protected GoalSelector targetSelector;

	protected MixinMobEntity(EntityType<? extends LivingEntity> entityType, World world) {
		super(entityType, world);
	}

	@Inject(method = "<init>", at = @At("RETURN"))
	protected void initGoals(CallbackInfo ci) {
		if (getWorld() != null && !getWorld().isClient() && (Object) this instanceof PathAwareEntity) {
			AccessorBrainProfile profile = (AccessorBrainProfile) (Object) this.createBrainProfile();
			if (profile.getMemoryModules().isEmpty() && profile.getSensors().isEmpty()) {
				this.goalSelector.add(1, new HostileMobAttackGoal((PathAwareEntity) (Object) this));
				this.targetSelector.add(1, new HostileMobTargetGoal((PathAwareEntity) (Object) this));
			}
		}
	}


	@Inject(method = "writeCustomData", at = @At("RETURN"))
	public void toNBT(WriteView nbt, CallbackInfo ci) {
		if ((Object) this instanceof HostileEntity) {
			nbt.putBoolean(EntityParameters.SURVIVES_SUNLIGHT, ((HostileEntityExtensions) this).metacraft_lib$survivesSunlight());
		}
	}

	@Inject(method = "readCustomData", at = @At("RETURN"))
	public void fromNBT(ReadView nbt, CallbackInfo ci) {
		if ((Object) this instanceof HostileEntity) {
			((HostileEntityExtensions) this).metacraft_lib$setSurvivesSunlight(nbt.getBoolean(EntityParameters.SURVIVES_SUNLIGHT, false));
		}
	}

	@Inject(method = "isAffectedByDaylight", at = @At("HEAD"), cancellable = true)
	protected void isAffectedByDaylight(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof HostileEntity && ((HostileEntityExtensions) this).metacraft_lib$survivesSunlight()) {
			cir.setReturnValue(false);
		}
	}

}
