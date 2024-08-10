package se.datasektionen.mc.simplecustomfeatures.objects.blocks.target_portal;

import com.google.common.base.Predicates;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.EndPortalFrameBlock;
import net.minecraft.block.pattern.BlockPattern;
import net.minecraft.block.pattern.BlockPatternBuilder;
import net.minecraft.block.pattern.CachedBlockPosition;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.predicate.block.BlockStatePredicate;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import se.datasektionen.mc.metacraft_lib.compat.IsLoaded;
import se.datasektionen.mc.simplecustomfeatures.Features;
import se.datasektionen.mc.simplecustomfeatures.compat.PortalBlockerCompat;

public class TargetPortalFrameBlock extends EndPortalFrameBlock implements PolymerBlock {

	private final BlockPattern pattern = BlockPatternBuilder.start().aisle(
			"?vvv?", ">???<", ">???<", ">???<", "?^^^?"
	).where(
			'?', CachedBlockPosition.matchesBlockState(BlockStatePredicate.ANY)
	).where(
			'^', CachedBlockPosition.matchesBlockState(
					BlockStatePredicate.forBlock(this).with(
							EYE, Predicates.equalTo(true)
					).with(FACING, Predicates.equalTo(Direction.SOUTH))
			)
	).where(
			'>', CachedBlockPosition.matchesBlockState(
					BlockStatePredicate.forBlock(this).with(
							EYE, Predicates.equalTo(true)
					).with(FACING, Predicates.equalTo(Direction.WEST))
			)
	).where(
			'v', CachedBlockPosition.matchesBlockState(
					BlockStatePredicate.forBlock(this).with(
							EYE, Predicates.equalTo(true)
					).with(FACING, Predicates.equalTo(Direction.NORTH))
			)
	).where(
			'<', CachedBlockPosition.matchesBlockState(
					BlockStatePredicate.forBlock(this).with(
							EYE, Predicates.equalTo(true)
					).with(FACING, Predicates.equalTo(Direction.EAST))
			)
	).build();

	private final TargetPortalFrameObject frame;

	public TargetPortalFrameBlock(Settings settings, TargetPortalFrameObject frame) {
		super(settings);
		this.frame = frame;
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state) {
		return Blocks.END_PORTAL_FRAME.getDefaultState().with(FACING, state.get(FACING)).with(EYE, state.get(EYE));
	}

	@Override
	protected ItemActionResult onUseWithItem(
			ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit
	) {
		if (!state.get(EYE) && frame.getActivator().test(stack)) {
			if (IsLoaded.PORTAL_BLOCKER.isLoaded() && !world.isClient()) {
				var reference = frame.getPortalReference();
				if (reference.isPresent()) {
					if (PortalBlockerCompat.isCreationBlocked(
							reference.get(), world.getServer(), world.getRegistryKey(), pos
					)) {
						return ItemActionResult.FAIL;
					}
				} else {
					Features.LOGGER.warn("Portal frame " + this + " does not have a valid portal reference, and therefore cannot be blocked by portal blocker!");
				}
			}
			BlockState activated = state.with(EndPortalFrameBlock.EYE, true);
			Block.pushEntitiesUpBeforeBlockChange(state, activated, world, pos);
			world.setBlockState(pos, activated, Block.NOTIFY_LISTENERS);
			world.updateComparators(pos, Blocks.END_PORTAL_FRAME);
			if (stack.isDamageable()) {
				stack.damage(1, player, hand == Hand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND);
			} else {
				stack.decrementUnlessCreative(1, player);
			}
			world.syncWorldEvent(WorldEvents.END_PORTAL_FRAME_FILLED, pos, 0);

			var portalResult = pattern.searchAround(world, pos);
			if (portalResult != null) {
				var bottom = portalResult.getFrontTopLeft().add(-3, 0, -3);
				for (var portalPos : BlockPos.iterate(bottom, new BlockPos.Mutable().set(bottom).move(2, 0, 2))) {
					world.setBlockState(portalPos, frame.getPortalBlock().get(world.getRandom(), portalPos));
				}
				world.syncGlobalEvent(WorldEvents.END_PORTAL_OPENED, bottom.add(1, 0, 1), 0);
			}
			return ItemActionResult.SUCCESS;
		}
		return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}
}
