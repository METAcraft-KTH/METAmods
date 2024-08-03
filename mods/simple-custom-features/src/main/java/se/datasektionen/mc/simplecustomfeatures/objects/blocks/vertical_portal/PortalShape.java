package se.datasektionen.mc.simplecustomfeatures.objects.blocks.vertical_portal;

import net.minecraft.block.Block;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.block.pattern.CachedBlockPosition;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.ArrayListDeque;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;

import java.util.*;

public class PortalShape {

	private final WorldAccess world;
	private final VerticalPortalBlock portal;
	private final Direction.Axis axis;
	private final Set<Direction> directions;

	private boolean valid = false;

	private final Queue<BlockPos> positionsToSearchNext = new ArrayListDeque<>();
	private final Set<BlockPos> exploredPositions = new HashSet<>();
	private final Set<BlockPos> insidePortal = new HashSet<>();

	public static Optional<PortalShape> findPortalShape(
			ServerWorld world, BlockPos pos, VerticalPortalBlock block
	) {
		return findPortalShape(world, pos, block, Direction.Axis.X);
	}

	public static Optional<PortalShape> findPortalShape(
			WorldAccess world, BlockPos pos, VerticalPortalBlock block, Direction.Axis axis
	) {
		if (world.getBlockState(pos).isOf(block)) {
			return Optional.empty();
		}
		var portal = new PortalShape(world, pos, block, axis);
		if (portal.isValid()) return Optional.of(portal);
		portal = new PortalShape(world, pos, block, axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
		if (portal.isValid()) return Optional.of(portal);
		return Optional.empty();
	}

	public PortalShape(WorldAccess world, BlockPos pos, VerticalPortalBlock portal, Direction.Axis axis) {
		if (axis.isVertical()) throw new IllegalArgumentException("Horizontal portals are not supported.");
		this.world = world;
		this.portal = portal;
		this.axis = axis;
		this.directions = EnumSet.of(
				Direction.UP, Direction.DOWN,
				Direction.from(axis, Direction.AxisDirection.POSITIVE),
				Direction.from(axis, Direction.AxisDirection.NEGATIVE)
		);
		findPortalShape(pos);
	}

	public boolean isValid() {
		return valid;
	}

	public void activate() {
		var state = portal.getDefaultState().with(NetherPortalBlock.AXIS, axis);
		insidePortal.forEach(pos -> {
			world.setBlockState(pos, state, Block.NOTIFY_ALL);
		});
	}

	private void findPortalShape(BlockPos startPos) {
		exploredPositions.add(startPos);
		positionsToSearchNext.add(startPos);
		while (!positionsToSearchNext.isEmpty()) {
			var pos = positionsToSearchNext.poll();
			if (pos.getSquaredDistance(startPos) > Math.pow(portal.getMaxSize(), 2)) {
				return;
			}
			CachedBlockPosition state = new CachedBlockPosition(world, pos, true);
			if (portal.isValidStateInsidePortal(state)) {
				insidePortal.add(pos);
			}
			if (portal.isFrameBlock(state)) {
				continue;
			}
			if (insidePortal.size() > Math.pow(portal.getMaxSize(), 2)) {
				return;
			}
			searchNeighbours(pos);
		}
		if (insidePortal.size() < portal.getPortal().getMinArea()) {
			return;
		}
		valid = true;
	}

	private void searchNeighbours(BlockPos pos) {
		for (var direction : directions) {
			var target = pos.offset(direction);
			if (!exploredPositions.contains(target)) {
				exploredPositions.add(target);
				positionsToSearchNext.add(target);
			}
		}
	}

}
