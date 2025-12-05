package nu.metacraft.simplecustomfeatures.objects.blocks.dynamic_portal;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.minecraft.util.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;
import nu.metacraft.lib.util.helper.EntityHelper;
import nu.metacraft.lib.util.helper.ViewHelper;
import nu.metacraft.simplecustomfeatures.Features;
import nu.metacraft.simplecustomfeatures.mixin.NetherPortalBlockAccessor;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.Comparator;
import java.util.Optional;

public class DynamicPortalBlock extends NetherPortalBlock implements PolymerBlock {

	private final PortalBlockObject portal;

	public DynamicPortalBlock(BlockBehaviour.Properties settings, PortalBlockObject portal) {
		super(settings);
		this.portal = portal;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
		if (world.getGameRules().get(GameRules.SPAWN_MOBS)) {
			var spawns = portal.getEntitySpawns().get(world.dimension());
			if (spawns != null && random.nextDouble() <= spawns.spawnChance()) {
				spawns.entities().getRandom(random).ifPresent(entityData -> {
					var spawnPos = new BlockPos.MutableBlockPos().set(pos);
					try (var logging = LoggingErrorReporter.create(() -> "simple-custom-features:DynamicPortalBlock#randomTick", Features.LOGGER)) {
						var readView = TagValueInput.create(logging, world.registryAccess(), entityData);
						var type = EntityType.by(readView).orElse(null);
						if (SpawnPlacements.getPlacementType(type) != SpawnPlacementTypes.NO_RESTRICTIONS) {
							BlockPos.MutableBlockPos blockBelowChecker = new BlockPos.MutableBlockPos().set(pos);
							while (world.getBlockState(blockBelowChecker).is(this) && blockBelowChecker.getY() > world.getMinY()) {
								blockBelowChecker.move(Direction.DOWN);
							}
							if (!world.getBlockState(blockBelowChecker).isSolid()) {
								return;
							}
							spawnPos.set(blockBelowChecker.move(Direction.UP));
						}
						var center = Vec3.atBottomCenterOf(spawnPos);
						var rootEntity = EntityHelper.loadEntityWithPassengers(readView, world, EntitySpawnReason.STRUCTURE, (entity, data) -> {
							entity.setPortalCooldown();
							if (spawns.initialize()) {
								EntityHelper.initializeEntity(
										entity, ViewHelper.getSize(data) > 1 ? data : null,
										world, world.getCurrentDifficultyAt(spawnPos),
										EntitySpawnReason.STRUCTURE, null
								);
							}
							entity.setPos(center);
							return entity;
						});
						rootEntity.ifPresent(world::addFreshEntityWithPassengers);
					}
				});
			}
		}
	}

	public PortalBlockObject getPortal() {
		return portal;
	}

	@Override
	protected BlockState updateShape(
			BlockState state, LevelReader world, ScheduledTickAccess tickView, BlockPos pos,
			Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random
	) {
		var axis = state.getValue(AXIS);
		var isIrrelevant = direction.getAxis().isHorizontal() && axis != direction.getAxis();
		if (isIrrelevant || neighborState.is(this) || findPortalShape(world, pos, axis).isPresent()) {
			return super.updateShape(state, world, tickView, pos, direction, neighborPos, neighborState, random);
		}
		return Blocks.AIR.defaultBlockState();
	}

	public Optional<PortalShape> findPortalShape(
			ServerLevel world, BlockPos pos
	) {
		return PortalShape.findPortalShape(world, pos, this);
	}

	public Optional<PortalShape> findPortalShape(LevelReader world, BlockPos pos, Direction.Axis axis) {
		return PortalShape.findPortalShape(world, pos, this, axis);
	}

	public boolean isValidStateInsidePortal(BlockInWorld state) {
		return state.getState().isAir() || state.getState().is(this) ||
				portal.getBlockActivator().map(activator -> activator.matches(state)).orElse(false) ||
				portal.getReplaceableByPortal().map(activator -> activator.matches(state)).orElse(false);
	}

	public boolean isFrameBlock(BlockInWorld state) {
		return portal.getValidFrameBlock().matches(state);
	}

	public int getMaxPortalSideLength() {
		return 32;
	}

	@Nullable
	@Override
	public TeleportTransition getPortalDestination(ServerLevel world, Entity entity, BlockPos pos) {
		var targetDim = portal.getTargetDim(world.dimension());
		if (targetDim == null) return null;
		var targetWorld = world.getServer().getLevel(targetDim);
		if (targetWorld == null) return null;
		var factor = DimensionType.getTeleportationScale(world.dimensionType(), targetWorld.dimensionType());
		BlockPos targetPos = targetWorld.getWorldBorder().clampToBounds(pos.getX() * factor, pos.getY(), pos.getZ() * factor);
		return getPortalTarget(entity, targetWorld, targetPos, pos);
	}

	private static void log(Identifier id) {
		Features.LOGGER.error(
				"{} is not a valid structure, portal generation cancelled.", id
		);
	}

	private TeleportTransition getPortalTarget(Entity entity, ServerLevel targetWorld, BlockPos targetPos, BlockPos srcPos) {
		var existingPortal = findExistingPortal(targetWorld, targetPos);
		BlockUtil.FoundRectangle portalShape;
		TeleportTransition.PostTeleportTransition transition;
		if (existingPortal.isPresent()) {
			var portalPos = existingPortal.get();
			var portalState = targetWorld.getBlockState(portalPos);
			portalShape = BlockUtil.getLargestRectangleAround(
					portalPos, portalState.getValue(BlockStateProperties.HORIZONTAL_AXIS), 21,
					Direction.Axis.Y, 21, checkPos -> targetWorld.getBlockState(checkPos) == portalState
			);
			transition = TeleportTransition.PLAY_PORTAL_SOUND.then(e -> e.placePortalTicket(targetPos));
		} else {
			var axis = entity.level().getBlockState(srcPos).getOptionalValue(AXIS).orElse(Direction.Axis.X);
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
				Features.LOGGER.error("Unable to create portal near {} in {}", targetPos, targetWorld.dimension());
				return null;
			}
			transition = TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET);
		}

		return NetherPortalBlockAccessor.callGetDimensionTransitionFromExit(entity, targetPos, portalShape, targetWorld, transition);
	}

	private Optional<BlockPos> findExistingPortal(ServerLevel targetWorld, BlockPos targetPos) {
		var poiStorage = targetWorld.getPoiManager();
		int searchRange = Mth.floor(128 / targetWorld.dimensionType().coordinateScale());
		poiStorage.ensureLoadedAndValid(
				targetWorld, targetPos, searchRange
		);
		return poiStorage.getInSquare(
				poi -> poi.is(portal.getPoiKey()), targetPos, searchRange, PoiManager.Occupancy.ANY
		).map(PoiRecord::getPos).filter(targetWorld.getWorldBorder()::isWithinBounds).filter(
				blockPos -> targetWorld.getBlockState(blockPos).hasProperty(BlockStateProperties.HORIZONTAL_AXIS)
		).min(Comparator.<BlockPos>comparingDouble(p -> p.distSqr(targetPos)).thenComparingInt(BlockPos::getY));
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext ctx) {
		return Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, state.getValue(NetherPortalBlock.AXIS));
	}

}
