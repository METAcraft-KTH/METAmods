package se.metacraft.portalopening.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.metacraft.portalopening.PortalOpeningDimensionData;
import se.metacraft.portalopening.WorldData;

@Mixin(World.class)
public abstract class MixinWorld implements WorldData {

	@Shadow public abstract boolean setBlockState(BlockPos pos, BlockState state, int flags);

	@Unique
	private boolean breakRifts = true;

	@Inject(
		method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/World;getWorldChunk(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/world/chunk/WorldChunk;"
		)
	)
	public void onSetBlockState(
			BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
			CallbackInfoReturnable<Boolean> cir
	) {
		if ((Object) this instanceof ServerWorld world && breakRifts) {
			PortalOpeningDimensionData.getInstance(world).removeRiftAt(pos);
		}
	}

	@Override
	public void portalOpening$setBlockNoTrigger(BlockPos pos, BlockState state) {
		breakRifts = false;
		this.setBlockState(pos, state, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
		breakRifts = true;
	}
}
