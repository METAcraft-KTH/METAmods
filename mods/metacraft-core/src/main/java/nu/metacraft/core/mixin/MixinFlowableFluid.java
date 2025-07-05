package nu.metacraft.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FlowableFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.core.block.METAcraftBlocks;
import nu.metacraft.core.block.blocks.PortalCore;
import nu.metacraft.core.block.blocks.PortalPadding;

@Mixin(FlowableFluid.class)
public class MixinFlowableFluid {

	@ModifyReturnValue(
		method = "canFill(Lnet/minecraft/block/BlockState;)Z",
		at = @At("RETURN")
	)
	private static boolean canFill(boolean original, BlockState state) {
		if (state.getBlock() instanceof PortalCore || state.getBlock() instanceof PortalPadding || state.isOf(METAcraftBlocks.MUSIC_PLAYER)) {
			return false;
		}
		return original;
	}

}
