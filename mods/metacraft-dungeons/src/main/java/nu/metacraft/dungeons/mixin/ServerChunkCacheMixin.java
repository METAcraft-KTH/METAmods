package nu.metacraft.dungeons.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.dungeons.extensions.ServerLevelExtension;

import java.io.IOException;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;

@Mixin(ServerChunkCache.class)
public class ServerChunkCacheMixin {

	@Shadow @Final
	ServerLevel level;

	@Inject(
		method = "save",
		at = @At("HEAD"),
		cancellable = true
	)
	public void save(boolean flush, CallbackInfo ci) throws IOException {
		if (((ServerLevelExtension) level).metacraft$isBeingDeleted()) {
			ci.cancel();
		}
	}

}
