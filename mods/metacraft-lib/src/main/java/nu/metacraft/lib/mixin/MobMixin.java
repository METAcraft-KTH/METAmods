package nu.metacraft.lib.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.lib.extensions.MonsterExtensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import nu.metacraft.lib.entity.EntityParameters;
import nu.metacraft.lib.entity.goals.HostileMobAttackGoal;
import nu.metacraft.lib.entity.goals.HostileMobTargetGoal;

@Mixin(Mob.class)
public abstract class MobMixin extends LivingEntity {

	@Shadow @Final protected GoalSelector goalSelector;

	@Shadow @Final protected GoalSelector targetSelector;

	protected MobMixin(EntityType<? extends LivingEntity> entityType, Level world) {
		super(entityType, world);
	}

	@Inject(method = "<init>", at = @At("RETURN"))
	protected void initGoals(CallbackInfo ci) {
		if (level() != null && !level().isClientSide() && (Object) this instanceof PathfinderMob) {
			var brain = (BrainAccessor) this.brain;
			if (brain.getMemories().isEmpty() && brain.getActivityRequirements().isEmpty()) {
				this.goalSelector.addGoal(1, new HostileMobAttackGoal((PathfinderMob) (Object) this));
				this.targetSelector.addGoal(1, new HostileMobTargetGoal((PathfinderMob) (Object) this));
			}
		}
	}


	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	public void toNBT(ValueOutput nbt, CallbackInfo ci) {
		if ((Object) this instanceof Monster) {
			nbt.putBoolean(EntityParameters.SURVIVES_SUNLIGHT, ((MonsterExtensions) this).metacraft_lib$survivesSunlight());
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	public void fromNBT(ValueInput nbt, CallbackInfo ci) {
		if ((Object) this instanceof Monster) {
			((MonsterExtensions) this).metacraft_lib$setSurvivesSunlight(nbt.getBooleanOr(EntityParameters.SURVIVES_SUNLIGHT, false));
		}
	}

	@Inject(method = "isSunBurnTick", at = @At("HEAD"), cancellable = true)
	protected void isAffectedByDaylight(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Monster && ((MonsterExtensions) this).metacraft_lib$survivesSunlight()) {
			cir.setReturnValue(false);
		}
	}

}
