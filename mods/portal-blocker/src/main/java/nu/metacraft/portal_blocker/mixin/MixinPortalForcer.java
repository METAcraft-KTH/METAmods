package nu.metacraft.portal_blocker.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockLocating;
import net.minecraft.world.dimension.PortalForcer;
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

@Mixin(PortalForcer.class)
public class MixinPortalForcer {

	@Shadow @Final private ServerWorld world;

	@Inject(
		method = "createPortal",
		at = {
			@At(
				value = "INVOKE",
				target = "Lnet/minecraft/world/border/WorldBorder;clampFloored(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/math/BlockPos;"
			)
		},
		cancellable = true
	)
	private void blockInvalidPortal(
			BlockPos pos, Direction.Axis axis, CallbackInfoReturnable<Optional<BlockLocating.Rectangle>> cir,
			@Local(ordinal = 0, argsOnly = true) BlockPos blockPos, @Local(ordinal = 0) BlockPos.Mutable mutable,
			@Local(ordinal = 0) Direction direction
	) {
		if (PortalBlockerSettings.getInstance(world.getServer()).isPortalBlocked(
			PortalTypeRegistry.NETHER, world.getRegistryKey(), PortalState.BlockingType.GENERATION, BlockPos.iterate(
				mutable.set(blockPos, -1 * direction.getOffsetX(), -1, -1 * direction.getOffsetZ()),
				mutable.set(blockPos, 2 * direction.getOffsetX(), 3, 2 * direction.getOffsetZ())
			)
		)) {
			PortalBlocker.LOGGER.info("Portal generation blocked.");
			cir.setReturnValue(Optional.empty());
		}
	}

	@Inject(method = "isValidPortalPos", at = @At("HEAD"), cancellable = true)
	private void isValidPortalPos(
			BlockPos pos, BlockPos.Mutable temp, Direction portalDirection,
			int distanceOrthogonalToPortal, CallbackInfoReturnable<Boolean> cir
	) {
		Direction direction = portalDirection.rotateYClockwise();
		if (PortalBlockerSettings.getInstance(world.getServer()).isPortalBlocked(
			PortalTypeRegistry.NETHER, world.getRegistryKey(), PortalState.BlockingType.GENERATION, BlockPos.iterate(
				temp.set(
					pos,
					portalDirection.getOffsetX() * -1 + direction.getOffsetX() * distanceOrthogonalToPortal,
					-1,
					portalDirection.getOffsetZ() * -1 + direction.getOffsetZ() * distanceOrthogonalToPortal
				),
				temp.set(
					pos,
					portalDirection.getOffsetX() * 2 + direction.getOffsetX() * distanceOrthogonalToPortal,
					3,
					portalDirection.getOffsetZ() * 2 + direction.getOffsetZ() * distanceOrthogonalToPortal
				)
			)
		)) {
			cir.setReturnValue(false);
			cir.cancel();
		}
	}

}
