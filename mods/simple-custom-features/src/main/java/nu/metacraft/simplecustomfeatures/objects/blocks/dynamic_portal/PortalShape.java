package nu.metacraft.simplecustomfeatures.objects.blocks.dynamic_portal;

import nu.metacraft.lib.compat.IsLoaded;
import nu.metacraft.simplecustomfeatures.Features;
import nu.metacraft.simplecustomfeatures.compat.PortalBlockerCompat;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ArrayListDeque;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;

public class PortalShape {

	private final LevelReader world;
	private final DynamicPortalBlock portal;
	private final Direction.Axis axis;
	private final Set<Direction> directions;

	private boolean valid = false;

	private final Queue<BlockPos> positionsToSearchNext = new ArrayListDeque<>();
	private final Set<BlockPos> exploredPositions = new HashSet<>();
	private final Set<BlockPos> insidePortal = new HashSet<>();

	public static Optional<PortalShape> findPortalShape(
			ServerLevel world, BlockPos pos, DynamicPortalBlock block
	) {
		return findPortalShape(world, pos, block, Direction.Axis.X);
	}

	public static Optional<PortalShape> findPortalShape(
			LevelReader world, BlockPos pos, DynamicPortalBlock block, Direction.Axis axis
	) {
		if (world.getBlockState(pos).is(block)) {
			return Optional.empty();
		}
		var portal = new PortalShape(world, pos, block, axis);
		if (portal.isValid()) return Optional.of(portal);
		portal = new PortalShape(world, pos, block, axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
		if (portal.isValid()) return Optional.of(portal);
		return Optional.empty();
	}

	public PortalShape(LevelReader world, BlockPos pos, DynamicPortalBlock portal, Direction.Axis axis) {
		if (axis.isVertical()) throw new IllegalArgumentException("Vertical portals are not supported.");
		this.world = world;
		this.portal = portal;
		this.axis = axis;
		this.directions = EnumSet.of(
				Direction.UP, Direction.DOWN,
				Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE),
				Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE)
		);
		findPortalShape(pos);
	}

	public boolean isValid() {
		return valid;
	}

	public void activate(LevelAccessor world) {
		var state = portal.defaultBlockState().setValue(NetherPortalBlock.AXIS, axis);
		insidePortal.forEach(pos -> {
			world.setBlock(pos, state, Block.UPDATE_ALL);
		});
	}

	private void findPortalShape(BlockPos startPos) {
		exploredPositions.add(startPos);
		positionsToSearchNext.add(startPos);
		while (!positionsToSearchNext.isEmpty()) {
			var pos = positionsToSearchNext.poll();
			if (pos.distSqr(startPos) > Math.pow(portal.getMaxPortalSideLength(), 2)) {
				return;
			}
			BlockInWorld state = new BlockInWorld(world, pos, true);
			if (portal.isValidStateInsidePortal(state)) {
				insidePortal.add(pos);
			}
			if (portal.isFrameBlock(state)) {
				continue;
			}
			if (insidePortal.size() > Math.pow(portal.getMaxPortalSideLength(), 2)) {
				return;
			}
			searchNeighbours(pos);
		}
		if (insidePortal.size() < portal.getPortal().getMinArea()) {
			return;
		}
		if (IsLoaded.PORTAL_BLOCKER.isLoaded()) {
			if (world instanceof Level w && !w.isClientSide()) {
				if (PortalBlockerCompat.isActivationBlocked(
						portal.getPortal().getBlock(), w.getServer(), w.dimension(), insidePortal
				)) {
					return;
				}
			} else {
				Features.LOGGER.warn(
						"Portal created in non-server world. Some mod you have installed might allow players to bypass portal-blocker!"
				);
			}
		}
		valid = true;
	}

	private void searchNeighbours(BlockPos pos) {
		for (var direction : directions) {
			var target = pos.relative(direction);
			if (!exploredPositions.contains(target)) {
				exploredPositions.add(target);
				positionsToSearchNext.add(target);
			}
		}
	}

}
