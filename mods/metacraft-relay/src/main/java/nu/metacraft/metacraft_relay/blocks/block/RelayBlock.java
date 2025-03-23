package nu.metacraft.metacraft_relay.blocks.block;

import com.mojang.serialization.MapCodec;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockAwareAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.block.*;
import net.minecraft.block.dispenser.DispenserBehavior;
import net.minecraft.block.dispenser.FallibleItemDispenserBehavior;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;
import nu.metacraft.metacraft_relay.blocks.entity.RelayBlockEntity;
import nu.metacraft.metacraft_relay.items.RelayComponents;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import se.datasektionen.mc.metacraft_lib.util.TaskScheduler;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.List;
import java.util.Optional;

public class RelayBlock extends Block implements PolymerBlock, BlockEntityProvider, BlockWithElementHolder {

	public static final MapCodec<RelayBlock> CODEC = createCodec(RelayBlock::new);
	public static final BooleanProperty CHARGED = BooleanProperty.of("charged");

	public RelayBlock(Settings settings) {
		super(settings);
		this.setDefaultState(this.stateManager.getDefaultState().with(CHARGED, false));
	}

	@Override
	public @Nullable ElementHolder createElementHolder(ServerWorld world, BlockPos pos, BlockState initialBlockState) {
		return new ModelHolder();
	}

	@Override
	protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		super.onBlockAdded(state, world, pos, oldState, notify);
		//Piston fix.
		//Attachment should have always been created at this point, but if it has not, we have the bug and fix it here.
		//We must check even if notify is false, as when a block is "spat out" by the piston this will be false even though the attachment will be missing.
		if (BlockBoundAttachment.get(world, pos) == null && world instanceof ServerWorld serverWorld) {
			new BlockBoundAttachment(
					createElementHolder(serverWorld, pos, state),
					world.getWorldChunk(pos), state,
					pos.toImmutable(),
					Vec3d.ofCenter(pos).add(
							getElementHolderOffset(serverWorld, pos, state)
					),
					tickElementHolder(serverWorld, pos, state)
			);
		}
	}

	@Override
	public ElementHolder createMovingElementHolder(
			ServerWorld world, BlockPos pos, BlockState initialBlockState,
			@Nullable ElementHolder oldMovingElementHolder
	) { //Piston fix. The piston moving block entity is not given the attachment properly, so we return null instead.
		return null;
	}

	@Override
	protected MapCodec<? extends Block> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(CHARGED);
	}

	public static MutableText getTargetText(String targetPos, String targetDim) {
		return Text.translatableWithFallback(
				"block.metacraft.relay.target", "Target: " + targetPos + " in " + targetDim,
				targetPos, targetDim
		).styled(style -> style.withFormatting(Formatting.GREEN));
	}

	@Override
	public BlockState getPolymerBlockState(BlockState blockState, PacketContext packetContext) {
		return Blocks.STONE.getDefaultState();
	}

	@Override
	public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new RelayBlockEntity(pos, state);
	}

	public static int getLightLevel(BlockState state, int maxLevel) {
		return state.get(CHARGED) ? maxLevel : 0;
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
		super.onPlaced(world, pos, state, placer, itemStack);
		var entity = world.getBlockEntity(pos);
		if (entity instanceof RelayBlockEntity e) {
			e.setComponents(itemStack.getComponents());
		}
	}

	private static boolean hasStillWater(BlockPos pos, World world) {
		FluidState fluidState = world.getFluidState(pos);
		if (!fluidState.isIn(FluidTags.WATER)) {
			return false;
		} else if (fluidState.isStill()) {
			return true;
		} else {
			float f = (float)fluidState.getLevel();
			if (f < 2.0F) {
				return false;
			} else {
				FluidState fluidState2 = world.getFluidState(pos.down());
				return !fluidState2.isIn(FluidTags.WATER);
			}
		}
	}

	private void explode(BlockState state, World world, BlockPos explodedPos) {
		world.removeBlock(explodedPos, false);
		boolean bl = Direction.Type.HORIZONTAL.stream().map(explodedPos::offset).anyMatch(pos -> hasStillWater(pos, world));
		final boolean bl2 = bl || world.getFluidState(explodedPos.up()).isIn(FluidTags.WATER);
		ExplosionBehavior explosionBehavior = new ExplosionBehavior() {
			@Override
			public Optional<Float> getBlastResistance(Explosion explosion, BlockView world, BlockPos pos, BlockState blockState, FluidState fluidState) {
				return pos.equals(explodedPos) && bl2
						? Optional.of(Blocks.WATER.getBlastResistance())
						: super.getBlastResistance(explosion, world, pos, blockState, fluidState);
			}
		};
		Vec3d vec3d = explodedPos.toCenterPos();
		world.createExplosion(null, world.getDamageSources().badRespawnPoint(vec3d), explosionBehavior, vec3d, 5.0F, true, World.ExplosionSourceType.BLOCK);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		var entity = world.getBlockEntity(pos);
		if (entity instanceof RelayBlockEntity e) {
			if (state.get(CHARGED)) {
				if (e.shouldExplode()) {
					explode(state, world, pos);
					return ActionResult.SUCCESS_SERVER;
				}
				e.getTarget().resultOrPartial(
						err -> player.sendMessage(Text.literal(err).styled(
								style -> style.withFormatting(Formatting.RED)
						), true)
				).ifPresent(
						target -> {
							player.teleportTo(target);
							world.setBlockState(pos, state.with(CHARGED, false));
						}
				);
				return ActionResult.SUCCESS_SERVER;
			}
		}
		return super.onUse(state, world, pos, player, hit);
	}

	public static boolean isChargeItem(ItemStack stack, World world, BlockPos pos) {
		var blockEntity = world.getBlockEntity(pos);
		if (blockEntity != null) {
			var chargeItems = blockEntity.getComponents().get(RelayComponents.VALID_CHARGE_ITEM);
			return chargeItems != null && stack.isIn(chargeItems);
		}
		return false;
	}

	private static boolean canCharge(BlockState state) {
		return !state.get(CHARGED);
	}

	private static void charge(World world, BlockPos pos, BlockState state, @Nullable Entity charger) {
		world.setBlockState(pos, state.with(CHARGED, true));
		world.emitGameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Emitter.of(charger, state));
		world.playSound(
				null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
				SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 1.0F, 1.0F
		);
	}

	@Override
	protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (isChargeItem(stack, world, pos) && canCharge(state)) {
			charge(world, pos, state, player);
			stack.decrementUnlessCreative(1, player);
			return ActionResult.SUCCESS_SERVER;
		}
		return super.onUseWithItem(stack, state, world, pos, player, hand, hit);
	}

	public static class RefillBehaviour extends FallibleItemDispenserBehavior {

		private final DispenserBehavior fallback;

		public RefillBehaviour(DispenserBehavior fallback) {
			this.fallback = fallback;
		}

		@Override
		protected ItemStack dispenseSilently(BlockPointer pointer, ItemStack stack) {
			Direction direction = pointer.state().get(DispenserBlock.FACING);
			BlockPos targetPos = pointer.pos().offset(direction);
			var targetState = pointer.world().getBlockState(targetPos);
			if (RelayBlock.isChargeItem(stack, pointer.world(), targetPos)) {
				if (canCharge(targetState)) {
					charge(pointer.world(), targetPos, targetState, null);
					stack.decrement(1);
					setSuccess(true);
				} else {
					setSuccess(false);
				}

				return stack;
			}
			return fallback.dispense(pointer, stack);
		}
	}

	public static class ModelHolder extends ElementHolder {

		private final ItemDisplayElement display;

		public ModelHolder() {
			display = new ItemDisplayElement();
			addElement(display);
			display.setBrightness(Brightness.FULL);
			display.setScale(new Vector3f(1.0004f));
		}

		public void updateItem() {
			display.setItem(getDisplayStack());
			display.tick();
		}

		@Nullable
		private BlockAwareAttachment getBlock() {
			return getAttachment() instanceof BlockAwareAttachment a ? a : null;
		}

		private ItemStack getDisplayStack() {
			var block = getBlock();
			if (block == null) {
				return ItemStack.EMPTY;
			}
			var blockEntity = block.getWorld().getBlockEntity(block.getBlockPos());
			if (blockEntity == null) {
				return ItemStack.EMPTY;
			}
			var model = blockEntity.getComponents().get(RelayComponents.BLOCK_MODEL);
			if (model != null) {
				return new ItemStack(
						Items.BARRIER.getRegistryEntry(), 1,
						ComponentChanges.builder().add(
								DataComponentTypes.ITEM_MODEL, model
						).add(
								DataComponentTypes.CUSTOM_MODEL_DATA,
								new CustomModelDataComponent(
										List.of(),
										List.of(block.getBlockState().get(RelayBlock.CHARGED)),
										List.of(),
										List.of()
								)
						).build()
				);
			} else {
				return ItemStack.EMPTY;
			}
		}

		@Override
		protected void onAttachmentSet(HolderAttachment attachment, @Nullable HolderAttachment oldAttachment) {
			super.onAttachmentSet(attachment, oldAttachment);
			if (attachment.getWorld() != null) {
				TaskScheduler.scheduleImmediately(
						attachment.getWorld().getServer(), this::updateItem
				);
			}
		}

		@Override
		public void notifyUpdate(HolderAttachment.UpdateType updateType) {
			super.notifyUpdate(updateType);
			if (updateType == BlockBoundAttachment.BLOCK_STATE_UPDATE) {
				updateItem();
			}
		}
	}
}
