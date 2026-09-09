package metacraft.moredyes.content;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockAwareAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import metacraft.moredyes.MoreDyes;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Stairs and slabs: shapes Polymer's visible pools cannot hold at scale, so each placed block is an
 * invisible, shape-correct donor state (correct collision and outline on the client) plus one item
 * display carrying the real model. Pattern from craftycorvid/wool-polymer (MIT).
 *
 * Break particles and sound come from {@link PolymerTexturedBlock#getPolymerBreakEventBlockState},
 * which returns the material's own client state so the particles use our texture.
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
    }

    static final Vector3f NO_TRANSLATION = new Vector3f();

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
        private final Map<BlockState, BlockState> client = new IdentityHashMap<>();
        private final Block material;
        private final Identifier straight, inner, outer;

        public Stairs(Block material, Properties properties, Identifier id) {
            super(material.defaultBlockState(), properties);
            this.material = material;
            this.straight = model(id, "");
            this.inner = model(id, "_inner");
            this.outer = model(id, "_outer");
            for (BlockState state : getStateDefinition().getPossibleStates()) {
                client.put(state, ClientStates.requestEmpty(id + "[" + state.getValue(FACING) + "]",
                        BlockModelType.getStairs(state.getValue(FACING), state.getValue(HALF),
                                state.getValue(SHAPE), state.getValue(WATERLOGGED))));
            }
        }

        @Override
        public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
            BlockState s = client.get(state);
            return s != null ? s : ClientStates.error(state);
        }

        @Override
        public BlockState getPolymerBreakEventBlockState(BlockState state, @Nullable PacketContext context) {
            return ClientStates.clientStateOf(material, context);
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
        public @Nullable ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState initialBlockState) {
            return new DisplayHolder(this, initialBlockState);
        }

        /**
         * Orient the item-display model like a vanilla stair: the (x, y) rotation vanilla's stair
         * blockstate file uses for this state, as R_Y(-y)·R_X(-x), then a global 180° about Y because
         * item displays render block models turned around relative to placed blockstates.
         */
        static Quaternionf stairRotation(Direction facing, Half half, StairsShape shape) {
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
                    .rotateX((float) Math.toRadians(-x))
                    .rotateY((float) Math.PI);
        }
    }

    public static final class Slab extends SlabBlock implements PolymerTexturedBlock, BlockWithElementHolder, DisplayProvider {
        private final Map<BlockState, BlockState> client = new IdentityHashMap<>();
        private final Block material;
        private final Identifier bottom, top;

        public Slab(Block material, Properties properties, Identifier id) {
            super(properties);
            this.material = material;
            this.bottom = model(id, "");
            this.top = model(id, "_top");
            for (BlockState state : getStateDefinition().getPossibleStates()) {
                boolean wl = state.getValue(WATERLOGGED);
                BlockState c = switch (state.getValue(TYPE)) {
                    case DOUBLE -> null; // resolved per viewer: the material's own client state
                    case BOTTOM -> ClientStates.requestEmpty(id + "[bottom]", BlockModelType.getSlab(true, wl));
                    case TOP -> ClientStates.requestEmpty(id + "[top]", BlockModelType.getSlab(false, wl));
                };
                if (c != null) client.put(state, c);
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
            return ClientStates.clientStateOf(material, context);
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
        public @Nullable ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState initialBlockState) {
            if (initialBlockState.getValue(TYPE) == SlabType.DOUBLE) return null;
            return new DisplayHolder(this, initialBlockState);
        }
    }

    /**
     * One item display per placed block, flush in the cell. Rebuilds itself on block-state changes
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
                element.setScale(new Vector3f(2));
                element.setInvisible(true);
                element.setDisplaySize(1, 1);
                addElement(element);
            }
            element.setItem(stack);
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
                }
            }
        }
    }
}
