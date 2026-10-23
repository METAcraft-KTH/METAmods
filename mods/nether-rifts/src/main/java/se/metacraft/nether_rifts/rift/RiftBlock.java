package se.metacraft.nether_rifts.rift;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Marks a rift's positions on the server. Clients see a nether portal turned the rift's way, so they draw its
 * swirl, particles and hum themselves (the rift's displays crack it over); on the server it stays a rift and
 * never teleports anyone. Its shape and light are the portal's, so clients predict it right.
 */
public class RiftBlock extends Block implements PolymerBlock {

	public static final EnumProperty<Direction.Axis> AXIS = NetherPortalBlock.AXIS;

	public RiftBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return portal(state).getShape(level, pos, context);
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		return portal(state);
	}

	private static BlockState portal(BlockState state) {
		return Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, state.getValue(AXIS));
	}
}
