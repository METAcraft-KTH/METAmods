package nu.metacraft.pointsystem.mixin;

import net.minecraft.TracingExecutor;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.throwables.MixinError;

@Mixin(Util.class)
public interface UtilAccessor {

	@Invoker
	static TracingExecutor callMakeExecutor(String string) {
		throw new MixinError("Not applied");
	}

}
