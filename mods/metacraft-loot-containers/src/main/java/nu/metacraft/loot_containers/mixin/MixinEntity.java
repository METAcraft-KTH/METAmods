package nu.metacraft.loot_containers.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.loot_containers.containers.LootContainerData;

import java.util.UUID;

@Mixin(Entity.class)
public abstract class MixinEntity {

	@Shadow public abstract World getEntityWorld();

	@Shadow public abstract UUID getUuid();

	@Shadow private World world;

	@Inject(
		method = "teleportCrossDimension",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/Entity;removeFromDimension()V"
		)
	)
	public void teleportTo(
			ServerWorld from, ServerWorld target,
			TeleportTarget teleportTarget, CallbackInfoReturnable<Entity> cir
	) {
		if (target.isClient()) return;
		var data = LootContainerData.getInstance(getEntityWorld().getServer());
		data.getLootContainers(getEntityWorld().getRegistryKey(), getUuid()).forEach(container -> {
			data.putLootContainer(container.getFirst(), target.getRegistryKey(), getUuid(), container.getSecond());
		});
	}

	@Inject(method = "setRemoved", at = @At("HEAD"))
	public void setRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
		if (world.isClient()) return;
		if (reason.shouldDestroy() || reason == Entity.RemovalReason.CHANGED_DIMENSION) {
			LootContainerData.getInstance(getEntityWorld().getServer()).removeLootContainers(
					getEntityWorld().getRegistryKey(), getUuid()
			);
		}
	}

}
