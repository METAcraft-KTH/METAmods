package nu.metacraft.cutscenes.mixin;

import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.thread.ConsecutiveExecutor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ThreadedLevelLightEngine.class)
public interface AccessorServerLightingProvider {

	@Accessor
	ConsecutiveExecutor getConsecutiveExecutor();

	@Invoker
	void callRunUpdate();

}
