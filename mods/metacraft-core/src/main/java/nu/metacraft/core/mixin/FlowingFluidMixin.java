package nu.metacraft.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.core.block.METAcraftBlocks;
import nu.metacraft.core.block.blocks.PortalCore;
import nu.metacraft.core.block.blocks.PortalPadding;

@Mixin(FlowingFluid.class)
public class FlowingFluidMixin {

	@ModifyReturnValue(
		method = "canHoldAnyFluid(Lnet/minecraft/world/level/block/state/BlockState;)Z",
		at = @At("RETURN")
	)
	private static boolean canFill(boolean original, BlockState state) {
		if (state.getBlock() instanceof PortalCore || state.getBlock() instanceof PortalPadding || state.is(METAcraftBlocks.MUSIC_PLAYER)) {
			return false;
		}
		return original;
	}

}
