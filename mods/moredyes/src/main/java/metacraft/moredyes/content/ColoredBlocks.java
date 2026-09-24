package metacraft.moredyes.content;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import metacraft.moredyes.MoreDyes;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.GlazedTerracottaBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The opaque block families (Phase 2). Each is a real vanilla block subclass for behaviour, plus a
 * Polymer textured block for looks: one FULL_BLOCK donor state per server state, carrying the model
 * {@code moredyes:block/<color>_<family>} from the generated resource pack.
 */
public final class ColoredBlocks {
	private ColoredBlocks() {}

	static Identifier model(Identifier blockId) {
		return Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID, "block/" + blockId.getPath());
	}

	/** Wool, concrete, terracotta: a plain block with a single donor state. */
	public static final class Simple extends Block implements PolymerTexturedBlock {
		private final BlockState client;

		public Simple(Properties properties, Identifier id) {
			super(properties);
			this.client = ClientStates.request(id.toString(), BlockModelType.FULL_BLOCK,
					PolymerBlockModel.of(model(id)));
		}

		@Override
		public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
			return client;
		}
	}

	/** Concrete powder: vanilla gravity and water-hardening, producing OUR concrete. */
	public static final class Powder extends ConcretePowderBlock implements PolymerTexturedBlock {
		private final BlockState client;

		public Powder(Block concrete, Properties properties, Identifier id) {
			super(concrete, properties);
			this.client = ClientStates.request(id.toString(), BlockModelType.FULL_BLOCK,
					PolymerBlockModel.of(model(id)));
		}

		@Override
		public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
			return client;
		}
	}

	/**
	 * Carpet on a flat tripwire donor (Polymer's TRIPWIRE_FLAT pool), which has no collision on the
	 * client. A server-side 1/16 collision would make every step onto the carpet a "moved wrongly"
	 * correction, so our carpet has no collision either: the outline stays 1/16 for targeting, and
	 * entities stand one pixel "inside" it, which is invisible in practice.
	 */
	public static final class Carpet extends CarpetBlock implements PolymerTexturedBlock {
		private final BlockState client;

		public Carpet(Properties properties, Identifier id) {
			super(properties);
			this.client = ClientStates.request(id.toString(), BlockModelType.TRIPWIRE_FLAT,
					PolymerBlockModel.of(model(id)));
		}

		@Override
		public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
			return client;
		}

		@Override
		protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return Shapes.empty();
		}
	}

	/**
	 * Candles: the client gets an invisible lantern-shaped donor (lantern collision, and lanterns have
	 * no occlusion, so the block underneath keeps its top face) plus one item display per candle
	 * carrying the real model. Visible pools were wrong here: sculk sensors occlude the face below,
	 * which showed as a hole into the void when looking down at a candle.
	 *
	 * Vanilla candles draw their flame particles client-side from their own block; a donor won't, so
	 * lit candles emit them from the server on a short scheduled-tick loop. Light: the donor emits
	 * none, so Polymer is asked to push server light updates for lit states.
	 */
	public static final class Candle extends CandleBlock implements PolymerTexturedBlock, BlockWithElementHolder, ShapedBlocks.DisplayProvider {
		private static final String[] NAMES = {"one_candle", "two_candles", "three_candles", "four_candles"};
		private final Identifier id;
		private final BlockState client, clientWaterlogged;

		public Candle(Properties properties, Identifier id) {
			super(properties);
			this.id = id;
			this.client = ClientStates.requestEmpty(id.toString(), BlockModelType.LANTERN);
			this.clientWaterlogged = ClientStates.requestEmpty(id + "[waterlogged]", BlockModelType.LANTERN_WATERLOGGED);
		}

		@Override
		public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
			return state.getValue(WATERLOGGED) ? clientWaterlogged : client;
		}

		@Override
		public BlockState getPolymerBreakEventBlockState(BlockState state, @Nullable PacketContext context) {
			// Break particles come from a client block; the closest vanilla one is the white candle.
			return Blocks.DYED_CANDLE.white().defaultBlockState();
		}

		@Override
		public @Nullable ItemStack displayStack(BlockState state) {
			String model = id.getPath() + "_" + NAMES[state.getValue(CANDLES) - 1] + (state.getValue(LIT) ? "_lit" : "");
			return ShapedBlocks.displayStack(Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID, model));
		}

		@Override
		public Quaternionf displayRotation(BlockState state) {
			return ShapedBlocks.NO_ROTATION;
		}

		@Override
		public @Nullable ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState initialBlockState) {
			return new ShapedBlocks.DisplayHolder(this, initialBlockState);
		}

		@Override
		public boolean forceLightUpdates(BlockState state) {
			return state.getValue(LIT);
		}

		@Override
		protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
			super.onPlace(state, level, pos, oldState, movedByPiston);
			Flames.onPlace(this, state, level, pos);
		}

		@Override
		protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
			Flames.rearm(this, state, level, pos);
		}

		@Override
		protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
			Flames.tick(this, state, level, pos, random, getParticleOffsets(state));
		}
	}

	/**
	 * Server-driven candle flames. Vanilla candles draw their particles client-side from their own
	 * block; a donor state won't, so lit candles (and candle cakes) emit them from the server on a
	 * short scheduled-tick loop.
	 */
	static final class Flames {
		private static final int PERIOD = 4;

		private Flames() {}

		static void onPlace(Block block, BlockState state, Level level, BlockPos pos) {
			if (state.getValue(CandleBlock.LIT) && !level.isClientSide()) {
				level.scheduleTick(pos, block, PERIOD);
			}
		}

		/** From randomTick: re-arm the loop for candles that were lit when their chunk was saved. */
		static void rearm(Block block, BlockState state, ServerLevel level, BlockPos pos) {
			if (state.getValue(CandleBlock.LIT) && !level.getBlockTicks().hasScheduledTick(pos, block)) {
				level.scheduleTick(pos, block, PERIOD);
			}
		}

		static void tick(Block block, BlockState state, ServerLevel level, BlockPos pos, RandomSource random, Iterable<Vec3> offsets) {
			if (!state.getValue(CandleBlock.LIT)) return;
			for (Vec3 offset : offsets) {
				double x = pos.getX() + offset.x, y = pos.getY() + offset.y, z = pos.getZ() + offset.z;
				if (random.nextFloat() < 0.3F) {
					level.sendParticles(ParticleTypes.SMOKE, x, y, z, 1, 0, 0, 0, 0);
				}
				level.sendParticles(ParticleTypes.SMALL_FLAME, x, y, z, 1, 0, 0, 0, 0);
			}
			level.scheduleTick(pos, block, PERIOD);
		}
	}

	/**
	 * A cake with one of our candles on it. Vanilla keys candle cakes by candle block (the
	 * {@code CandleCakeBlock} constructor registers the pairing), so once our candles are in the
	 * {@code #minecraft:candles} item tag, candle-on-cake resolves to this block with no extra code.
	 *
	 * The client sees a plain vanilla cake (correct shape and texture, no block entity) plus one
	 * item display of our single-candle model lifted onto the cake top. Eating it drops the candle
	 * through this block's loot table and leaves a bitten vanilla cake, as in vanilla.
	 */
	public static final class CandleCake extends CandleCakeBlock implements PolymerTexturedBlock, BlockWithElementHolder, ShapedBlocks.DisplayProvider {
		/** The candle model sits at y=0..6 in its own cell; on a cake it stands on the cake top (y=8). */
		private static final Vector3f ON_CAKE = new Vector3f(0, 0.5F, 0);
		private final Identifier candleId;

		public CandleCake(Candle candle, Properties properties, Identifier candleId) {
			super(candle, properties);
			this.candleId = candleId;
		}

		@Override
		public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
			return Blocks.CAKE.defaultBlockState();
		}

		@Override
		public @Nullable ItemStack displayStack(BlockState state) {
			String model = candleId.getPath() + "_one_candle" + (state.getValue(LIT) ? "_lit" : "");
			return ShapedBlocks.displayStack(Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID, model));
		}

		@Override
		public Quaternionf displayRotation(BlockState state) {
			return ShapedBlocks.NO_ROTATION;
		}

		@Override
		public Vector3f displayTranslation(BlockState state) {
			return ON_CAKE;
		}

		@Override
		public @Nullable ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState initialBlockState) {
			return new ShapedBlocks.DisplayHolder(this, initialBlockState);
		}

		@Override
		public boolean forceLightUpdates(BlockState state) {
			return state.getValue(LIT);
		}

		@Override
		protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
			super.onPlace(state, level, pos, oldState, movedByPiston);
			Flames.onPlace(this, state, level, pos);
		}

		@Override
		protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
			Flames.rearm(this, state, level, pos);
		}

		@Override
		protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
			Flames.tick(this, state, level, pos, random, getParticleOffsets(state));
		}
	}

	/** Glazed terracotta: four facings, each its own donor state with the model rotated. */
	public static final class Glazed extends GlazedTerracottaBlock implements PolymerTexturedBlock {
		private final Map<BlockState, BlockState> client = new IdentityHashMap<>();

		public Glazed(Properties properties, Identifier id) {
			super(properties);
			Identifier model = model(id);
			for (BlockState state : getStateDefinition().getPossibleStates()) {
				Direction facing = state.getValue(FACING);
				// Same y rotations vanilla's glazed terracotta blockstate files use.
				int y = switch (facing) {
					case SOUTH -> 0;
					case WEST -> 90;
					case NORTH -> 180;
					case EAST -> 270;
					default -> 0;
				};
				client.put(state, ClientStates.request(id + "[" + facing + "]", BlockModelType.FULL_BLOCK,
						PolymerBlockModel.of(model, 0, y)));
			}
		}

		@Override
		public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
			BlockState s = client.get(state);
			if (s == null) {
				MoreDyes.LOGGER.error("[{}] no client state for {}", MoreDyes.MOD_ID, state);
				return ClientStates.ERROR;
			}
			return s;
		}
	}
}
