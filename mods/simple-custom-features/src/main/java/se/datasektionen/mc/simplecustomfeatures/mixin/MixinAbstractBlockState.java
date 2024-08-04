package se.datasektionen.mc.simplecustomfeatures.mixin;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.simplecustomfeatures.objects.blocks.dynamic_portal.PortalBlockObject;
import se.datasektionen.mc.simplecustomfeatures.objects.blocks.dynamic_portal.PortalShape;

@Mixin(AbstractBlock.AbstractBlockState.class)
public class MixinAbstractBlockState {

	@Inject(method = "onBlockAdded", at = @At("HEAD"))
	public void onBlockAdded(World world, BlockPos pos, BlockState oldState, boolean notify, CallbackInfo ci) {
		if (world instanceof ServerWorld sw && (Object) this instanceof BlockState state) {
			PortalBlockObject.getForBlock(pos, sw, state).flatMap(
					portal -> portal.findPortalShape(sw, pos)
			).ifPresent(PortalShape::activate);
		}
	}

}
