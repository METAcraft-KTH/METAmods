package nu.metacraft.portal_blocker.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.portal_blocker.PortalBlocker;
import nu.metacraft.portal_blocker.PortalBlockerSettings;
import nu.metacraft.portal_blocker.PortalState;
import nu.metacraft.portal_blocker.portal_type.PortalTypeRegistry;

import java.util.Optional;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.portal.PortalForcer;

@Mixin(PortalForcer.class)
public class PortalForcerMixin {

	@Shadow @Final private ServerLevel level;

	@Inject(
		method = "createPortal",
		at = {
			@At(
				value = "INVOKE",
				target = "Lnet/minecraft/world/level/border/WorldBorder;clampToBounds(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;"
			)
		},
		cancellable = true
	)
	private void blockInvalidPortal(
			BlockPos pos, Direction.Axis axis, CallbackInfoReturnable<Optional<BlockUtil.FoundRectangle>> cir,
			@Local(ordinal = 0, argsOnly = true) BlockPos blockPos, @Local(ordinal = 0) BlockPos.MutableBlockPos mutable,
			@Local(ordinal = 0) Direction direction
	) {
		if (PortalBlockerSettings.getInstance(level.getServer()).isPortalBlocked(
			PortalTypeRegistry.NETHER, level.dimension(), PortalState.BlockingType.GENERATION, BlockPos.betweenClosed(
				mutable.setWithOffset(blockPos, -1 * direction.getStepX(), -1, -1 * direction.getStepZ()),
				mutable.setWithOffset(blockPos, 2 * direction.getStepX(), 3, 2 * direction.getStepZ())
			)
		)) {
			PortalBlocker.LOGGER.info("Portal generation blocked.");
			cir.setReturnValue(Optional.empty());
		}
	}

	@Inject(method = "canHostFrame", at = @At("HEAD"), cancellable = true)
	private void isValidPortalPos(
			BlockPos pos, BlockPos.MutableBlockPos temp, Direction portalDirection,
			int distanceOrthogonalToPortal, CallbackInfoReturnable<Boolean> cir
	) {
		Direction direction = portalDirection.getClockWise();
		if (PortalBlockerSettings.getInstance(level.getServer()).isPortalBlocked(
			PortalTypeRegistry.NETHER, level.dimension(), PortalState.BlockingType.GENERATION, BlockPos.betweenClosed(
				temp.setWithOffset(
					pos,
					portalDirection.getStepX() * -1 + direction.getStepX() * distanceOrthogonalToPortal,
					-1,
					portalDirection.getStepZ() * -1 + direction.getStepZ() * distanceOrthogonalToPortal
				),
				temp.setWithOffset(
					pos,
					portalDirection.getStepX() * 2 + direction.getStepX() * distanceOrthogonalToPortal,
					3,
					portalDirection.getStepZ() * 2 + direction.getStepZ() * distanceOrthogonalToPortal
				)
			)
		)) {
			cir.setReturnValue(false);
			cir.cancel();
		}
	}

}
