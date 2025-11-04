package nu.metacraft.simplecustomfeatures.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.portal.PortalForcer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PortalForcer.class)
public interface AccessorPortalForcer {

	@Invoker
	boolean callCanPortalReplaceBlock(BlockPos.MutableBlockPos pos);

}
