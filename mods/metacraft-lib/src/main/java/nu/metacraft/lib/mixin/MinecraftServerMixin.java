package nu.metacraft.lib.mixin;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.lib.util.helper.TestHelper;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

	@Inject(method = "stopServer", at = @At("HEAD"), cancellable = true)
	public void onShutdown(CallbackInfo ci) {
		if ((Object) this instanceof GameTestServer && TestHelper.isJunit()) {
			ci.cancel();
		}
	}

}
