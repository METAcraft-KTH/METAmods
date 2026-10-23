package se.metacraft.moredyes.content.block;

import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockAwareAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import se.metacraft.moredyes.MoreDyes;
import se.metacraft.moredyes.content.Family;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import se.metacraft.moredyes.sign.SignColors;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Stairs and slabs: shapes Polymer's visible pools cannot hold at scale. The client is told the block
 * is vanilla's wool or concrete stair or slab in the nearest vanilla colour ({@link #vanillaShape}),
 * with the same facing, half, shape, type and waterlogging, and one item display per placed block
 * draws our model over it. Item display per block after craftycorvid/wool-polymer (MIT). Double slabs
 * have no display: they are sent as the material's own full block.
 *
 * Why a visible block under the display and not an invisible donor (which is what wool-polymer and
 * this mod used before): a client hides a neighbour's faces behind the stair it is told stands there.
 * An invisible stair is a hole into the world (x-ray) wherever the display is not drawn: between the
 * block arriving and the display's first update when one is placed, and for good beyond the display's
 * view range (64 blocks at the client's default entity distance), where the client stops drawing
 * display entities but keeps the block. Under the display is now a pink wool stair where a Cerise
 * one stands, which is about what a stair that far away looks like anyway. It takes no Polymer donor
 * states.
 *
 * The display covers the vanilla faces exactly, so on its own it would z-fight with them; it is grown
 * a hair ({@link #COVER_GROWTH}) about a point inside the shape that sees every face, so every one of
 * its faces lies just outside the vanilla face under it.
 *
 * Break particles and sound come from {@link PolymerTexturedBlock#getPolymerBreakEventBlockState},
 * which returns the material (see {@link ClientStates#materialBreakState}) so the particles use our
 * texture. Mining and walking particles come from the vanilla block, in its colour.
 */
public final class ShapedBlocks {
	private ShapedBlocks() {}

	static final Quaternionf NO_ROTATION = new Quaternionf();
	/** Item displays render block models turned 180° about Y relative to the placed blockstate. */
	static final Quaternionf FIXED_FLIP = new Quaternionf().rotateY((float) Math.PI);

	/** What a placed shape shows: the model to display and how to rotate it. */
	public interface DisplayProvider {
		@Nullable ItemStack displayStack(BlockState state);

		Quaternionf displayRotation(BlockState state);

		/** World-axis offset of the display from its default (flush in the cell) position. */
		default Vector3f displayTranslation(BlockState state) {
			return NO_TRANSLATION;
		}

		/** Uniform growth of the display about the cell centre, before the translation; 1 is flush. */
		default float displayGrowth(BlockState state) {
			return 1;
		}
	}

	static final Vector3f NO_TRANSLATION = new Vector3f();

	/**
	 * How much a display that covers a vanilla block of its own shape is grown. The centre of growth
	 * ({@link #coverTranslation}) is a quarter block from the nearest face plane, so each face clears
	 * the vanilla face under it by 1/1024 block to 3/1024. Flush, the vanilla client already draws the
	 * display over the block (entities come after terrain and win a tie in depth), so the margin is
	 * only there for rounding: a face computed a hair nearer through the chunk mesh (or by a renderer
	 * mod that packs chunk vertices its own way) would show through in specks. Not more: at 1/64 the display's
	 * far faces covered the far edges of the selection outline (seen in the client screenshot test).
	 */
	public static final float COVER_GROWTH = 1 + 1f / 256;

	/**
	 * The translation that moves a {@link #COVER_GROWTH} growth's centre from the cell centre to
	 * {@code centre} (world axes, relative to the cell centre). The centre must see every face of the
	 * shape from inside it, or the faces it does not see sink into the vanilla block.
	 */
	public static Vector3f coverTranslation(Vector3f centre) {
		return new Vector3f(centre).mul(1 - COVER_GROWTH);
	}

	/**
	 * Vanilla's block of a stairs or slab family in a dye colour: what the client is shown under the
	 * display. The colour is {@link SignColors#nearestDye}, as everywhere a
	 * vanilla client needs one of the sixteen.
	 */
	public static Block vanillaShape(Family family, DyeColor dye) {
		return switch (family) {
			case WOOL_STAIRS -> Blocks.WOOL_STAIRS.pick(dye);
			case WOOL_SLAB -> Blocks.WOOL_SLAB.pick(dye);
			case CONCRETE_STAIRS -> Blocks.CONCRETE_STAIRS.pick(dye);
			case CONCRETE_SLAB -> Blocks.CONCRETE_SLAB.pick(dye);
			default -> throw new IllegalArgumentException(family.id + " is not a stairs or slab family");
		};
	}

	public static ItemStack displayStack(Identifier model) {
		// A plain vanilla item with ITEM_MODEL: Polymer passes vanilla stacks through untouched, and a
		// client with the (required) pack resolves the model. Nothing to register.
		ItemStack stack = new ItemStack(Items.OAK_STAIRS);
		stack.set(DataComponents.ITEM_MODEL, model);
		return stack;
	}

	/**
	 * ITEM_MODEL takes an item-definition id ({@code assets/moredyes/items/<path>.json}), not a model
	 * id; gen_assets.py emits a definition per shape variant pointing at the block model.
	 */
	static Identifier model(Identifier blockId, String suffix) {
		return Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID, blockId.getPath() + suffix);
	}

	public static final class Stairs extends StairBlock implements PolymerTexturedBlock, BlockWithElementHolder, DisplayProvider {
		/**
		 * The centre of growth in the frame of vanilla's three stair models (east-facing, bottom
		 * half): the lower half of the quarter column x, z in 8..16, which is under the upper step in
		 * all three and behind every riser. Turned with the model by {@link #blockstateRotation}.
		 */
		private static final Vector3f MODEL_COVER_CENTRE = new Vector3f(0.25F, -0.25F, 0.25F);

		private final Map<BlockState, BlockState> client = new IdentityHashMap<>();
		private final Block material;
		private final Identifier straight, inner, outer;

		/** @param shown vanilla's stair the client is shown under the display ({@link #vanillaShape}) */
		public Stairs(Block material, Block shown, Properties properties, Identifier id) {
			super(material.defaultBlockState(), properties);
			this.material = material;
			this.straight = model(id, "");
			this.inner = model(id, "_inner");
			this.outer = model(id, "_outer");
			for (BlockState state : getStateDefinition().getPossibleStates()) {
				client.put(state, shown.withPropertiesOf(state));
			}
		}

		@Override
		public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
			BlockState s = client.get(state);
			return s != null ? s : ClientStates.error(state);
		}

		@Override
		public BlockState getPolymerBreakEventBlockState(BlockState state, @Nullable PacketContext context) {
			return ClientStates.materialBreakState(material);
		}

		@Override
		public @Nullable ItemStack displayStack(BlockState state) {
			Identifier model = switch (state.getValue(SHAPE)) {
				case STRAIGHT -> straight;
				case INNER_LEFT, INNER_RIGHT -> inner;
				case OUTER_LEFT, OUTER_RIGHT -> outer;
			};
			return ShapedBlocks.displayStack(model);
		}

		@Override
		public Quaternionf displayRotation(BlockState state) {
			return stairRotation(state.getValue(FACING), state.getValue(HALF), state.getValue(SHAPE));
		}

		@Override
		public Vector3f displayTranslation(BlockState state) {
			return coverTranslation(coverCentre(state));
		}

		@Override
		public float displayGrowth(BlockState state) {
			return COVER_GROWTH;
		}

		/** Where this state's display grows from, in world axes from the cell centre. */
		public static Vector3f coverCentre(BlockState state) {
			return blockstateRotation(state.getValue(FACING), state.getValue(HALF), state.getValue(SHAPE))
					.transform(new Vector3f(MODEL_COVER_CENTRE));
		}

		@Override
		public @Nullable ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState initialBlockState) {
			return new DisplayHolder(this, initialBlockState);
		}

		/**
		 * Orient the item-display model like a vanilla stair: {@link #blockstateRotation}, then a
		 * global 180° about Y because item displays render block models turned around relative to
		 * placed blockstates.
		 */
		static Quaternionf stairRotation(Direction facing, Half half, StairsShape shape) {
			return blockstateRotation(facing, half, shape).rotateY((float) Math.PI);
		}

		/** The (x, y) rotation vanilla's stair blockstate file uses for this state, as R_Y(-y)·R_X(-x). */
		static Quaternionf blockstateRotation(Direction facing, Half half, StairsShape shape) {
			int base = switch (facing) {
				case NORTH -> 270;
				case EAST -> 0;
				case SOUTH -> 90;
				case WEST -> 180;
				default -> 0;
			};
			int x, y;
			if (half == Half.BOTTOM) {
				x = 0;
				boolean left = shape == StairsShape.INNER_LEFT || shape == StairsShape.OUTER_LEFT;
				y = left ? (base + 270) % 360 : base;
			} else {
				x = 180;
				boolean right = shape == StairsShape.INNER_RIGHT || shape == StairsShape.OUTER_RIGHT;
				y = right ? (base + 90) % 360 : base;
			}
			return new Quaternionf()
					.rotateY((float) Math.toRadians(-y))
					.rotateX((float) Math.toRadians(-x));
		}
	}

	public static final class Slab extends SlabBlock implements PolymerTexturedBlock, BlockWithElementHolder, DisplayProvider {
		/** A slab grows from its own centre, a quarter block below or above the cell's. */
		private static final Vector3f BOTTOM_COVER_CENTRE = new Vector3f(0, -0.25F, 0), TOP_COVER_CENTRE = new Vector3f(0, 0.25F, 0);
		private static final Vector3f BOTTOM_COVER = coverTranslation(BOTTOM_COVER_CENTRE), TOP_COVER = coverTranslation(TOP_COVER_CENTRE);

		private final Map<BlockState, BlockState> client = new IdentityHashMap<>();
		private final Block material;
		private final Identifier bottom, top;

		/** @param shown vanilla's slab the client is shown under the display ({@link #vanillaShape}) */
		public Slab(Block material, Block shown, Properties properties, Identifier id) {
			super(properties);
			this.material = material;
			this.bottom = model(id, "");
			this.top = model(id, "_top");
			for (BlockState state : getStateDefinition().getPossibleStates()) {
				// a double slab is resolved per viewer: the material's own client state
				if (state.getValue(TYPE) != SlabType.DOUBLE) client.put(state, shown.withPropertiesOf(state));
			}
		}

		@Override
		public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
			if (state.getValue(TYPE) == SlabType.DOUBLE) {
				return ClientStates.clientStateOf(material, context);
			}
			BlockState s = client.get(state);
			return s != null ? s : ClientStates.error(state);
		}

		@Override
		public BlockState getPolymerBreakEventBlockState(BlockState state, @Nullable PacketContext context) {
			return ClientStates.materialBreakState(material);
		}

		@Override
		public @Nullable ItemStack displayStack(BlockState state) {
			SlabType type = state.getValue(TYPE);
			if (type == SlabType.DOUBLE) return null;
			return ShapedBlocks.displayStack(type == SlabType.TOP ? top : bottom);
		}

		@Override
		public Quaternionf displayRotation(BlockState state) {
			return NO_ROTATION;
		}

		@Override
		public Vector3f displayTranslation(BlockState state) {
			return state.getValue(TYPE) == SlabType.TOP ? TOP_COVER : BOTTOM_COVER;
		}

		@Override
		public float displayGrowth(BlockState state) {
			return COVER_GROWTH;
		}

		/** Where this state's display grows from, in world axes from the cell centre. */
		public static Vector3f coverCentre(BlockState state) {
			return state.getValue(TYPE) == SlabType.TOP ? TOP_COVER_CENTRE : BOTTOM_COVER_CENTRE;
		}

		@Override
		public @Nullable ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState initialBlockState) {
			if (initialBlockState.getValue(TYPE) == SlabType.DOUBLE) return null;
			return new DisplayHolder(this, initialBlockState);
		}
	}

	/**
	 * One item display per placed block, flush in the cell unless its provider grows it. Rebuilds itself on block-state changes
	 * (a stair's corner shape changing when a neighbour is placed, a slab becoming double).
	 * Lifecycle (create on chunk load, destroy on unload/break) is handled by Polymer.
	 */
	public static final class DisplayHolder extends ElementHolder {
		private final DisplayProvider provider;
		private @Nullable ItemDisplayElement element;

		public DisplayHolder(DisplayProvider provider, BlockState initialState) {
			this.provider = provider;
			refresh(initialState);
		}

		private void refresh(BlockState state) {
			ItemStack stack = provider.displayStack(state);
			if (stack == null) {
				if (element != null) {
					removeElement(element);
					element = null;
				}
				return;
			}
			if (element == null) {
				element = new ItemDisplayElement();
				// FIXED is the "placed block" context but bakes a 0.5 scale; our models also pin
				// display.fixed.scale 0.5, so ×2 here gives a flush 1×1×1.
				element.setItemDisplayContext(ItemDisplayContext.FIXED);
				element.setInvisible(true);
				element.setDisplaySize(1, 1);
				addElement(element);
			}
			element.setItem(stack);
			element.setScale(new Vector3f(2 * provider.displayGrowth(state)));
			element.setLeftRotation(provider.displayRotation(state));
			element.setTranslation(provider.displayTranslation(state));
		}

		@Override
		public void notifyUpdate(HolderAttachment.UpdateType updateType) {
			super.notifyUpdate(updateType);
			if (updateType == BlockAwareAttachment.BLOCK_STATE_UPDATE) {
				var attachment = BlockAwareAttachment.get(this);
				if (attachment != null) {
					refresh(attachment.getBlockState());
					// Block-bound holders are not ticked (tickElementHolder is false), and only a tick
					// sends element changes to players already watching; push this one now.
					tick();
				}
			}
		}
	}
}
