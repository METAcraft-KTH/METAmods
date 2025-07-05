package nu.metacraft.core.mixin;

import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerSyncHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ScreenHandler.class)
public interface AccessorScreenHandler {

	@Accessor
	ScreenHandlerSyncHandler getSyncHandler();

}
