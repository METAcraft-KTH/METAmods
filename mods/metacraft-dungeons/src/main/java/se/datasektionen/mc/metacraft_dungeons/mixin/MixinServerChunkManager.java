package se.datasektionen.mc.metacraft_dungeons.mixin;

import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_dungeons.extensions.ServerWorldExtension;

import java.io.IOException;

@Mixin(ServerChunkManager.class)
public class MixinServerChunkManager {

	@Shadow @Final
	ServerWorld world;

	@Inject(
		method = "save",
		at = @At("HEAD"),
		cancellable = true
	)
	public void save(boolean flush, CallbackInfo ci) throws IOException {
		if (((ServerWorldExtension) world).metacraft$isBeingDeleted()) {
			ci.cancel();
		}
	}

}
