package se.datasektionen.mc.metacraft_dungeons.dungeons.datablocks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.stateprovider.BlockStateProvider;
import net.minecraft.world.gen.stateprovider.SimpleBlockStateProvider;

public class BlockDataBlock extends DataBlock {

	public static final MapCodec<BlockDataBlock> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
			Codec.withAlternative(
					BlockStateProvider.TYPE_CODEC,
					BlockState.CODEC, SimpleBlockStateProvider::of
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
		parameters.dungeons.setBlockState(pos, block.get(parameters.dungeons.getRandom(), pos));
	}
}
