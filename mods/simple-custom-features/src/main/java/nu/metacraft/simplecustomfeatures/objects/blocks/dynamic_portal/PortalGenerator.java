package nu.metacraft.simplecustomfeatures.objects.blocks.dynamic_portal;

import net.minecraft.block.Block;
import net.minecraft.block.Portal;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.BlockLocating;
import net.minecraft.world.Heightmap;
import nu.metacraft.lib.compat.IsLoaded;
import nu.metacraft.simplecustomfeatures.compat.PortalBlockerCompat;
import nu.metacraft.simplecustomfeatures.mixin.AccessorPortalForcer;

import java.util.Optional;

/**
 * Basically just a blatant copy of {@link net.minecraft.world.dimension.PortalForcer},
 * but with some extra config options.
 */
public class PortalGenerator {

	private final Portal portal;
	private final int height;
	private final int width;
	private final BlockRotation rotation;
	private final Direction.Axis axis;
	private final PortalBlockObject.ValidStructureWithOffset portalStructure;
	private final PortalBlockObject.ValidStructureWithOffset portalWithPlatformStructure;

	public PortalGenerator(
			Portal portal,
			PortalBlockObject.ValidStructureWithOffset portalStructure,
			PortalBlockObject.ValidStructureWithOffset portalWithPlatformStructure,
			Direction.Axis axis
	) {
		this.portal = portal;
		if (portalStructure.structure().getSize().getX() == portalStructure.structure().getSize().getZ()) {
			rotation = BlockRotation.NONE;
		} else if (portalStructure.structure().getSize().getX() > portalStructure.structure().getSize().getZ()) {
			rotation = switch (axis) {
				case X -> BlockRotation.NONE;
				case Z -> BlockRotation.CLOCKWISE_90;
				case Y -> throw new IllegalStateException("Vertical portals are not supported");
			};
		} else {
			rotation = switch (axis) {
				case X -> BlockRotation.COUNTERCLOCKWISE_90;
				case Z -> BlockRotation.NONE;
				case Y -> throw new IllegalStateException("Vertical portals are not supported");
			};
		}
		this.portalStructure = portalStructure;
		this.portalWithPlatformStructure = portalWithPlatformStructure;
		this.width = Math.max(portalStructure.structure().getSize().getX(), portalStructure.structure().getSize().getZ());
		this.height = portalStructure.structure().getSize().getY();
		this.axis = axis;
	}

	public Optional<BlockLocating.Rectangle> createPortal(
			ServerWorld targetWorld, BlockPos targetPos
	) {
		int maxY = Math.min(targetWorld.getTopYInclusive(), targetWorld.getBottomY() + targetWorld.getLogicalHeight()) - 1;
		var forcer = (AccessorPortalForcer) targetWorld.getPortalForcer();
		var border = targetWorld.getWorldBorder();
		var positive = Direction.from(axis, Direction.AxisDirection.POSITIVE);
		boolean foundSafePos = false;
		BlockPos foundPos = null;
		double leastFoundDist = Double.POSITIVE_INFINITY;
		var invOffset = getOffsetInverted(portalStructure);
		for (var pos : BlockPos.iterateInSquare(targetPos, 16, Direction.EAST, Direction.SOUTH)) {
			if (!border.contains(pos) || !border.contains(pos.move(positive))) continue;
			pos.move(positive.getOpposite());
			int yGround = Math.min(maxY, targetWorld.getTopY(Heightmap.Type.MOTION_BLOCKING, pos.getX(), pos.getZ()));
			for (int topY = yGround; topY > targetWorld.getBottomY(); topY--) {
				pos.setY(topY);
				if (!forcer.callIsBlockStateValid(pos)) {
					continue;
				}
				int bottomY = topY;
				while (bottomY > targetWorld.getBottomY() && forcer.callIsBlockStateValid(pos.move(Direction.DOWN))) {
					bottomY--;
				}
				if (topY + height - 1 > yGround) {
					continue;
				}
				var foundHeight = topY - bottomY;
				if (foundHeight > 0 && foundHeight < height-2) {
					continue;
				}
				if (!isValidPortalPos(targetWorld, pos.setY(bottomY), invOffset, 0)) {
					continue;
				}
				var dist = targetPos.getSquaredDistance(pos);
				if (isValidPortalPos(targetWorld, pos, invOffset, 1) && isValidPortalPos(targetWorld, pos, invOffset, -1)) {
					if (!foundSafePos || leastFoundDist > dist) {
						foundSafePos = true;
						foundPos = pos.toImmutable();
						leastFoundDist = dist;
					}
				} else if (!foundSafePos && leastFoundDist > dist) {
					leastFoundDist = dist;
					foundPos = pos.toImmutable();
				}
			}
		}

		if (!foundSafePos) {
			int lowestPossiblePortalPos = Math.min(targetWorld.getBottomY() + 4, 70);
			int highestPossiblePortalPos = maxY - height - 1;
			if (lowestPossiblePortalPos > highestPossiblePortalPos) {
				return Optional.empty();
			}
			foundPos = border.clampFloored(new BlockPos(
					targetPos.getX(),
					MathHelper.clamp(targetPos.getY(), lowestPossiblePortalPos, highestPossiblePortalPos),
					targetPos.getZ()
			));
			if (IsLoaded.PORTAL_BLOCKER.isLoaded()) {
				var minPos = getMinFramePos(foundPos, invOffset);
				if (PortalBlockerCompat.isGenerationBlocked(
						portal, targetWorld.getServer(), targetWorld.getRegistryKey(),
						BlockPos.iterate(minPos, getMaxFramePos(minPos.mutableCopy()))
				)) {
					return Optional.empty();
				}
			}
			portalWithPlatformStructure.structure().place(
					targetWorld, foundPos.subtract(portalWithPlatformStructure.offset()), portalWithPlatformStructure.offset(),
					new StructurePlacementData().setPosition(portalWithPlatformStructure.offset()).setRotation(rotation).setUpdateNeighbors(true),
					targetWorld.getRandom(), Block.FORCE_STATE | Block.NOTIFY_LISTENERS
			);
		} else {
			portalStructure.structure().place(
					targetWorld, foundPos.subtract(portalStructure.offset()), portalStructure.offset(),
					new StructurePlacementData().setPosition(portalStructure.offset()).setRotation(rotation).setUpdateNeighbors(true),
					targetWorld.getRandom(), Block.FORCE_STATE | Block.NOTIFY_LISTENERS
			);
		}

		int portalWidth;
		if (portalStructure.structure().getSize().getX() >= portalStructure.structure().getSize().getZ()) {
			portalWidth = width - portalStructure.offset().getX()*2;
		} else {
			portalWidth = width - portalStructure.offset().getZ()*2;
		}

		return Optional.of(new BlockLocating.Rectangle(foundPos, portalWidth, height - portalStructure.offset().getY()));
	}

	private boolean isValidPortalPos(ServerWorld world, BlockPos portalPos, BlockPos invertedOffset, int outDistance) {
		var facing = Direction.get(Direction.AxisDirection.POSITIVE, axis).rotateYClockwise();
		var minPos = getMinFramePos(portalPos, invertedOffset).move(facing, outDistance);

		if (IsLoaded.PORTAL_BLOCKER.isLoaded()) {
			if (PortalBlockerCompat.isGenerationBlocked(
					portal, world.getServer(), world.getRegistryKey(),
					BlockPos.iterate(minPos, getMaxFramePos(minPos.mutableCopy()))
			)) {
				return false;
			}
		}

		for (BlockPos pos : BlockPos.iterate(minPos, getBottomMaxFramePos(minPos.mutableCopy()))) {
			if (!world.getBlockState(pos).isSolid()) {
				return false;
			}
		}
		var forcer = (AccessorPortalForcer) world.getPortalForcer();
		for (BlockPos pos : BlockPos.iterate(minPos.mutableCopy().move(Direction.UP), getMaxFramePos(minPos))) {
			if (!forcer.callIsBlockStateValid((BlockPos.Mutable) pos)) {
				return false;
			}
		}
		return true;
	}

	public BlockPos getOffset(PortalBlockObject.ValidStructureWithOffset structure) {
		var offset = new BlockPos.Mutable().set(structure.offset().rotate(rotation));
		if (offset.getX() < 0) {
			offset.setX(-offset.getX());
		}
		if (offset.getZ() < 0) {
			offset.setZ(-offset.getZ());
		}
		return offset;
	}

	public BlockPos getOffsetInverted(PortalBlockObject.ValidStructureWithOffset structure) {
		return getOffset(structure).multiply(-1);
	}

	public BlockPos.Mutable getMinFramePos(BlockPos target, BlockPos invOffset) {
		return new BlockPos.Mutable().set(target).move(invOffset);
	}

	public BlockPos.Mutable getMaxFramePos(BlockPos.Mutable minFramePos) {
		return getBottomMaxFramePos(minFramePos).move(Direction.UP, height-1);
	}

	public BlockPos.Mutable getMaxPortalPos(BlockPos.Mutable minFramePos, BlockPos invOffset) {
		return getMaxFramePos(minFramePos).move(invOffset);
	}

	public BlockPos.Mutable getBottomMaxFramePos(BlockPos.Mutable minFramePos) {
		return minFramePos.move(
				Direction.from(axis, Direction.AxisDirection.POSITIVE), width-1
		);
	}

}
