package nu.metacraft.core.block.blocks;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.core.block.METAcraftBlockEntities;
import nu.metacraft.core.block.entities.BlackHolePortalEntity;

public class BlackHolePortalCore extends PortalCore {

	public static final MapCodec<BlackHolePortalCore> CODEC = simpleCodec(BlackHolePortalCore::new);

	public BlackHolePortalCore(Properties settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BlackHolePortalEntity(pos, state);
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext ctx) {
		return Blocks.END_GATEWAY.defaultBlockState();
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
		return createTickerHelper(type, METAcraftBlockEntities.BLACK_HOLE, BlackHolePortalEntity::tick);
	}
}
