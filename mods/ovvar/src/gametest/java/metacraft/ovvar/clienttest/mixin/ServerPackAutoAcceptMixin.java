package metacraft.ovvar.clienttest.mixin;

import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The test client accepts every server's resource pack without the prompt: the connect step of
 * the test API blocks until the world loads, and the pack prompt would block it first. With the
 * status enabled up front the client downloads and applies the pack unasked. Test source set only.
 */
@Mixin(ServerData.class)
public abstract class ServerPackAutoAcceptMixin {
	@Inject(method = "<init>", at = @At("RETURN"))
	private void ovvar$acceptPacks(CallbackInfo ci) {
		((ServerData) (Object) this).setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
	}
}
