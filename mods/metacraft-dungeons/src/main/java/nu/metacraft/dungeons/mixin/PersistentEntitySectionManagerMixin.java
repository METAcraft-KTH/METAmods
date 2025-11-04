package nu.metacraft.dungeons.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.dungeons.extensions.ServerEntityManagerExtension;

import java.io.IOException;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;

@Mixin(PersistentEntitySectionManager.class)
public class PersistentEntitySectionManagerMixin implements ServerEntityManagerExtension {

	@Unique
	private boolean isBeingDeleted = false;

	@Inject(
			method = {"autoSave", "saveAll"},
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
