package se.datasektionen.mc.metacraft_lib.entity.goals;

import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.mob.PathAwareEntity;
import se.datasektionen.mc.metacraft_lib.extensions.LivingEntityExtensions;

public class HostileMobAttackGoal extends MeleeAttackGoal {
	public HostileMobAttackGoal(PathAwareEntity mob) {
		super(mob, 1.25, true);
	}

	@Override
	public boolean canStart() {
		return ((LivingEntityExtensions) mob).metacraft_lib$isHostile() && super.canStart();
	}
}
