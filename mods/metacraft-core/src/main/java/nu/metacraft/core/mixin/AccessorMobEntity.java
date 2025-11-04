package nu.metacraft.core.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Mob.class)
public interface AccessorMobEntity {

	@Accessor("DATA_MOB_FLAGS_ID")
	static EntityDataAccessor<Byte> getMobFlags() {
		throw new IllegalStateException("Mixin Error");
	}

	@Accessor
	GoalSelector getGoalSelector();

	@Accessor
	GoalSelector getTargetSelector();
}
