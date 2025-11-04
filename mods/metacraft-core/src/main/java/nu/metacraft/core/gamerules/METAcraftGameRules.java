package nu.metacraft.core.gamerules;

import net.minecraft.world.level.GameRules;
import nu.metacraft.core.mixin.AccessorGameRules;

public class METAcraftGameRules {

	public static final GameRules.Key<GameRules.BooleanValue> DO_ARMOR_DAMAGE = AccessorGameRules.callRegister(
			"doArmorDamage", GameRules.Category.PLAYER,
			AccessorGameRules.BooleanRule.callCreate(true)
	);

	public static void init() {

	}

}
