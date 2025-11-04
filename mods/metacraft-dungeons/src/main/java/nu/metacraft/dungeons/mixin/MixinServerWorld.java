package nu.metacraft.dungeons.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import nu.metacraft.dungeons.dungeons.DungeonData;
import nu.metacraft.dungeons.extensions.ServerEntityManagerExtension;
import nu.metacraft.dungeons.extensions.ServerWorldExtension;

@Mixin(ServerLevel.class)
public class MixinServerWorld implements ServerWorldExtension {

	@Shadow @Final private PersistentEntitySectionManager<Entity> entityManager;
	@Unique
	private volatile boolean isBeingDeleted = false;

	@Inject(method = "addPlayer", at = @At("HEAD"), cancellable = true)
	private void addPlayer(ServerPlayer player, CallbackInfo ci) {
		DungeonData.getIfPresent((ServerLevel) (Object) this).ifPresent(data -> {
			if (data.isClearing()) {
				data.teleportOut(player);
				ci.cancel();
			}
		});
	}

	@Override
	public boolean metacraft$isBeingDeleted() {
		return isBeingDeleted;
	}

	@Override
	public void metacraft$setBeingDeleted(boolean beingDeleted) {
		this.isBeingDeleted = beingDeleted;
		((ServerEntityManagerExtension) this.entityManager).metacraft$setBeingDeleted(beingDeleted);
	}
}
