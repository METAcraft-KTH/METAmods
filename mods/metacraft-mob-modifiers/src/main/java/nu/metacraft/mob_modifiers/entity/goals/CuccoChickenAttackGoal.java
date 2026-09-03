package nu.metacraft.mob_modifiers.entity.goals;

import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import nu.metacraft.mob_modifiers.extensions.ChickenExtensions;

public class CuccoChickenAttackGoal extends MeleeAttackGoal {

	public CuccoChickenAttackGoal(Chicken chicken) {
		super(chicken, 1.5, true);
	}

	@Override
	public boolean canUse() {
		return ((ChickenExtensions) mob).metacraft_lib$isCucco() && super.canUse();
	}
}
