package nu.metacraft.core.block.blocks;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import nu.metacraft.core.block.entities.BlockEntityWithDisguise;

import java.util.Optional;

public interface BlockWithDisguise {

	default Optional<? extends BlockEntityWithDisguise> getBlockEntity(BlockView world, BlockPos pos) {
		var entity = world.getBlockEntity(pos);
		if (entity instanceof BlockEntityWithDisguise disguised) {
			return Optional.of(disguised);
		}
		return Optional.empty();
	}

}
