package se.datasektionen.mc.metacraft_lib.entity.goals;

import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import se.datasektionen.mc.metacraft_lib.extensions.LivingEntityExtensions;

public class HostileMobTargetGoal extends ActiveTargetGoal<PlayerEntity> {
	public HostileMobTargetGoal(PathAwareEntity mob) {
		super(mob, PlayerEntity.class, true);
	}

	@Override
	public boolean canStart() {
		return ((LivingEntityExtensions) mob).metacraft_lib$isHostile() && super.canStart();
	}
}
