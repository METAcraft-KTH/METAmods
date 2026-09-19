package nu.metacraft.rivals.paint;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import nu.metacraft.rivals.PaintColor;

import java.util.EnumMap;
import java.util.Map;

/**
 * Paint on one face of a cell, with the four in-plane connection bits the client turns into a
 * continuous sheet (spec §3). {@link #FACE} is the attach direction: floor paint attaches DOWN. The
 * bits are recomputed from the neighbours whenever one changes, through {@link #updateShape}, so a
 * cell whose neighbour is painted or wiped re-borders itself without anyone telling it to.
 *
 * <p>One face only: a cell that takes paint on a second face becomes the {@link PaintBlock} splat
 * fallback instead, which has the six face flags but no bits.
 */
public final class ConnectedPaintBlock extends Block implements Paint, PolymerBlock {
	public static final EnumProperty<Direction> FACE = EnumProperty.create("face", Direction.class);
	public static final BooleanProperty NEG_U = BooleanProperty.create("neg_u");
	public static final BooleanProperty POS_U = BooleanProperty.create("pos_u");
	public static final BooleanProperty NEG_V = BooleanProperty.create("neg_v");
	public static final BooleanProperty POS_V = BooleanProperty.create("pos_v");
	private static final BooleanProperty[] BITS = {NEG_U, POS_U, NEG_V, POS_V};
	/**
	 * The paper-thin slab against each face — the shape the multiface donors carry — so rays and
	 * {@link Painter#paintable} see a face rather than a cube. Vanilla keeps its own copy behind a
	 * private field on {@code MultifaceBlock} in 26.3, so paint builds the six boxes itself.
	 */
	private static final Map<Direction, VoxelShape> SHAPES = shapes();

	private final PaintColor color;

	public ConnectedPaintBlock(Properties properties, PaintColor color) {
		super(properties);
		this.color = color;
		registerDefaultState(stateDefinition.any().setValue(FACE, Direction.DOWN)
				.setValue(NEG_U, false).setValue(POS_U, false).setValue(NEG_V, false).setValue(POS_V, false));
	}

	private static Map<Direction, VoxelShape> shapes() {
		Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
		shapes.put(Direction.DOWN, Block.box(0, 0, 0, 16, 1, 16));
		shapes.put(Direction.UP, Block.box(0, 15, 0, 16, 16, 16));
		shapes.put(Direction.NORTH, Block.box(0, 0, 0, 16, 16, 1));
		shapes.put(Direction.SOUTH, Block.box(0, 0, 15, 16, 16, 16));
		shapes.put(Direction.WEST, Block.box(0, 0, 0, 1, 16, 16));
		shapes.put(Direction.EAST, Block.box(15, 0, 0, 16, 16, 16));
		return Map.copyOf(shapes);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACE, NEG_U, POS_U, NEG_V, POS_V);
	}

	@Override
	public PaintColor color() {
		return color;
	}

	@Override
	public int faceMask(BlockState state) {
		return 1 << state.getValue(FACE).ordinal();
	}

	public static int bits(BlockState state) {
		int bits = 0;
		for (int i = 0; i < 4; i++) {
			if (state.getValue(BITS[i])) bits |= 1 << i;
		}
		return bits;
	}

	public static BlockState withBits(BlockState state, int bits) {
		for (int i = 0; i < 4; i++) state = state.setValue(BITS[i], (bits >> i & 1) != 0);
		return state;
	}

	/**
	 * The in-plane neighbours of a face, in bit order NEG_U, POS_U, NEG_V, POS_V (spec §3): normal
	 * Y → u = x, v = z; normal X → u = z, v = y; normal Z → u = x, v = y.
	 */
	public static Direction[] inPlane(Direction face) {
		return switch (face.getAxis()) {
			case Y -> new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};
			case X -> new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.DOWN, Direction.UP};
			case Z -> new Direction[]{Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP};
		};
	}

	/**
	 * Bits for {@code cell} holding {@code color} on {@code face}, from what its four in-plane neighbours
	 * hold now. A neighbour counts if it is a paint block of the same colour carrying the same face — or,
	 * on a server level, if it is a cell of {@link PaintDisplays} quads of the same colour on the same
	 * face. Quads are not blocks and a block's neighbour test would not see them otherwise, which left a
	 * one-sided seam wherever block paint met a slab or a stair: the quads opened their border towards the
	 * block and the block kept its own closed. {@link PaintDisplays#faceAt} records the face of the
	 * <em>surface</em> the quads cover, which is the opposite of the attach direction a cell carries.
	 */
	public static int neighbourBits(BlockGetter level, BlockPos cell, Direction face, PaintColor color) {
		Direction[] around = inPlane(face);
		PaintDisplays quads = level instanceof ServerLevel server ? PaintDisplays.of(server) : null;
		int bits = 0;
		for (int i = 0; i < 4; i++) {
			BlockPos at = cell.relative(around[i]);
			BlockState other = level.getBlockState(at);
			if (other.getBlock() instanceof Paint paint && paint.color() == color && (paint.faceMask(other) & 1 << face.ordinal()) != 0) {
				bits |= 1 << i;
			} else if (quads != null && quads.colorAt(at) == color && quads.faceAt(at) == face.getOpposite()) {
				bits |= 1 << i;
			}
		}
		return bits;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACE));
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Direction face = state.getValue(FACE);
		BlockPos support = pos.relative(face);
		return Block.isFaceFull(level.getBlockState(support).getCollisionShape(level, support), face.getOpposite());
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		if (!canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();
		return withBits(state, neighbourBits(level, pos, state.getValue(FACE), color));
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		return PaintStates.connected(color, state.getValue(FACE), bits(state));
	}
}
