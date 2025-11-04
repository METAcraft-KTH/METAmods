package nu.metacraft.simplecustomfeatures.objects.blocks.target_portal;

import com.google.common.base.Predicates;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.block.state.pattern.BlockPatternBuilder;
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate;
import net.minecraft.world.phys.BlockHitResult;
import nu.metacraft.lib.compat.IsLoaded;
import nu.metacraft.simplecustomfeatures.Features;
import nu.metacraft.simplecustomfeatures.compat.PortalBlockerCompat;
import xyz.nucleoid.packettweaker.PacketContext;

public class TargetPortalFrameBlock extends EndPortalFrameBlock implements PolymerBlock {

	private final BlockPattern pattern = BlockPatternBuilder.start().aisle(
			"?vvv?", ">???<", ">???<", ">???<", "?^^^?"
	).where(
			'?', BlockInWorld.hasState(BlockStatePredicate.ANY)
	).where(
			'^', BlockInWorld.hasState(
					BlockStatePredicate.forBlock(this).where(
							HAS_EYE, Predicates.equalTo(true)
					).where(FACING, Predicates.equalTo(Direction.SOUTH))
			)
	).where(
			'>', BlockInWorld.hasState(
					BlockStatePredicate.forBlock(this).where(
							HAS_EYE, Predicates.equalTo(true)
					).where(FACING, Predicates.equalTo(Direction.WEST))
			)
	).where(
			'v', BlockInWorld.hasState(
					BlockStatePredicate.forBlock(this).where(
							HAS_EYE, Predicates.equalTo(true)
					).where(FACING, Predicates.equalTo(Direction.NORTH))
			)
	).where(
			'<', BlockInWorld.hasState(
					BlockStatePredicate.forBlock(this).where(
							HAS_EYE, Predicates.equalTo(true)
					).where(FACING, Predicates.equalTo(Direction.EAST))
			)
	).build();

	private final TargetPortalFrameObject frame;

	public TargetPortalFrameBlock(Properties settings, TargetPortalFrameObject frame) {
		super(settings);
		this.frame = frame;
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext ctx) {
		return Blocks.END_PORTAL_FRAME.defaultBlockState().setValue(FACING, state.getValue(FACING)).setValue(HAS_EYE, state.getValue(HAS_EYE));
	}

	@Override
	protected InteractionResult useItemOn(
			ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit
	) {
		if (!state.getValue(HAS_EYE) && frame.activator().test(stack)) {
			if (IsLoaded.PORTAL_BLOCKER.isLoaded() && !world.isClientSide()) {
				var reference = frame.portalReference();
				if (reference.isPresent()) {
					if (PortalBlockerCompat.isActivationBlocked(
							reference.get(), world.getServer(), world.dimension(), pos
					)) {
						return InteractionResult.FAIL;
					}
				} else {
					Features.LOGGER.warn("Portal frame " + this + " does not have a valid portal reference, and therefore cannot be blocked by portal blocker!");
				}
			}
			BlockState activated = state.setValue(EndPortalFrameBlock.HAS_EYE, true);
			Block.pushEntitiesUp(state, activated, world, pos);
			world.setBlock(pos, activated, Block.UPDATE_CLIENTS);
			world.updateNeighbourForOutputSignal(pos, Blocks.END_PORTAL_FRAME);
			if (stack.isDamageableItem()) {
				stack.hurtAndBreak(1, player, hand == InteractionHand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND);
			} else {
				stack.consume(1, player);
			}
			world.levelEvent(LevelEvent.END_PORTAL_FRAME_FILL, pos, 0);

			var portalResult = pattern.find(world, pos);
			if (portalResult != null) {
				var bottom = portalResult.getFrontTopLeft().offset(-3, 0, -3);
				for (var portalPos : BlockPos.betweenClosed(bottom, new BlockPos.MutableBlockPos().set(bottom).move(2, 0, 2))) {
					world.setBlockAndUpdate(portalPos, frame.portalBlock().getState(world.getRandom(), portalPos));
				}
				world.globalLevelEvent(LevelEvent.SOUND_END_PORTAL_SPAWN, bottom.offset(1, 0, 1), 0);
			}
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}
}
