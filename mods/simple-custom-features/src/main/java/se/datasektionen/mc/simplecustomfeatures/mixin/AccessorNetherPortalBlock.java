package se.datasektionen.mc.simplecustomfeatures.mixin;

import net.minecraft.block.NetherPortalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockLocating;
import net.minecraft.world.TeleportTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NetherPortalBlock.class)
public interface AccessorNetherPortalBlock {

	@Invoker
	static TeleportTarget callGetExitPortalTarget(
			Entity entity, BlockPos pos, BlockLocating.Rectangle exitPortalRectangle,
			ServerWorld world, TeleportTarget.PostDimensionTransition postDimensionTransition
	) {
		throw new IllegalStateException("Mixin Error");
	}

}
