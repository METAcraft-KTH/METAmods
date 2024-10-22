package se.datasektionen.mc.metacraft_core.gamerules;

import net.minecraft.world.GameRules;
import se.datasektionen.mc.metacraft_core.mixin.AccessorGameRules;

public class METAcraftGameRules {

	public static final GameRules.Key<GameRules.BooleanRule> DO_ARMOR_DAMAGE = AccessorGameRules.callRegister(
			"doArmorDamage", GameRules.Category.PLAYER,
			AccessorGameRules.BooleanRule.callCreate(true)
	);

	public static void init() {

	}

}
