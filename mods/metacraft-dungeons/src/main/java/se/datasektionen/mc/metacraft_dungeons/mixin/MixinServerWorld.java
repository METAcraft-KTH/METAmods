package se.datasektionen.mc.metacraft_dungeons.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_dungeons.dungeons.DungeonData;
import se.datasektionen.mc.metacraft_dungeons.extensions.ServerEntityManagerExtension;
import se.datasektionen.mc.metacraft_dungeons.extensions.ServerWorldExtension;

@Mixin(ServerWorld.class)
public class MixinServerWorld implements ServerWorldExtension {

	@Shadow @Final private ServerEntityManager<Entity> entityManager;
	@Unique
	private boolean isBeingDeleted = false;

	@Inject(method = "addPlayer", at = @At("HEAD"), cancellable = true)
	private void addPlayer(ServerPlayerEntity player, CallbackInfo ci) {
		DungeonData.getIfPresent((ServerWorld) (Object) this).ifPresent(data -> {
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
