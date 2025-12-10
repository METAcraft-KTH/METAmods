package se.metacraft.portalopening.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.metacraft.portalopening.PortalOpeningDimensionData;
import se.metacraft.portalopening.WorldData;

@Mixin(Level.class)
public abstract class LevelMixin implements WorldData {

	@Shadow public abstract boolean setBlock(BlockPos pos, BlockState state, int flags);

	@Unique
	private boolean breakRifts = true;

	@Inject(
		method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;getChunkAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/chunk/LevelChunk;"
		)
	)
	public void onSetBlockState(
			BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
			CallbackInfoReturnable<Boolean> cir
	) {
		if ((Object) this instanceof ServerLevel world && breakRifts) {
			PortalOpeningDimensionData.getInstance(world).removeRiftAt(pos);
		}
	}

	@Override
	public void portalOpening$setBlockNoTrigger(BlockPos pos, BlockState state) {
		breakRifts = false;
		this.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		breakRifts = true;
	}
}
