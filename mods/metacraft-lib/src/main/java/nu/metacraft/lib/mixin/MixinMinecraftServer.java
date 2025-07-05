package nu.metacraft.lib.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.test.TestServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.lib.util.helper.TestHelper;

@Mixin(MinecraftServer.class)
public class MixinMinecraftServer {

	@Inject(method = "shutdown", at = @At("HEAD"), cancellable = true)
	public void onShutdown(CallbackInfo ci) {
		if ((Object) this instanceof TestServer && TestHelper.isJunit()) {
			ci.cancel();
		}
	}

}
