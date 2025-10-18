package se.metacraft.portalopening;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public interface WorldData {

	void portalOpening$setBlockNoTrigger(BlockPos pos, BlockState state);

}
