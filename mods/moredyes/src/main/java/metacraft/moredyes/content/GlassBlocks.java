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
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stained glass and panes (Phase 6). Since 26.x the client picks the render layer per sprite from
 * the texture's alpha (and vanilla's glass models pin {@code force_translucent}), not per block, so
 * our translucent glass textures render on the translucent chunk layer whatever donor carries them:
 * real alpha, sorted with the rest of the chunk, no entities.
 *
 * Donors: leaves for the block (non-occluding, so neighbours keep their faces; full collision) and
 * copper bars for the panes (same {@code CrossCollisionBlock} shapes as vanilla panes).
 *
 * Deliberately not {@code BeaconBeamBlock}: that reports a {@code DyeColor}, and a white beam
 * through cerise glass would be wrong. A beacon beam passes through ours untinted, like plain glass.
 */
public final class GlassBlocks {
	private GlassBlocks() {}

	public static final class Glass extends TransparentBlock implements PolymerTexturedBlock {
		private final BlockState client;

		public Glass(Properties properties, Identifier id) {
			super(properties);
			this.client = ClientStates.request(id.toString(), BlockModelType.LEAVES,
					PolymerBlockModel.of(ColoredBlocks.model(id)));
		}

		@Override
		public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
			return client;
		}
	}

	/**
	 * One pre-baked model per connection set (post plus arms; Polymer donors take one model, not a
	 * multipart). In {@link Looks.Look#DONOR} mode each (connections, waterlogged) state gets a
	 * visible copper-bars donor carrying that model; in {@link Looks.Look#DISPLAY} mode the donor is
	 * the shared invisible one and an item display carries the model. Same hitbox either way.
	 */
	public static final class Pane extends IronBarsBlock implements PolymerTexturedBlock, BlockWithElementHolder, ShapedBlocks.DisplayProvider {
		private final Map<BlockState, BlockState> client = new IdentityHashMap<>();
		private final Map<BlockState, Identifier> displayModels = new IdentityHashMap<>();
		private final Block glass;
		private final Looks.Look look;

		public Pane(Block glass, Properties properties, Identifier id) {
			super(properties);
			this.glass = glass;
			this.look = Looks.of(Family.STAINED_GLASS_PANE);
			for (BlockState state : getStateDefinition().getPossibleStates()) {
				List<Direction> sides = new ArrayList<>(4);
				StringBuilder suffix = new StringBuilder();
				for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
					if (state.getValue(PROPERTY_BY_DIRECTION.get(d))) {
						sides.add(d);
						suffix.append(d.getSerializedName().charAt(0));
					}
				}
				boolean waterlogged = state.getValue(WATERLOGGED);
				String modelPath = id.getPath() + "_" + (suffix.isEmpty() ? "post" : suffix.toString());
				BlockModelType pool = BlockModelType.getBars(waterlogged, sides);
				String what = id + "[" + suffix + (waterlogged ? ",waterlogged" : "") + "]";
				if (look == Looks.Look.DONOR) {
					client.put(state, ClientStates.request(what, pool,
							PolymerBlockModel.of(Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID, "block/" + modelPath))));
				} else {
					client.put(state, ClientStates.requestEmpty(what, pool));
					// item-definition id (assets/moredyes/items/<path>.json), see ShapedBlocks.displayStack
					displayModels.put(state, Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID, modelPath));
				}
			}
		}

		@Override
		public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
			BlockState s = client.get(state);
			return s != null ? s : ClientStates.error(state);
		}

		@Override
		public @Nullable ItemStack displayStack(BlockState state) {
			Identifier model = displayModels.get(state);
			return model == null ? null : ShapedBlocks.displayStack(model);
		}

		@Override
		public Quaternionf displayRotation(BlockState state) {
			return ShapedBlocks.FIXED_FLIP;
		}

		@Override
		public @Nullable ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState initialBlockState) {
			return look == Looks.Look.DISPLAY ? new ShapedBlocks.DisplayHolder(this, initialBlockState) : null;
		}

		@Override
		public BlockState getPolymerBreakEventBlockState(BlockState state, @Nullable PacketContext context) {
			// Break particles from our glass texture rather than the copper donor's.
			return ClientStates.clientStateOf(glass, context);
		}
	}
}
