package nu.metacraft.portal_blocker.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.portal.TeleportTransition;
import nu.metacraft.portal_blocker.PortalBlockerSettings;
import nu.metacraft.portal_blocker.PortalState;
import nu.metacraft.portal_blocker.portal_type.PortalTypeRegistry;

@Mixin(PortalProcessor.class)
public class MixinPortalManager {

	@Shadow private BlockPos entryPosition;

	@Final
	@Shadow private Portal portal;

	@Inject(
		method = "processPortalTeleportation",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/world/entity/PortalProcessor;portalTime:I",
			ordinal = 0
		),
		cancellable = true
	)
	public void tick(ServerLevel world, Entity entity, boolean canUsePortals, CallbackInfoReturnable<Boolean> cir) {
		for (var type : PortalTypeRegistry.REGISTRY) {
			if (type.affectsPortal(portal) && PortalBlockerSettings.getInstance(world.getServer()).isPortalBlocked(
					type, world.dimension(), PortalState.BlockingType.TRAVEL, entryPosition)
			) {
				if (entity instanceof ServerPlayer player) {
					type.getTravelMessage().ifPresent(msg -> {
						player.displayClientMessage(msg, true);
					});
				}
				cir.setReturnValue(false);
			}
		}
	}

	//Technically not necessary, but does well as a last resort.
	@Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
	public void createTeleportTarget(ServerLevel world, Entity entity, CallbackInfoReturnable<TeleportTransition> cir) {
		for (var type : PortalTypeRegistry.REGISTRY) {
			if (type.affectsPortal(portal) && PortalBlockerSettings.getInstance(world.getServer()).isPortalBlocked(
					type, world.dimension(), PortalState.BlockingType.TRAVEL, entryPosition)
			) {
				cir.setReturnValue(null);
			}
		}
	}

}
