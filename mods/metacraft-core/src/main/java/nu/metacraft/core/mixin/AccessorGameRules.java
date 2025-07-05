package nu.metacraft.core.mixin;

import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRules.class)
public interface AccessorGameRules {

	@Invoker
	static <T extends GameRules.Rule<T>> GameRules.Key<T> callRegister(
			String name, GameRules.Category category, GameRules.Type<T> type
	) {
		throw new IllegalStateException("Mixin Error");
	}

	@Mixin(GameRules.BooleanRule.class)
	interface BooleanRule {
		@Invoker
		static GameRules.Type<GameRules.BooleanRule> callCreate(boolean initialValue) {
			throw new IllegalStateException("Mixin Error");
		}
	}

}
