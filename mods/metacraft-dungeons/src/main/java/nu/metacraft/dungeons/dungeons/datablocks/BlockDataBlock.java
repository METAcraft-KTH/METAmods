package nu.metacraft.dungeons.dungeons.datablocks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.SimpleStateProvider;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

public class BlockDataBlock extends DataBlock {

	public static final MapCodec<BlockDataBlock> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
			Codec.withAlternative(
					BlockStateProvider.CODEC,
					BlockState.CODEC, SimpleStateProvider::simple
			).fieldOf("block").forGetter(b -> b.block)
		).apply(instance, BlockDataBlock::new)
	);

	protected final BlockStateProvider block;

	public BlockDataBlock(
			BlockStateProvider block
	) {
		this.block = block;
	}

	@Override
	public DataBlockRegistry.DataBlockType<?> getType() {
		return DataBlockRegistry.BLOCK;
	}

	@Override
	public void processDataBlock(BlockPos pos, StructurePiece piece) {
		parameters.dungeons.setBlockAndUpdate(pos, block.getState(parameters.dungeons.getRandom(), pos));
	}
}
