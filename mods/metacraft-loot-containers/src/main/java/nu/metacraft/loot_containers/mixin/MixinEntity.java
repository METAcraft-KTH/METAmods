package nu.metacraft.loot_containers.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.loot_containers.containers.LootContainerData;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;

@Mixin(Entity.class)
public abstract class MixinEntity {

	@Shadow public abstract Level level();

	@Shadow public abstract UUID getUUID();

	@Shadow private Level level;

	@Inject(
		method = "teleportCrossDimension",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;removeAfterChangingDimensions()V"
		)
	)
	public void teleportTo(
			ServerLevel from, ServerLevel target,
			TeleportTransition teleportTarget, CallbackInfoReturnable<Entity> cir
	) {
		if (target.isClientSide()) return;
		var data = LootContainerData.getInstance(level().getServer());
		data.getLootContainers(level().dimension(), getUUID()).forEach(container -> {
			data.putLootContainer(container.getFirst(), target.dimension(), getUUID(), container.getSecond());
		});
	}

	@Inject(method = "setRemoved", at = @At("HEAD"))
	public void setRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
		if (level.isClientSide()) return;
		if (reason.shouldDestroy() || reason == Entity.RemovalReason.CHANGED_DIMENSION) {
			LootContainerData.getInstance(level().getServer()).removeLootContainers(
					level().dimension(), getUUID()
			);
		}
	}

}
