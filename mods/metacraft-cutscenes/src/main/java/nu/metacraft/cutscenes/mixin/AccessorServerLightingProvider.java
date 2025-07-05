package nu.metacraft.cutscenes.mixin;

import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.util.thread.SimpleConsecutiveExecutor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerLightingProvider.class)
public interface AccessorServerLightingProvider {

	@Accessor
	SimpleConsecutiveExecutor getProcessor();

	@Invoker
	void callRunTasks();

}
