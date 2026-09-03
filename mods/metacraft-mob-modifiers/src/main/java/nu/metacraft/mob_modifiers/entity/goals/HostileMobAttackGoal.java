package nu.metacraft.mob_modifiers.entity.goals;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import nu.metacraft.mob_modifiers.extensions.LivingEntityExtensions;

public class HostileMobAttackGoal extends MeleeAttackGoal {
	public HostileMobAttackGoal(PathfinderMob mob) {
		super(mob, 1.25, true);
	}

	@Override
	public boolean canUse() {
		return ((LivingEntityExtensions) mob).metacraft_lib$isHostile() && super.canUse();
	}
}
