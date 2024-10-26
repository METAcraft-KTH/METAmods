package se.datasektionen.mc.loot_containers.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.loot_containers.containers.LootContainerData;

import java.util.UUID;

@Mixin(Entity.class)
public abstract class MixinEntity {

	@Shadow public abstract World getWorld();

	@Shadow @Nullable public abstract MinecraftServer getServer();

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
			ServerWorld target, TeleportTarget teleportTarget,
			CallbackInfoReturnable<Entity> cir
	) {
		if (target.isClient()) return;
		var data = LootContainerData.getInstance(getServer());
		data.getLootContainers(getWorld().getRegistryKey(), getUuid()).forEach(container -> {
			data.putLootContainer(container.getFirst(), target.getRegistryKey(), getUuid(), container.getSecond());
		});
	}

	@Inject(method = "setRemoved", at = @At("HEAD"))
	public void setRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
		if (world.isClient()) return;
		if (reason.shouldDestroy() || reason == Entity.RemovalReason.CHANGED_DIMENSION) {
			LootContainerData.getInstance(getServer()).removeLootContainers(
					getWorld().getRegistryKey(), getUuid()
			);
		}
	}

}
