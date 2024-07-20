package se.datasektionen.mc.metacraft_lib.entity.goals;

import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.passive.ChickenEntity;
import se.datasektionen.mc.metacraft_lib.extensions.ChickenExtensions;

public class CuccoChickenAttackGoal extends MeleeAttackGoal {

	public CuccoChickenAttackGoal(ChickenEntity chicken) {
		super(chicken, 1.5, true);
	}

	@Override
	public boolean canStart() {
		return ((ChickenExtensions) mob).metacraft_lib$isCucco() && super.canStart();
	}
}
