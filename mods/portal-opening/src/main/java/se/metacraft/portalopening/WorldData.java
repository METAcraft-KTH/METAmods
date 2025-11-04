package se.metacraft.portalopening;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public interface WorldData {

	void portalOpening$setBlockNoTrigger(BlockPos pos, BlockState state);

}
