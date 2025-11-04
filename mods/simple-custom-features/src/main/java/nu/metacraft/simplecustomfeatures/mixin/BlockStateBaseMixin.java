package nu.metacraft.simplecustomfeatures.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import nu.metacraft.simplecustomfeatures.objects.blocks.dynamic_portal.PortalBlockObject;

@Mixin(BlockBehaviour.BlockStateBase.class)
public class BlockStateBaseMixin {

	@Inject(method = "onPlace", at = @At("HEAD"))
	public void onBlockAdded(Level world, BlockPos pos, BlockState oldState, boolean notify, CallbackInfo ci) {
		if (world instanceof ServerLevel sw && (Object) this instanceof BlockState state) {
			PortalBlockObject.getForBlock(pos, sw, state).flatMap(
					portal -> portal.findPortalShape(sw, pos)
			).ifPresent(portal -> portal.activate(world));
		}
	}

}
