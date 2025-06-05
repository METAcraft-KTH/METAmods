package nu.metacraft.minigame_util;

import net.minecraft.world.GameRules;
import se.datasektionen.mc.metacraft_core.mixin.AccessorGameRules;

public class MinigameGameRules {

	public static final GameRules.Key<GameRules.BooleanRule> REPLACE_BROKEN_BLOCKS = AccessorGameRules.callRegister(
			"replaceBrokenBlocks", GameRules.Category.PLAYER,
			AccessorGameRules.BooleanRule.callCreate(false)
	);

	public static void init() {

	}

}
