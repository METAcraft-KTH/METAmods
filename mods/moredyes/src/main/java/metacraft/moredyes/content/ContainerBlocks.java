package metacraft.moredyes.content;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockAwareAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import metacraft.moredyes.MoreDyes;
import metacraft.moredyes.mixin.BlockEntityTypeAccessor;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Beds and shulker boxes (Phase 5). */
public final class ContainerBlocks {
	private ContainerBlocks() {}

	/**
	 * Beds are plain block models in 26.x, and Polymer hands out the spare {@code occupied=true}
	 * bed states per (facing, part) as donors. So a bed in our colour is a real bed on the client:
	 * correct shape, sleeping direction and all, with our head/foot models. Vanilla's DyeColor is
	 * WHITE and only feeds paths that can't show our colour anyway.
	 */
	public static final class Bed extends BedBlock implements PolymerTexturedBlock {
		private final Map<BlockState, BlockState> client = new IdentityHashMap<>();

		public Bed(Properties properties, Identifier id) {
			super(DyeColor.WHITE, properties);
			for (BlockState state : getStateDefinition().getPossibleStates()) {
				if (state.getValue(OCCUPIED)) continue; // same look; mapped below
				Direction facing = state.getValue(FACING);
				String part = state.getValue(PART).getSerializedName();
				Identifier model = Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID, "block/" + id.getPath() + "_" + part);
				int y = switch (facing) {
					case EAST -> 90;
					case SOUTH -> 180;
					case WEST -> 270;
					default -> 0;
				};
				BlockState donor = ClientStates.request(id + "[" + facing + "," + part + "]",
						BlockModelType.getBed(facing, state.getValue(PART)), PolymerBlockModel.of(model, 0, y));
				client.put(state, donor);
				client.put(state.setValue(OCCUPIED, true), donor);
			}
		}

		@Override
		public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
			BlockState s = client.get(state);
			return s != null ? s : ClientStates.error(state);
		}
	}

	/**
	 * Shulker boxes render through a block entity on the client, so the placed box is an invisible
	 * full-cube donor (leaves pool) plus two item displays (base and lid); the lid slides out while the box is
	 * open, driven by the same block event vanilla uses. Contents, hoppers and the menu are the
	 * vanilla block entity, whose type is taught about our blocks at registration.
	 */
	public static final class ShulkerBox extends ShulkerBoxBlock implements PolymerTexturedBlock, BlockWithElementHolder {
		private final Identifier id;
		private final BlockState client;

		public ShulkerBox(Properties properties, Identifier id) {
			super(DyeColor.WHITE, properties);
			this.id = id;
			// Not FULL_BLOCK: Polymer's invisible full-block state is a waterlogged double slab, which
			// makes the client render water in the box. The leaves pool's invisible state is a dry,
			// full-collision, non-occluding cube, which is exactly what a shulker box is.
			this.client = ClientStates.requestEmpty(id.toString(), BlockModelType.LEAVES);
			addValidBlock(BlockEntityTypes.SHULKER_BOX, this);
		}

		@Override
		public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
			return client;
		}

		@Override
		public BlockState getPolymerBreakEventBlockState(BlockState state, @Nullable PacketContext context) {
			return Blocks.DYED_SHULKER_BOX.white().defaultBlockState();
		}

		@Override
		public @Nullable ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState initialBlockState) {
			return new LidHolder(id, initialBlockState.getValue(FACING));
		}

		@Override
		public boolean tickElementHolder(ServerLevel world, BlockPos pos, BlockState initialBlockState) {
			return true;
		}

		@Override
		protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
			boolean result = super.triggerEvent(state, level, pos, id, param);
			if (id == 1 && !level.isClientSide()) {
				BlockAwareAttachment attachment = BlockAwareAttachment.get(level, pos);
				if (attachment != null && attachment.holder() instanceof LidHolder lid) {
					lid.setOpen(param > 0);
				}
			}
			return result;
		}
	}

	/**
	 * Base + sliding lid. Vanilla (ShulkerBoxRenderer / ShulkerModel) lifts the lid half a block and
	 * turns it 270° about the box axis over ~10 ticks while open, and back on close.
	 */
	static final class LidHolder extends ElementHolder {
		private static final Quaternionf FIXED_FLIP = new Quaternionf().rotateY((float) Math.PI);
		private final ItemDisplayElement base, lid;
		private final Vector3f axis;
		private final Quaternionf rot;
		private boolean open;
		private float progress;

		LidHolder(Identifier id, Direction facing) {
			this.axis = new Vector3f(facing.getStepX(), facing.getStepY(), facing.getStepZ());
			this.rot = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), axis).mul(FIXED_FLIP);
			this.base = part(rot);
			this.lid = part(rot);
			this.base.setItem(ShapedBlocks.displayStack(Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID, id.getPath() + "_base")));
			this.lid.setItem(ShapedBlocks.displayStack(Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID, id.getPath() + "_lid")));
		}

		private ItemDisplayElement part(Quaternionf rot) {
			ItemDisplayElement e = new ItemDisplayElement();
			e.setItemDisplayContext(ItemDisplayContext.FIXED);
			e.setScale(new Vector3f(2));
			e.setLeftRotation(rot);
			e.setInvisible(true);
			e.setDisplaySize(1, 1);
			e.setInterpolationDuration(1);
			addElement(e);
			return e;
		}

		void setOpen(boolean open) {
			this.open = open;
		}

		@Override
		protected void onTick() {
			float target = open ? 1.0F : 0.0F;
			if (progress == target) return;
			progress = open ? Math.min(1.0F, progress + 0.1F) : Math.max(0.0F, progress - 0.1F);
			lid.setTranslation(new Vector3f(axis).mul(0.5F * progress));
			lid.setLeftRotation(new Quaternionf().rotationAxis((float) Math.toRadians(270.0F * progress), axis).mul(rot));
			lid.startInterpolationIfDirty();
		}
	}

	/** Teach a vanilla block-entity type about one of our blocks (its valid-block set is immutable). */
	static void addValidBlock(BlockEntityType<?> type, Block block) {
		BlockEntityTypeAccessor accessor = (BlockEntityTypeAccessor) type;
		Set<Block> blocks = new HashSet<>(accessor.moredyes$validBlocks());
		blocks.add(block);
		accessor.moredyes$setValidBlocks(blocks);
	}
}
