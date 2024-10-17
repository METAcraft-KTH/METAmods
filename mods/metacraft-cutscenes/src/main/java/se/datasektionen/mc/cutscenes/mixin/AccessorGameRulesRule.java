package se.datasektionen.mc.cutscenes.mixin;

import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRules.Rule.class)
public interface AccessorGameRulesRule {

	@Invoker
	void callDeserialize(String value);

}
