package nu.metacraft.core.block.blocks;

import nu.metacraft.core.block.entities.BlockEntityWithDisguise;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

public interface BlockWithDisguise {

	default Optional<? extends BlockEntityWithDisguise> getBlockEntity(BlockGetter world, BlockPos pos) {
		var entity = world.getBlockEntity(pos);
		if (entity instanceof BlockEntityWithDisguise disguised) {
			return Optional.of(disguised);
		}
		return Optional.empty();
	}

}
