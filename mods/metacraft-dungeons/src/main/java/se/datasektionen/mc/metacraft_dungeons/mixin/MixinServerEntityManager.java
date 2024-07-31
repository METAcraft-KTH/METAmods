package se.datasektionen.mc.metacraft_dungeons.mixin;

import net.minecraft.server.world.ServerEntityManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_dungeons.extensions.ServerEntityManagerExtension;

import java.io.IOException;

@Mixin(ServerEntityManager.class)
public class MixinServerEntityManager implements ServerEntityManagerExtension {

	@Unique
	private boolean isBeingDeleted = false;

	@Inject(
			method = {"save", "flush"},
			at = @At("HEAD"),
			cancellable = true
	)
	public void save(CallbackInfo ci) throws IOException {
		if (isBeingDeleted) {
			ci.cancel();
		}
	}

	@Override
	public void metacraft$setBeingDeleted(boolean beingDeleted) {
		this.isBeingDeleted = beingDeleted;
	}
}
