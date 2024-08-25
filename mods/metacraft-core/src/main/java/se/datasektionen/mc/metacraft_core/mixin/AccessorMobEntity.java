package se.datasektionen.mc.metacraft_core.mixin;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MobEntity.class)
public interface AccessorMobEntity {

	@Accessor("MOB_FLAGS")
	static TrackedData<Byte> getMobFlags() {
		throw new IllegalStateException("Mixin Error");
	}

	@Accessor
	GoalSelector getGoalSelector();

	@Accessor
	GoalSelector getTargetSelector();
}
