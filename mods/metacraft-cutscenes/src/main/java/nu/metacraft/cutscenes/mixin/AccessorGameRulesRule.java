package nu.metacraft.cutscenes.mixin;

import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRules.Value.class)
public interface AccessorGameRulesRule {

	@Invoker
	void callDeserialize(String value);

}
