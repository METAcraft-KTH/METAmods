package nu.metacraft.core.gamerules;

import net.minecraft.world.level.GameRules;
import nu.metacraft.core.mixin.GameRulesAccessor;

public class METAcraftGameRules {

	public static final GameRules.Key<GameRules.BooleanValue> DO_ARMOR_DAMAGE = GameRulesAccessor.callRegister(
			"doArmorDamage", GameRules.Category.PLAYER,
			GameRulesAccessor.BooleanValue.callCreate(true)
	);

	public static void init() {

	}

}
