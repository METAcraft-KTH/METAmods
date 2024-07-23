package se.datasektionen.mc.metacraft_dungeons.block.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.metacraft_dungeons.block.block_entities.DungeonEntranceEntity;

public class DungeonEntrance extends PortalWithTarget {

	public static final MapCodec<DungeonEntrance> CODEC = createCodec(DungeonEntrance::new);

	public DungeonEntrance(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new DungeonEntranceEntity(pos, state);
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state) {
		return Blocks.END_GATEWAY.getDefaultState();
	}
}
