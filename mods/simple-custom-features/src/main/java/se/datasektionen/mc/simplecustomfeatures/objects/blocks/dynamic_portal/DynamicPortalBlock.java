package se.datasektionen.mc.simplecustomfeatures.objects.blocks.dynamic_portal;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.block.pattern.CachedBlockPosition;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockLocating;
import net.minecraft.world.GameRules;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.poi.PointOfInterest;
import net.minecraft.world.poi.PointOfInterestStorage;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityHelper;
import se.datasektionen.mc.simplecustomfeatures.Features;
import se.datasektionen.mc.simplecustomfeatures.mixin.AccessorNetherPortalBlock;

import java.util.Comparator;
import java.util.Optional;

public class DynamicPortalBlock extends NetherPortalBlock implements PolymerBlock {

	private final PortalBlockObject portal;

	public DynamicPortalBlock(AbstractBlock.Settings settings, PortalBlockObject portal) {
		super(settings);
		this.portal = portal;
	}

	@Override
	protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		if (world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING)) {
			var spawns = portal.getEntitySpawns().get(world.getRegistryKey());
			if (spawns != null && random.nextDouble() <= spawns.spawnChance()) {
				spawns.entities().getDataOrEmpty(random).ifPresent(entityData -> {
					var spawnPos = new BlockPos.Mutable().set(pos);
					var type = EntityType.fromNbt(entityData).orElse(null);
					if (SpawnRestriction.getLocation(type) != SpawnLocationTypes.UNRESTRICTED) {
						BlockPos.Mutable blockBelowChecker = new BlockPos.Mutable().set(pos);
						while (world.getBlockState(blockBelowChecker).isOf(this) && blockBelowChecker.getY() > world.getBottomY()) {
							blockBelowChecker.move(Direction.DOWN);
						}
						if (!world.getBlockState(blockBelowChecker).isSolid()) {
							return;
						}
						spawnPos.set(blockBelowChecker.move(Direction.UP));
					}
					var center = Vec3d.ofBottomCenter(spawnPos);
					var rootEntity = EntityHelper.loadEntityWithPassengers(entityData, world, (entity, data) -> {
						entity.resetPortalCooldown();
						if (entity instanceof MobEntity mob && spawns.initialize()) {
							mob.initialize(world, world.getLocalDifficulty(spawnPos), SpawnReason.STRUCTURE, null);
							if (data.getSize() > 1) {
								mob.readNbt(data);
							}
						}
						entity.setPosition(center);
						return entity;
					});
					rootEntity.ifPresent(world::spawnEntityAndPassengers);
				});
			}
		}
	}

	public PortalBlockObject getPortal() {
		return portal;
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		var axis = state.get(AXIS);
		var isIrrelevant = direction.getAxis().isHorizontal() && axis != direction.getAxis();
		if (isIrrelevant || neighborState.isOf(this) || findPortalShape(world, pos, axis).isPresent()) {
			return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
		}
		return Blocks.AIR.getDefaultState();
	}

	public Optional<PortalShape> findPortalShape(
			ServerWorld world, BlockPos pos
	) {
		return PortalShape.findPortalShape(world, pos, this);
	}

	public Optional<PortalShape> findPortalShape(WorldAccess world, BlockPos pos, Direction.Axis axis) {
		return PortalShape.findPortalShape(world, pos, this, axis);
	}

	public boolean isValidStateInsidePortal(CachedBlockPosition state) {
		return state.getBlockState().isAir() || state.getBlockState().isOf(this) || portal.getBlockActivator().map(activator -> activator.test(state)).orElse(false);
	}

	public boolean isFrameBlock(CachedBlockPosition state) {
		return portal.getValidFrameBlock().test(state);
	}

	public int getMaxPortalSideLength() {
		return 32;
	}

	@Nullable
	@Override
	public TeleportTarget createTeleportTarget(ServerWorld world, Entity entity, BlockPos pos) {
		var targetDim = portal.getTargetDim(world.getRegistryKey());
		if (targetDim == null) return null;
		var targetWorld = world.getServer().getWorld(targetDim);
		if (targetWorld == null) return null;
		var factor = DimensionType.getCoordinateScaleFactor(world.getDimension(), targetWorld.getDimension());
		BlockPos targetPos = targetWorld.getWorldBorder().clamp(pos.getX() * factor, pos.getY(), pos.getZ() * factor);
		return getPortalTarget(entity, targetWorld, targetPos, pos);
	}

	private static void log(Identifier id) {
		Features.LOGGER.error(
				"{} is not a valid structure, portal generation cancelled.", id
		);
	}

	private TeleportTarget getPortalTarget(Entity entity, ServerWorld targetWorld, BlockPos targetPos, BlockPos srcPos) {
		var existingPortal = findExistingPortal(targetWorld, targetPos);
		BlockLocating.Rectangle portalShape;
		TeleportTarget.PostDimensionTransition transition;
		if (existingPortal.isPresent()) {
			var portalPos = existingPortal.get();
			var portalState = targetWorld.getBlockState(portalPos);
			portalShape = BlockLocating.getLargestRectangle(
					portalPos, portalState.get(Properties.HORIZONTAL_AXIS), 21,
					Direction.Axis.Y, 21, checkPos -> targetWorld.getBlockState(checkPos) == portalState
			);
			transition = TeleportTarget.SEND_TRAVEL_THROUGH_PORTAL_PACKET.then(e -> e.addPortalChunkTicketAt(targetPos));
		} else {
			var axis = entity.getWorld().getBlockState(srcPos).getOrEmpty(AXIS).orElse(Direction.Axis.X);
			var normalPortal = portal.getPortalStructure(targetWorld);
			var portalWithPlatform = portal.getPortalWithPlatformStructure(targetWorld);
			if (normalPortal.isEmpty()) {
				portal.getPortalStructure().ifPresent(s -> log(s.id()));
				return null;
			}
			if (portalWithPlatform.isEmpty()) {
				portal.getPortalWithPlatformStructure().ifPresent(s -> log(s.id()));
				return null;
			}
			var generator = new PortalGenerator(portal.getBlock(), normalPortal.get(), portalWithPlatform.get(), axis);
			targetWorld.getChunk(targetPos);
			portalShape = generator.createPortal(targetWorld, targetPos).orElse(null);
			if (portalShape == null) {
				Features.LOGGER.error("Unable to create portal near {} in {}", targetPos, targetWorld.getRegistryKey());
				return null;
			}
			transition = TeleportTarget.SEND_TRAVEL_THROUGH_PORTAL_PACKET.then(TeleportTarget.ADD_PORTAL_CHUNK_TICKET);
		}

		return AccessorNetherPortalBlock.callGetExitPortalTarget(entity, targetPos, portalShape, targetWorld, transition);
	}

	private Optional<BlockPos> findExistingPortal(ServerWorld targetWorld, BlockPos targetPos) {
		var poiStorage = targetWorld.getPointOfInterestStorage();
		int searchRange = MathHelper.floor(128 / targetWorld.getDimension().coordinateScale());
		poiStorage.preloadChunks(
				targetWorld, targetPos, searchRange
		);
		return poiStorage.getInSquare(
				poi -> poi.matchesKey(portal.getPoiKey()), targetPos, searchRange, PointOfInterestStorage.OccupationStatus.ANY
		).map(PointOfInterest::getPos).filter(targetWorld.getWorldBorder()::contains).filter(
				blockPos -> targetWorld.getBlockState(blockPos).contains(Properties.HORIZONTAL_AXIS)
		).min(Comparator.<BlockPos>comparingDouble(p -> p.getSquaredDistance(targetPos)).thenComparingInt(BlockPos::getY));
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state) {
		return Blocks.NETHER_PORTAL.getDefaultState().with(NetherPortalBlock.AXIS, state.get(NetherPortalBlock.AXIS));
	}

}
