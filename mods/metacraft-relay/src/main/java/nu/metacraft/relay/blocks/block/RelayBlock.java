package nu.metacraft.relay.blocks.block;

import com.mojang.serialization.MapCodec;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockAwareAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Brightness;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.relay.blocks.entity.RelayBlockEntity;
import nu.metacraft.relay.items.RelayComponents;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import nu.metacraft.lib.util.TaskScheduler;

import java.util.List;
import java.util.Optional;

public class RelayBlock extends Block implements PolymerBlock, EntityBlock, BlockWithElementHolder {

	public static final BooleanProperty CHARGED = BooleanProperty.create("charged");

	public RelayBlock(Properties settings) {
		super(settings);
		this.registerDefaultState(this.stateDefinition.any().setValue(CHARGED, false));
	}

	@Override
	public @Nullable ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState initialBlockState) {
		return new ModelHolder();
	}

	@Override
	protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
		super.onPlace(state, world, pos, oldState, notify);
		//Piston fix.
		//Attachment should have always been created at this point, but if it has not, we have the bug and fix it here.
		//We must check even if notify is false, as when a block is "spat out" by the piston this will be false even though the attachment will be missing.
		if (BlockBoundAttachment.get(world, pos) == null && world instanceof ServerLevel serverWorld) {
			new BlockBoundAttachment(
					createElementHolder(serverWorld, pos, state),
					world.getChunkAt(pos), state,
					pos.immutable(),
					Vec3.atCenterOf(pos).add(
							getElementHolderOffset(serverWorld, pos, state)
					),
					tickElementHolder(serverWorld, pos, state)
			);
		}
	}

	@Override
	public ElementHolder createMovingElementHolder(
			ServerLevel world, BlockPos pos, BlockState initialBlockState,
			@Nullable ElementHolder oldMovingElementHolder
	) { //Piston fix. The piston moving block entity is not given the attachment properly, so we return null instead.
		return null;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(CHARGED);
	}

	public static MutableComponent getTargetText(String targetPos, String targetDim) {
		return Component.translatableWithFallback(
				"block.metacraft.relay.target", "Target: " + targetPos + " in " + targetDim,
				targetPos, targetDim
		).withStyle(style -> style.applyFormat(ChatFormatting.GREEN));
	}

	@Override
	public BlockState getPolymerBlockState(BlockState blockState, @Nullable PacketContext packetContext) {
		return Blocks.STONE.defaultBlockState();
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new RelayBlockEntity(pos, state);
	}

	public static int getLightLevel(BlockState state, int maxLevel) {
		return state.getValue(CHARGED) ? maxLevel : 0;
	}

	@Override
	public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
		super.setPlacedBy(world, pos, state, placer, itemStack);
		var entity = world.getBlockEntity(pos);
		if (entity instanceof RelayBlockEntity e) {
			e.setComponents(itemStack.getComponents());
		}
	}

	private static boolean hasStillWater(BlockPos pos, Level world) {
		FluidState fluidState = world.getFluidState(pos);
		if (!fluidState.is(FluidTags.WATER)) {
			return false;
		} else if (fluidState.isSource()) {
			return true;
		} else {
			float f = (float)fluidState.getAmount();
			if (f < 2.0F) {
				return false;
			} else {
				FluidState fluidState2 = world.getFluidState(pos.below());
				return !fluidState2.is(FluidTags.WATER);
			}
		}
	}

	private void explode(BlockState state, Level world, BlockPos explodedPos) {
		world.removeBlock(explodedPos, false);
		boolean bl = Direction.Plane.HORIZONTAL.stream().map(explodedPos::relative).anyMatch(pos -> hasStillWater(pos, world));
		final boolean bl2 = bl || world.getFluidState(explodedPos.above()).is(FluidTags.WATER);
		ExplosionDamageCalculator explosionBehavior = new ExplosionDamageCalculator() {
			@Override
			public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter world, BlockPos pos, BlockState blockState, FluidState fluidState) {
				return pos.equals(explodedPos) && bl2
						? Optional.of(Blocks.WATER.getExplosionResistance())
						: super.getBlockExplosionResistance(explosion, world, pos, blockState, fluidState);
			}
		};
		Vec3 vec3d = Vec3.atCenterOf(explodedPos);
		world.explode(null, world.damageSources().badRespawnPointExplosion(vec3d), explosionBehavior, vec3d, 5.0F, true, Level.ExplosionInteraction.BLOCK);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
		var entity = world.getBlockEntity(pos);
		if (entity instanceof RelayBlockEntity e) {
			if (state.getValue(CHARGED)) {
				if (e.shouldExplode()) {
					explode(state, world, pos);
					return InteractionResult.SUCCESS_SERVER;
				}
				e.getTarget().resultOrPartial(
						err -> player.sendOverlayMessage(Component.literal(err).withStyle(
								style -> style.applyFormat(ChatFormatting.RED)
						))
				).ifPresent(
						target -> {
							player.teleport(target);
							world.setBlockAndUpdate(pos, state.setValue(CHARGED, false));
						}
				);
				return InteractionResult.SUCCESS_SERVER;
			}
		}
		return super.useWithoutItem(state, world, pos, player, hit);
	}

	public static boolean isChargeItem(ItemStack stack, Level world, BlockPos pos) {
		var blockEntity = world.getBlockEntity(pos);
		if (blockEntity != null) {
			var chargeItems = blockEntity.components().get(RelayComponents.VALID_CHARGE_ITEM);
			return chargeItems != null && stack.is(chargeItems);
		}
		return false;
	}

	private static boolean canCharge(BlockState state) {
		return !state.getValue(CHARGED);
	}

	private static void charge(Level world, BlockPos pos, BlockState state, @Nullable Entity charger) {
		world.setBlockAndUpdate(pos, state.setValue(CHARGED, true));
		world.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(charger, state));
		world.playSound(
				null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
				SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.0F, 1.0F
		);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (isChargeItem(stack, world, pos) && canCharge(state)) {
			charge(world, pos, state, player);
			stack.consume(1, player);
			return InteractionResult.SUCCESS_SERVER;
		}
		return super.useItemOn(stack, state, world, pos, player, hand, hit);
	}

	public static class RefillBehaviour extends OptionalDispenseItemBehavior {

		private final DispenseItemBehavior fallback;

		public RefillBehaviour(DispenseItemBehavior fallback) {
			this.fallback = fallback;
		}

		@Override
		protected ItemStack execute(BlockSource pointer, ItemStack stack) {
			Direction direction = pointer.state().getValue(DispenserBlock.FACING);
			BlockPos targetPos = pointer.pos().relative(direction);
			var targetState = pointer.level().getBlockState(targetPos);
			if (RelayBlock.isChargeItem(stack, pointer.level(), targetPos)) {
				if (canCharge(targetState)) {
					charge(pointer.level(), targetPos, targetState, null);
					stack.shrink(1);
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
			display.setBrightness(Brightness.FULL_BRIGHT);
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
			var model = blockEntity.components().get(RelayComponents.BLOCK_MODEL);
			if (model != null) {
				return new ItemStack(
						Items.BARRIER.builtInRegistryHolder(), 1,
						DataComponentPatch.builder().set(
								DataComponents.ITEM_MODEL, model
						).set(
								DataComponents.CUSTOM_MODEL_DATA,
								new CustomModelData(
										List.of(),
										List.of(block.getBlockState().getValue(RelayBlock.CHARGED)),
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
