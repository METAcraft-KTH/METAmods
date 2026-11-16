package se.metacraft.ovvar.clienttest.mixin;

import net.minecraft.client.resources.server.DownloadedPackSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.metacraft.ovvar.clienttest.ServerPackActivity;

import java.net.URL;
import java.util.UUID;

/** Counts the server's pack pushes and the reloads that apply them (ServerPackActivity). Test source set only. */
@Mixin(DownloadedPackSource.class)
public abstract class ServerPackAppliedMixin {
	@Inject(method = "pushPack", at = @At("HEAD"))
	private void ovvar$pushed(UUID id, URL url, String hash, CallbackInfo ci) {
		ServerPackActivity.pushed(hash);
	}

	@Inject(method = "startReload", at = @At("HEAD"))
	private void ovvar$reloadStarted(CallbackInfo ci) {
		ServerPackActivity.reloadStarted();
	}

	@Inject(method = "onReloadSuccess", at = @At("HEAD"))
	private void ovvar$reloadSucceeded(CallbackInfo ci) {
		ServerPackActivity.reloadSucceeded();
	}
}
