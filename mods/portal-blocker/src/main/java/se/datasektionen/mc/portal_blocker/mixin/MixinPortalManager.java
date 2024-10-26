package se.datasektionen.mc.portal_blocker.mixin;

import net.minecraft.block.Portal;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.dimension.PortalManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.portal_blocker.PortalBlockerSettings;
import se.datasektionen.mc.portal_blocker.PortalState;
import se.datasektionen.mc.portal_blocker.portal_type.PortalTypeRegistry;

@Mixin(PortalManager.class)
public class MixinPortalManager {

	@Shadow private BlockPos pos;

	@Final
	@Shadow private Portal portal;

	@Inject(
		method = "tick",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/world/dimension/PortalManager;ticksInPortal:I",
			ordinal = 0
		),
		cancellable = true
	)
	public void tick(ServerWorld world, Entity entity, boolean canUsePortals, CallbackInfoReturnable<Boolean> cir) {
		for (var type : PortalTypeRegistry.REGISTRY) {
			if (type.affectsPortal(portal) && PortalBlockerSettings.getInstance(world.getServer()).isPortalBlocked(
					type, world.getRegistryKey(), PortalState.BlockingType.TRAVEL, pos)
			) {
				if (entity instanceof ServerPlayerEntity player) {
					type.getTravelMessage().ifPresent(msg -> {
						player.sendMessage(msg, true);
					});
				}
				cir.setReturnValue(false);
			}
		}
	}

	//Technically not necessary, but does well as a last resort.
	@Inject(method = "createTeleportTarget", at = @At("HEAD"), cancellable = true)
	public void createTeleportTarget(ServerWorld world, Entity entity, CallbackInfoReturnable<TeleportTarget> cir) {
		for (var type : PortalTypeRegistry.REGISTRY) {
			if (type.affectsPortal(portal) && PortalBlockerSettings.getInstance(world.getServer()).isPortalBlocked(
					type, world.getRegistryKey(), PortalState.BlockingType.TRAVEL, pos)
			) {
				cir.setReturnValue(null);
			}
		}
	}

}
