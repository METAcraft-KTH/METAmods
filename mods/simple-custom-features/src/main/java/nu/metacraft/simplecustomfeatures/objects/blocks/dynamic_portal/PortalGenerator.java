package nu.metacraft.simplecustomfeatures.objects.blocks.dynamic_portal;

import nu.metacraft.lib.compat.IsLoaded;
import nu.metacraft.simplecustomfeatures.compat.PortalBlockerCompat;
import nu.metacraft.simplecustomfeatures.mixin.PortalForcerAccessor;

import java.util.Optional;
import net.minecraft.util.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * Basically just a blatant copy of {@link net.minecraft.world.level.portal.PortalForcer},
 * but with some extra config options.
 */
public class PortalGenerator {

	private final Portal portal;
	private final int height;
	private final int width;
	private final Rotation rotation;
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
			rotation = Rotation.NONE;
		} else if (portalStructure.structure().getSize().getX() > portalStructure.structure().getSize().getZ()) {
			rotation = switch (axis) {
				case X -> Rotation.NONE;
				case Z -> Rotation.CLOCKWISE_90;
				case Y -> throw new IllegalStateException("Vertical portals are not supported");
			};
		} else {
			rotation = switch (axis) {
				case X -> Rotation.COUNTERCLOCKWISE_90;
				case Z -> Rotation.NONE;
				case Y -> throw new IllegalStateException("Vertical portals are not supported");
			};
		}
		this.portalStructure = portalStructure;
		this.portalWithPlatformStructure = portalWithPlatformStructure;
		this.width = Math.max(portalStructure.structure().getSize().getX(), portalStructure.structure().getSize().getZ());
		this.height = portalStructure.structure().getSize().getY();
		this.axis = axis;
	}

	public Optional<BlockUtil.FoundRectangle> createPortal(
			ServerLevel targetWorld, BlockPos targetPos
	) {
		int maxY = Math.min(targetWorld.getMaxY(), targetWorld.getMinY() + targetWorld.getLogicalHeight()) - 1;
		var forcer = (PortalForcerAccessor) targetWorld.getPortalForcer();
		var border = targetWorld.getWorldBorder();
		var positive = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
		boolean foundSafePos = false;
		BlockPos foundPos = null;
		double leastFoundDist = Double.POSITIVE_INFINITY;
		var invOffset = getOffsetInverted(portalStructure);
		for (var pos : BlockPos.spiralAround(targetPos, 16, Direction.EAST, Direction.SOUTH)) {
			if (!border.isWithinBounds(pos) || !border.isWithinBounds(pos.move(positive))) continue;
			pos.move(positive.getOpposite());
			int yGround = Math.min(maxY, targetWorld.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()));
			for (int topY = yGround; topY > targetWorld.getMinY(); topY--) {
				pos.setY(topY);
				if (!forcer.callCanPortalReplaceBlock(pos)) {
					continue;
				}
				int bottomY = topY;
				while (bottomY > targetWorld.getMinY() && forcer.callCanPortalReplaceBlock(pos.move(Direction.DOWN))) {
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
				var dist = targetPos.distSqr(pos);
				if (isValidPortalPos(targetWorld, pos, invOffset, 1) && isValidPortalPos(targetWorld, pos, invOffset, -1)) {
					if (!foundSafePos || leastFoundDist > dist) {
						foundSafePos = true;
						foundPos = pos.immutable();
						leastFoundDist = dist;
					}
				} else if (!foundSafePos && leastFoundDist > dist) {
					leastFoundDist = dist;
					foundPos = pos.immutable();
				}
			}
		}

		if (!foundSafePos) {
			int lowestPossiblePortalPos = Math.min(targetWorld.getMinY() + 4, 70);
			int highestPossiblePortalPos = maxY - height - 1;
			if (lowestPossiblePortalPos > highestPossiblePortalPos) {
				return Optional.empty();
			}
			foundPos = border.clampToBounds(new BlockPos(
					targetPos.getX(),
					Mth.clamp(targetPos.getY(), lowestPossiblePortalPos, highestPossiblePortalPos),
					targetPos.getZ()
			));
			if (IsLoaded.PORTAL_BLOCKER.isLoaded()) {
				var minPos = getMinFramePos(foundPos, invOffset);
				if (PortalBlockerCompat.isGenerationBlocked(
						portal, targetWorld.getServer(), targetWorld.dimension(),
						BlockPos.betweenClosed(minPos, getMaxFramePos(minPos.mutable()))
				)) {
					return Optional.empty();
				}
			}
			portalWithPlatformStructure.structure().placeInWorld(
					targetWorld, foundPos.subtract(portalWithPlatformStructure.offset()), portalWithPlatformStructure.offset(),
					new StructurePlaceSettings().setRotationPivot(portalWithPlatformStructure.offset()).setRotation(rotation).setKnownShape(true),
					targetWorld.getRandom(), Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS
			);
		} else {
			portalStructure.structure().placeInWorld(
					targetWorld, foundPos.subtract(portalStructure.offset()), portalStructure.offset(),
					new StructurePlaceSettings().setRotationPivot(portalStructure.offset()).setRotation(rotation).setKnownShape(true),
					targetWorld.getRandom(), Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS
			);
		}

		int portalWidth;
		if (portalStructure.structure().getSize().getX() >= portalStructure.structure().getSize().getZ()) {
			portalWidth = width - portalStructure.offset().getX()*2;
		} else {
			portalWidth = width - portalStructure.offset().getZ()*2;
		}

		return Optional.of(new BlockUtil.FoundRectangle(foundPos, portalWidth, height - portalStructure.offset().getY()));
	}

	private boolean isValidPortalPos(ServerLevel world, BlockPos portalPos, BlockPos invertedOffset, int outDistance) {
		var facing = Direction.get(Direction.AxisDirection.POSITIVE, axis).getClockWise();
		var minPos = getMinFramePos(portalPos, invertedOffset).move(facing, outDistance);

		if (IsLoaded.PORTAL_BLOCKER.isLoaded()) {
			if (PortalBlockerCompat.isGenerationBlocked(
					portal, world.getServer(), world.dimension(),
					BlockPos.betweenClosed(minPos, getMaxFramePos(minPos.mutable()))
			)) {
				return false;
			}
		}

		for (BlockPos pos : BlockPos.betweenClosed(minPos, getBottomMaxFramePos(minPos.mutable()))) {
			if (!world.getBlockState(pos).isSolid()) {
				return false;
			}
		}
		var forcer = (PortalForcerAccessor) world.getPortalForcer();
		for (BlockPos pos : BlockPos.betweenClosed(minPos.mutable().move(Direction.UP), getMaxFramePos(minPos))) {
			if (!forcer.callCanPortalReplaceBlock((BlockPos.MutableBlockPos) pos)) {
				return false;
			}
		}
		return true;
	}

	public BlockPos getOffset(PortalBlockObject.ValidStructureWithOffset structure) {
		var offset = new BlockPos.MutableBlockPos().set(structure.offset().rotate(rotation));
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

	public BlockPos.MutableBlockPos getMinFramePos(BlockPos target, BlockPos invOffset) {
		return new BlockPos.MutableBlockPos().set(target).move(invOffset);
	}

	public BlockPos.MutableBlockPos getMaxFramePos(BlockPos.MutableBlockPos minFramePos) {
		return getBottomMaxFramePos(minFramePos).move(Direction.UP, height-1);
	}

	public BlockPos.MutableBlockPos getMaxPortalPos(BlockPos.MutableBlockPos minFramePos, BlockPos invOffset) {
		return getMaxFramePos(minFramePos).move(invOffset);
	}

	public BlockPos.MutableBlockPos getBottomMaxFramePos(BlockPos.MutableBlockPos minFramePos) {
		return minFramePos.move(
				Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE), width-1
		);
	}

}
