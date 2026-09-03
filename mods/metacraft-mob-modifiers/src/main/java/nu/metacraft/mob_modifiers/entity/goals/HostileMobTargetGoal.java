package nu.metacraft.mob_modifiers.entity.goals;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import nu.metacraft.mob_modifiers.extensions.LivingEntityExtensions;

public class HostileMobTargetGoal extends NearestAttackableTargetGoal<Player> {
	public HostileMobTargetGoal(PathfinderMob mob) {
		super(mob, Player.class, true);
	}

	@Override
	public boolean canUse() {
		return ((LivingEntityExtensions) mob).metacraft_lib$isHostile() && super.canUse();
	}
}
