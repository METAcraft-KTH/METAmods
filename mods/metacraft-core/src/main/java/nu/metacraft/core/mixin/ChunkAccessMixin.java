package nu.metacraft.core.mixin;

import net.minecraft.world.level.chunk.ChunkAccess;
import nu.metacraft.core.commands.InhabitedTimeCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkAccess.class)
public class ChunkAccessMixin {
	@Inject(method = "incrementInhabitedTime", at = @At("HEAD"), cancellable = true)
	private void onIncreaseInhabitedTime(long timeDelta, CallbackInfo ci) {
		if (InhabitedTimeCommand.frozen) {
			ci.cancel();
		}
	}
}
