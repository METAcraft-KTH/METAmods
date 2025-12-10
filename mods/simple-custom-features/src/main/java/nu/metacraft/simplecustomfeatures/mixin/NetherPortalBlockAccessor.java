package nu.metacraft.simplecustomfeatures.mixin;

import net.minecraft.util.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NetherPortalBlock.class)
public interface NetherPortalBlockAccessor {

	@Invoker
	static TeleportTransition callGetDimensionTransitionFromExit(
			Entity entity, BlockPos pos, BlockUtil.FoundRectangle exitPortalRectangle,
			ServerLevel world, TeleportTransition.PostTeleportTransition postDimensionTransition
	) {
		throw new IllegalStateException("Mixin Error");
	}

}
