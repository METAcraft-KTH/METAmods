package se.datasektionen.mc.metacraft_dungeons.block.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.metacraft_dungeons.block.block_entities.BlackHolePortalEntity;
import se.datasektionen.mc.metacraft_dungeons.block.DungeonsBlockEntities;

public class BlackHolePortal extends PortalWithTarget {

	public static final MapCodec<BlackHolePortal> CODEC = createCodec(BlackHolePortal::new);

	public BlackHolePortal(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new BlackHolePortalEntity(pos, state);
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state) {
		return Blocks.END_GATEWAY.getDefaultState();
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return validateTicker(type, DungeonsBlockEntities.BLACK_HOLE, BlackHolePortalEntity::tick);
	}
}
