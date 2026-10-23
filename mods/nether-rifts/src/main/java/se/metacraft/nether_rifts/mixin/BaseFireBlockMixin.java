package se.metacraft.nether_rifts.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.portal.PortalShape;
import se.metacraft.nether_rifts.rift.RiftForming;
import se.metacraft.nether_rifts.rift.RiftShape;
import se.metacraft.portal_blocker.PortalBlockerSettings;
import se.metacraft.portal_blocker.PortalState;
import se.metacraft.portal_blocker.portal_type.PortalTypeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;

/**
 * Fire lit inside a nether frame: where portal-blocker would refuse the portal, in rift mode a rift takes its
 * place. Wrapping the call (rather than cancelling inside PortalShape) runs before portal-blocker's own
 * PortalShapeMixin ever sees it; portal-blocker's BaseFireBlockMixin still puts the fire out, which is fine.
 */
@Mixin(BaseFireBlock.class)
public abstract class BaseFireBlockMixin {

	@WrapOperation(
			method = "onPlace",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/portal/PortalShape;createPortalBlocks(Lnet/minecraft/world/level/LevelAccessor;)V")
	)
	private void netherRifts$riftInstead(PortalShape shape, LevelAccessor accessor, Operation<Void> original, @Local(argsOnly = true) Level level) {
		if (level instanceof ServerLevel serverLevel) {
			var s = (PortalShapeAccessor) shape;
			var positions = new ArrayList<BlockPos>();
			var bottomLeft = s.netherRifts$bottomLeft();
			for (var pos : BlockPos.betweenClosed(bottomLeft, bottomLeft.relative(Direction.UP, s.netherRifts$height() - 1).relative(s.netherRifts$rightDir(), s.netherRifts$width() - 1))) {
				positions.add(pos.immutable());
			}
			boolean blocked = PortalBlockerSettings.getInstance(serverLevel.getServer()).isPortalBlocked(
					PortalTypeRegistry.NETHER, serverLevel.dimension(), PortalState.BlockingType.ACTIVATION, positions
			);
			if (RiftForming.tryForm(serverLevel, positions, s.netherRifts$axis(), RiftShape.FrameTest.of(PortalShape.FRAME), blocked)) {
				return;
			}
		}
		original.call(shape, accessor);
	}
}
