package nu.metacraft.core.block.blocks;

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
import nu.metacraft.core.block.METAcraftBlockEntities;
import nu.metacraft.core.block.entities.BlackHolePortalEntity;
import xyz.nucleoid.packettweaker.PacketContext;

public class BlackHolePortalCore extends PortalCore {

	public static final MapCodec<BlackHolePortalCore> CODEC = createCodec(BlackHolePortalCore::new);

	public BlackHolePortalCore(Settings settings) {
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
	public BlockState getPolymerBlockState(BlockState state, PacketContext ctx) {
		return Blocks.END_GATEWAY.getDefaultState();
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return validateTicker(type, METAcraftBlockEntities.BLACK_HOLE, BlackHolePortalEntity::tick);
	}
}
