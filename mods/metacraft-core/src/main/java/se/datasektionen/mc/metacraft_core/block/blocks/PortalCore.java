package se.datasektionen.mc.metacraft_core.block.blocks;

import com.mojang.serialization.MapCodec;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.metacraft_core.block.entities.PortalEntity;

public class PortalCore extends BlockWithEntity implements PolymerBlock {

	public static final MapCodec<PortalCore> CODEC = createCodec(PortalCore::new);

	public PortalCore(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new PortalEntity(pos, state);
	}

	@Override
	public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
		if (world.getBlockEntity(pos) instanceof PortalEntity portal) {
			portal.onCollision(state, world, pos, entity);
		}
	}

	@Override
	public void onPolymerBlockSend(BlockState blockState, BlockPos.Mutable pos, ServerPlayerEntity player) {
		PortalPadding.sendDummyEndGateway(pos, player);
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state) {
		return Blocks.END_GATEWAY.getDefaultState();
	}
}
