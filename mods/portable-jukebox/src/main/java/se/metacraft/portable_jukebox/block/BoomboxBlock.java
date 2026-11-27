package se.metacraft.portable_jukebox.block;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import se.metacraft.lib.util.EntityRef;
import se.metacraft.portable_jukebox.item.BoomboxItem;
import se.metacraft.portable_jukebox.item.components.Components;
import se.metacraft.portable_jukebox.entity.BoomboxEntity;
import se.metacraft.portable_jukebox.gui.BoomboxGui;

import java.util.List;

public class BoomboxBlock extends BaseEntityBlock implements PolymerTexturedBlock, BlockWithElementHolder {

	public static final IntegerProperty ROTATION = SkullBlock.ROTATION;

	private static final VoxelShape SHAPE = Block.column(16.0, 0.0, 8.0);

	private final BlockState clientState;

	public BoomboxBlock(Properties settings) {
		super(settings);
		registerDefaultState(stateDefinition.any().setValue(ROTATION, 0));
		this.clientState = PolymerBlockResourceUtils.requestEmpty(BlockModelType.TRIPWIRE);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ROTATION);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState()
				.setValue(ROTATION, RotationSegment.convertToSegment(context.getRotation()));
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(ROTATION, rotation.rotate(state.getValue(ROTATION), 16));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.setValue(ROTATION, mirror.mirror(state.getValue(ROTATION), 16));
	}

	@Override
	public ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState state) {
		var stack = new ItemStack(Items.PAPER);
		var display = new ItemDisplayElement(stack);
		display.setItemDisplayContext(ItemDisplayContext.NONE);
		display.setDisplaySize(1, 1);
		// The display is centred on the block, like the model's 16x16x16 grid, so the model's bottom
		// sits on the floor. Its front (the speakers) faces north, so turning it by the placer's yaw
		// points the front back at them.
		float yaw = Mth.wrapDegrees(state.getValue(ROTATION) * 22.5F + 180);
		display.setTransformation(new Matrix4f().rotateY((float) Math.toRadians(-yaw)));
		var holder = new ElementHolder();
		holder.addElement(display);
		return holder;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return Shapes.empty();
	}

	@Override
	public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
		super.setPlacedBy(world, pos, state, placer, itemStack);
		var blockEntity = world.getBlockEntity(pos);
		if (blockEntity instanceof BoomboxBlockEntity b && placer != null) {
			b.setBoombox(itemStack.copy());
			BoomboxEntity.transfer(
					EntityRef.fromEntity(placer), EntityRef.fromBlock(b), b.getBoombox()
			);
			// Creative keeps the stack in hand, which would otherwise still own the sound.
			itemStack.remove(Components.BOOMBOX_ENTITY);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
		var block = world.getBlockEntity(pos);
		if (block instanceof BoomboxBlockEntity boombox) {
			var gui = BoomboxGui.create((ServerPlayer) player, boombox.getBoombox(), EntityRef.fromBlock(boombox));
			gui.open();
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	/** Like a shulker box, creative players still get a boombox that holds discs. */
	@Override
	public BlockState playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
		if (
			!world.isClientSide() && player.preventsBlockDrops() &&
			world.getBlockEntity(pos) instanceof BoomboxBlockEntity boombox && BoomboxItem.hasDiscs(boombox.getBoombox())
		) {
			var entity = new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, droppedStack(boombox));
			entity.setDefaultPickUpDelay();
			world.addFreshEntity(entity);
		}
		return super.playerWillDestroy(world, pos, state, player);
	}

	private static ItemStack droppedStack(BoomboxBlockEntity boombox) {
		var stack = boombox.getBoombox().copy();
		stack.remove(Components.BOOMBOX_ENTITY);
		return stack;
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
		BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
		if (blockEntity instanceof BoomboxBlockEntity boombox) {
			return List.of(droppedStack(boombox));
		}
		return super.getDrops(state, builder);
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state, boolean includeData) {
		if (world.getBlockEntity(pos) instanceof BoomboxBlockEntity boombox && !boombox.getBoombox().isEmpty()) {
			// A plain pick gives an empty boombox; Ctrl + pick keeps the contents.
			var stack = droppedStack(boombox);
			if (!includeData) {
				stack.remove(Components.BOOMBOX);
				stack.remove(Components.BOOMBOX_STORAGE);
				stack.remove(Components.CURRENT_SLOT);
				stack.remove(Components.BOOMBOX_CONFIGURATION);
				stack.remove(Components.AUTOPLAY);
				stack.remove(Components.SHUFFLE);
			}
			return stack;
		}
		return super.getCloneItemStack(world, pos, state, includeData);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
		BlockEntity blockEntity = world.getBlockEntity(pos);
		super.affectNeighborsAfterRemoval(state, world, pos, moved);
		if (blockEntity instanceof BoomboxBlockEntity boombox) {
			world.updateNeighbourForOutputSignal(pos, state.getBlock());
		}
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BoomboxBlockEntity(pos, state);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos, Direction direction) {
		var blockEntity = world.getBlockEntity(pos);
		if (blockEntity instanceof BoomboxBlockEntity portable) {
			return BoomboxItem.getComparatorOutput(portable.getBoombox());
		}
		return 0;
	}

	@Override
	protected void neighborChanged(
			BlockState state, Level world, BlockPos pos, Block sourceBlock,
			Orientation orientation, boolean notify
	) {
		var blockEntity = world.getBlockEntity(pos);
		if (blockEntity instanceof BoomboxBlockEntity portable) {
			BoomboxItem.updateRedstone(EntityRef.fromBlock(portable), portable.getBoombox());
		}
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @org.jspecify.annotations.Nullable PacketContext context) {
		return this.clientState;
	}

	@Override
	public BlockState getPolymerBreakEventBlockState(BlockState state, @org.jspecify.annotations.Nullable PacketContext context) {
		return Blocks.NOTE_BLOCK.defaultBlockState();
	}
}
