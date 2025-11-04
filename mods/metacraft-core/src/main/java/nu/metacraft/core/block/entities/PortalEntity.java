package nu.metacraft.core.block.entities;

import com.google.common.collect.Iterables;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ArrayListDeque;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.LockCode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.core.portal.*;
import nu.metacraft.lib.scheduler.Throwaway;
import org.jetbrains.annotations.NotNull;
import org.joml.*;
import nu.metacraft.core.METAcraftCoreTags;
import nu.metacraft.core.block.METAcraftBlockEntities;
import nu.metacraft.core.callbacks.PortalTargetValidEvent;
import nu.metacraft.core.util.TeleportPredicate;
import nu.metacraft.lib.util.METACodecs;
import nu.metacraft.lib.util.TaskScheduler;
import nu.metacraft.lib.util.helper.OrientationHelper;
import nu.metacraft.lib.util.helper.TeleportHelper;

import java.lang.Math;
import java.util.*;
import java.util.stream.Stream;

public class PortalEntity extends BlockEntity {

	private static final String TARGET_DIM = "TargetDim";
	private static final String TARGET_POS = "TargetPos";
	private static final String TARGET = "target";
	private static final String PORTAL_FACING = "PortalFacing";
	private static final String SHOULD_TELEPORT = "ShouldTeleport";

	public static final int MAX_SEARCH_BLOCKS = 100;
	private static final int RANGE_CHECK = 10;

	@NotNull
	protected volatile PortalTarget target = EmptyPortalTarget.getInstance();
	protected FrontAndTop portalFacing;
	protected final List<TeleportPredicate> shouldTeleport = new ArrayList<>();
	protected LockCode lock = LockCode.NO_LOCK;

	private final Set<Entity> pushedAway = new HashSet<>();

	public PortalEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public PortalEntity(BlockPos pos, BlockState state) {
		super(METAcraftBlockEntities.PORTAL, pos, state);
	}

	public void setTarget(PortalTarget target) {
		setTarget(target, true);
	}

	public void setTarget(PortalTarget target, boolean needsSaving) {
		this.target = target;
		if (needsSaving) {
			setChanged();
		}
	}

	public @NotNull PortalTarget getTarget() {
		return target;
	}

	public void setPortalFacing(FrontAndTop facing) {
		portalFacing = facing;
		setChanged();
	}

	public void setShouldTeleport(List<TeleportPredicate> shouldTeleport) {
		this.shouldTeleport.clear();
		this.shouldTeleport.addAll(shouldTeleport);
		setChanged();
	}

	private void notifyLocked(Stream<Player> players) {
		players.forEach(p -> p.displayClientMessage(Component.literal("The portal is locked"), true));
		level.playSound(null, worldPosition, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS);
	}

	public void initializeTarget() {
		getTarget().initialize(this);
	}

	protected void onUnlocked(Player player, InteractionHand hand, ItemStack stack) {
		lock = LockCode.NO_LOCK;
		setChanged();
		var useRemainder = stack.get(DataComponents.USE_REMAINDER);
		int c = stack.getCount();
		stack.consume(1, player);
		if (useRemainder != null) {
			player.setItemInHand(hand, useRemainder.convertIntoRemainder(stack, c, player.isCreative(), player::handleExtraItemsCreatedOnUse));
		}
		level.playSound(null, worldPosition, SoundEvents.VAULT_INSERT_ITEM, SoundSource.BLOCKS);
		player.displayClientMessage(Component.literal("The portal is now unlocked!"), true);
		initializeTarget();
	}

	public InteractionResult interactWithItem(
			ItemStack stack, BlockState state, Level world, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit
	) {
		if (isLocked()) {
			if (lock.unlocksWith(stack)) {
				onUnlocked(player, hand, stack);
			} else {
				notifyLocked(Stream.of(player));
			}
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	public void setLevel(Level world) {
		super.setLevel(world);
		if (!world.isClientSide() && target instanceof FixedPortalTarget(GlobalPos t, boolean autolink)) {
			if (t.pos() == null) {
				target = EmptyPortalTarget.getInstance();
				return;
			}
			if (t.dimension() == null) {
				target = FixedPortalTarget.create(
						world.dimension(), t.pos()
				);
			}
		}
	}

	@Override
	public void loadAdditional(ValueInput nbt) {
		super.loadAdditional(nbt);
		target = nbt.read(
				TARGET, PortalTargetRegistry.CODEC
		).orElse(EmptyPortalTarget.getInstance());
		nbt.read(TARGET_POS, BlockPos.CODEC).ifPresent(targetPos -> {
			target = nbt.read(TARGET_DIM, Level.RESOURCE_KEY_CODEC).<PortalTarget>map(
					targetDim -> FixedPortalTarget.create(targetDim, targetPos)
			).orElseGet(() -> FixedLocalPortalTarget.create(targetPos));
		});
		nbt.read(PORTAL_FACING, METACodecs.ORIENTATION_CODEC).ifPresentOrElse(
				facing -> portalFacing = facing,
				() -> portalFacing = null
		);
		shouldTeleport.clear();
		nbt.read(SHOULD_TELEPORT, TeleportPredicate.LIST_CODEC).ifPresent(this.shouldTeleport::addAll);
		lock = LockCode.fromTag(nbt);
	}

	public boolean isPartOfPortal() {
		return true;
	}

	public void setLock(LockCode lock) {
		this.lock = lock;
		setChanged();
	}

	@Override
	public void saveAdditional(ValueOutput nbt) {
		super.saveAdditional(nbt);
		if (target != EmptyPortalTarget.getInstance()) {
			nbt.store(TARGET, PortalTargetRegistry.CODEC, target);
		}
		if (portalFacing != null) {
			nbt.store(PORTAL_FACING, METACodecs.ORIENTATION_CODEC, portalFacing);
		}
		nbt.store(SHOULD_TELEPORT, TeleportPredicate.LIST_CODEC, shouldTeleport);

		lock.addToTag(nbt);
	}

	public ServerLevel getTargetDim(GlobalPos target) {
		if (level == null || level.isClientSide()) return null;
		if (target != null) {
			var dim = level.getServer().getLevel(target.dimension());
			if (dim != null) {
				return dim;
			}
		}
		return (ServerLevel) level;
	}

	protected boolean shouldTeleport(Entity entity) {
		return entity.canUsePortal(false) && TeleportPredicate.shouldTeleport(shouldTeleport, (ServerLevel) level, entity);
	}

	protected void onTeleportFail(Entity entity) {
		if (entity instanceof ServerPlayer player) {
			player.sendSystemMessage(Component.literal("This portal cannot teleport players."));
		}
	}

	private boolean isSpaceOpen(Level world, BlockPos pos) {
		return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
	}

	public Iterable<BlockPos> forAllNearbyPortals() {
		return forAllNearbyPortals(level, worldPosition, MAX_SEARCH_BLOCKS, false);
	}

	public static Iterable<BlockPos> forAllNearbyPortals(Level world, BlockPos pos) {
		return forAllNearbyPortals(world, pos, false);
	}

	public static Optional<PortalEntity> findPortal(Level world, BlockPos pos) {
		if (world.getBlockEntity(pos) instanceof PortalEntity p) {
			return Optional.of(p);
		}
		for (var portalPos : forAllNearbyPortals(world, pos)) {
			if (world.getBlockEntity(portalPos) instanceof PortalEntity p) {
				return Optional.of(p);
			}
		}
		return Optional.empty();
	}

	public static Iterable<BlockPos> forAllNearbyPortals(Level world, BlockPos pos, boolean alwaysIncludeCore) {
		return forAllNearbyPortals(world, pos, MAX_SEARCH_BLOCKS, alwaysIncludeCore);
	}

	public static Iterable<BlockPos> forAllNearbyPortals(Level world, BlockPos pos, int maxBlocks, boolean alwaysIncludeCore) {
		Set<BlockPos> visited = new HashSet<>();
		visited.add(pos);
		BlockPos.MutableBlockPos currentPos = new BlockPos.MutableBlockPos();
		currentPos.set(pos);
		return () -> new Iterator<>() {
			private final Queue<BlockPos> toSearch = new ArrayListDeque<>();


			private int stepsRemaining = maxBlocks;

			private void findNextPositions() {
				if (stepsRemaining <= 0) return;
				BlockPos.MutableBlockPos checker = new BlockPos.MutableBlockPos();
				for (Direction direction : Direction.values()) {
					checker.setWithOffset(currentPos, direction);
					if (
							(
								world.getBlockState(checker).is(METAcraftCoreTags.PORTAL_PADDING) ||
								world.getBlockEntity(checker) instanceof PortalEntity p && (p.isPartOfPortal() || alwaysIncludeCore)
							) && !visited.contains(checker)
					) {
						stepsRemaining--;
						var pos = checker.immutable();
						toSearch.add(pos);
						visited.add(pos);
					}
				}
			}

			@Override
			public boolean hasNext() {
				if (toSearch.isEmpty()) {
					findNextPositions();
				}
				return !toSearch.isEmpty();
			}

			@Override
			public BlockPos next() {
				var pos = toSearch.poll();
				currentPos.set(pos);
				findNextPositions();
				return pos;
			}
		};
	}

	private void chooseDirection(Set<Direction.Axis> axes) {
		if (level != null) {
			int currentBest = 0;
			for (Direction.AxisDirection direction : Direction.AxisDirection.values()) {
				for (Direction.Axis axis : axes) {
					var facing = Direction.fromAxisAndDirection(axis, direction);
					var found = countEmptySpaces(facing);
					if (found > currentBest) {
						currentBest = found;
						portalFacing = OrientationHelper.fromDirection(facing);
						setChanged();
					}
				}
			}
		}
	}

	private int countEmptySpaces(Direction direction) {
		if (level == null) return 0;
		int spaces = 0;
		for (BlockPos pos : forAllNearbyPortals()) {
			BlockPos.MutableBlockPos check = new BlockPos.MutableBlockPos();
			check.setWithOffset(pos, direction);
			if (isSpaceOpen(level, check)) {
				spaces++;
				for (int i = 0; i < RANGE_CHECK && isSpaceOpen(level, check); i++) {
					spaces++;
				}
			}
		}
		return spaces;
	}

	public void computeFacing() {
		Set<Direction.Axis> validAxes = new HashSet<>(List.of(Direction.Axis.values()));
		BlockPos.MutableBlockPos prevPos = new BlockPos.MutableBlockPos();
		prevPos.set(worldPosition);
		for (BlockPos pos : forAllNearbyPortals()) {
			if (prevPos.getX() != pos.getX()) {
				validAxes.remove(Direction.Axis.X);
			}
			if (prevPos.getY() != pos.getY()) {
				validAxes.remove(Direction.Axis.Y);
			}
			if (prevPos.getZ() != pos.getZ()) {
				validAxes.remove(Direction.Axis.Z);
			}
			if (validAxes.isEmpty()) break;
		}
		chooseDirection(validAxes);
	}

	public void computeTargetFacing(GlobalPos target) {
		if (target != null) {
			var targetDim = getTargetDim(target);
			if (targetDim.getBlockEntity(target.pos()) instanceof PortalEntity portal) {
				if (portal.portalFacing != null) return;
				portal.computeFacing();
			}
		}
	}

	private Entity teleportNoFacing(Entity entity, GlobalPos target) {
		var targetDim = getTargetDim(target);
		return TeleportHelper.teleportEntity(
				entity,
				new TeleportTransition(
						targetDim, Vec3.atBottomCenterOf(target.pos()), entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(),
						TeleportTransition.DO_NOTHING
				)
		);
	}

	public record Angles(float yaw, float pitch) {}

	private static Angles fix(Angles toFix) {
		var yaw = Mth.wrapDegrees(toFix.yaw);
		var pitch = toFix.pitch % 360.0f;
		var absPitch = Math.abs(pitch);
		if (absPitch > 90 && absPitch < 270) {
			yaw = Mth.wrapDegrees(yaw + 180);
		}
		pitch = Mth.wrapDegrees(pitch);
		if (Math.abs(pitch) > 90) {
			var pitchOffset = pitch < 0 ? -90 : 90;
			var diff = pitch - pitchOffset;
			pitch = pitchOffset - diff;
		}
		if (pitch != toFix.pitch || yaw != toFix.yaw) {
			return new Angles(yaw, pitch);
		}
		return toFix;
	}

	private static int getAngleBetweenDirections(Direction source, Direction target) {
		if (source == target) {
			return 0;
		} else if (source == target.getOpposite()) {
			return 180;
		} else {
			if (source.getAxis().isVertical()) {
				return -source.getAxisDirection().getStep() * 90;
			} else if (target.getAxis().isVertical()) {
				return target.getAxisDirection().getStep() * 90;
			} else {
				if (source.getClockWise(Direction.Axis.Y) == target) {
					return -90;
				} else {
					return 90;
				}
			}
		}
	}

	private static Quaterniond getQuaternion(
			FrontAndTop sourceDirection, FrontAndTop targetDirection, Direction entityFacing
	) {
		double angle = Math.toRadians(getAngleBetweenDirections(sourceDirection.front(), targetDirection.front()));

		if (OrientationHelper.isHorizontal(sourceDirection) && OrientationHelper.isHorizontal(targetDirection)) {
			return new Quaterniond().rotateYXZ(angle, 0, 0);
		} else if (OrientationHelper.isVertical(sourceDirection) && OrientationHelper.isHorizontal(targetDirection)) {
			int offsetAngle = entityFacing != null ? getAngleBetweenDirections(
					entityFacing, sourceDirection.top().getOpposite()
			) : 0;
			var horizontalAngle = getAngleBetweenDirections(sourceDirection.top().getOpposite(), targetDirection.front());
			return new Quaterniond().rotateYXZ(Math.toRadians(horizontalAngle + offsetAngle), angle, Math.PI);
		} else if (OrientationHelper.isHorizontal(sourceDirection) && OrientationHelper.isVertical(targetDirection)) {
			var horizontalAngle = Math.toRadians(getAngleBetweenDirections(sourceDirection.front(), targetDirection.top().getOpposite()));
			return new Quaterniond().rotateYXZ(horizontalAngle, -angle, 0);
		} else {
			var horizontalAngle = Math.toRadians(getAngleBetweenDirections(sourceDirection.top(), targetDirection.top()));
			return new Quaterniond().rotateYXZ(horizontalAngle, -angle, 0);
		}
	}

	private Angles rotateYawPitch(float yaw, float pitch, Quaterniond quaternion) {
		var angles = quaternion.getEulerAnglesYXZ(new Vector3d());

		float yawOffset = Mth.sin((float) angles.z) * Mth.HALF_PI * Mth.RAD_TO_DEG;
		float pitchOffset = (Mth.HALF_PI - Mth.cos((float) angles.z) * Mth.HALF_PI) * Mth.RAD_TO_DEG;
		angles = angles.mul(Mth.RAD_TO_DEG);

		return fix(new Angles(
				(float) -angles.y + yaw + yawOffset,
				(float) angles.x + pitch + pitchOffset
		));
	}

	private Quaterniond getRotationToPortal(FrontAndTop otherFacing, Entity entity) {
		if (portalFacing == null || otherFacing == null) {
			return new Quaterniond();
		}
		return getQuaternion(FrontAndTop.fromFrontAndTop(portalFacing.front().getOpposite(), portalFacing.top()), otherFacing, entity != null ? entity.getDirection() : null);
	}

	public AABB getBoundingBox() {
		return AABB.of(BoundingBox.encapsulatingPositions(Iterables.concat(
				List.of(worldPosition),
				forAllNearbyPortals()
		)).orElse(BoundingBox.fromCorners(worldPosition, worldPosition)));
	}

	private AABB getBoundingBoxIncludingPassengers(Entity entity) {
		var entityBox = entity.getBoundingBox();
		if (entity.isVehicle()) {
			for (var passenger : entity.getIndirectPassengers()) {
				entityBox = entityBox.minmax(passenger.getBoundingBox());
			}
		}
		return entityBox;
	}

	private static double getVectorPartForAxis(Vec3 pos, Direction.Axis axis) {
		return switch (axis) {
			case X -> pos.x();
			case Y -> pos.y();
			case Z -> pos.z();
		};
	}

	public void onCollision(BlockState state, Level world, BlockPos pos, Entity entity) {
		if (entity.isPassenger()) return;
		if (world.isClientSide()) return;
		AABB box = getBoundingBox();
		AABB entityBox = getBoundingBoxIncludingPassengers(entity);
		if (isLocked() && !pushedAway.contains(entity)) {
			var axis = portalFacing.front().getAxis();
			var entityPos = getVectorPartForAxis(entityBox.getCenter(), axis);
			var thisPos = getVectorPartForAxis(box.getCenter(), axis);
			Direction.AxisDirection pushDirection;
			if (thisPos > entityPos) {
				pushDirection = Direction.AxisDirection.NEGATIVE;
			} else {
				pushDirection = Direction.AxisDirection.POSITIVE;
			}

			var vector = Direction.fromAxisAndDirection(axis, pushDirection).getUnitVec3().add(
					new Vec3(
							0.25 * entity.getRandom().nextGaussian(),
							0.25 * entity.getRandom().nextGaussian(),
							0.25 * entity.getRandom().nextGaussian()
					)
			).normalize();
			entity.setDeltaMovement(vector);
			entity.hasImpulse = true;
			entity.hurtMarked = true;
			notifyLocked(entity.getSelfAndPassengers().filter(e -> e instanceof Player).map(p -> (Player) p));
			if (pushedAway.isEmpty()) {
				TaskScheduler.schedule(
						world.getServer(), METAcraftCore.getID("portal_clear/" + getBlockPos().asLong()),
						new Throwaway(pushedAway::clear), 5
				);
			}
			pushedAway.add(entity);
			return;
		}
		if (portalFacing != null) {
			var x = Math.max(entityBox.getXsize() - box.getXsize(), 0) + Math.abs(entity.getX() - entity.xo);
			var y = Math.max(entityBox.getYsize() - box.getYsize(), 0) + Math.abs(entity.getY() - entity.yo);
			var z = Math.max(entityBox.getZsize() - box.getZsize(), 0) + Math.abs(entity.getZ() - entity.zo);
			box = box.expandTowards(
					x * portalFacing.front().getStepX(),
					y * portalFacing.front().getStepY(),
					z * portalFacing.front().getStepZ()
			);
			box = box.expandTowards(
					x * -portalFacing.front().getStepX(),
					y * -portalFacing.front().getStepY(),
					z * -portalFacing.front().getStepZ()
			);
		}
		if (box.minmax(entityBox).equals(box)) {
			TaskScheduler.scheduleImmediately(world.getServer(), () -> {
				Entity toTP = entity;
				if (toTP.getPortalCooldown() <= 0) {
					toTP = teleport(toTP);
				}
				if (toTP != null) {
					toTP.setPortalCooldown(20);
				}
			});
		}
	}

	private static Vec3 rotate(Vec3 vec, Quaterniond quaternion) {
		Vector3d rotatable = new Vector3d(vec.x(), vec.y(), vec.z());
		rotatable.rotate(quaternion);
		return new Vec3(rotatable.x, rotatable.y, rotatable.z);
	}

	private Vec3 getTarget(AABB targetBox, Vec3 dist, Entity entity) {
		return targetBox.getCenter().add(dist).subtract(0, entity.getBbHeight()/2, 0);
	}

	public boolean isLocked() {
		return lock != LockCode.NO_LOCK;
	}

	public Entity teleport(Entity entity) {
		if (isLocked()) return entity;
		if (!shouldTeleport(entity)) {
			onTeleportFail(entity);
			return entity;
		}
		if (portalFacing == null) {
			computeFacing();
		}
		Entity newEntity = entity;
		var targetRes = this.target.getOrInitializeTargetForEntity(this, entity);
		if (targetRes.result().isPresent()) {
			var target = targetRes.result().get();
			computeTargetFacing(target);
			var targetDim = getTargetDim(target);
			if (!PortalTargetValidEvent.EVENT.invoker().isValid(targetDim, target.pos(), this, entity, true)) {
				return entity;
			}
			if (targetDim.getBlockEntity(target.pos()) instanceof PortalEntity portal) {
				if (portal.portalFacing != null) {
					var rotation = getRotationToPortal(portal.portalFacing, null);

					Vec3 velocity = rotate(entity.getDeltaMovement(), rotation);

					//Find position to place the player on other portal.
					var sourceBox = getBoundingBox();
					var targetBox = portal.getBoundingBox();
					Vec3 entityMovement = entity.position().subtract(entity.xo, entity.yo, entity.zo);
					if (entity.getDeltaMovement().length() > entityMovement.length()) {
						entityMovement = entity.getDeltaMovement();
					}
					Vec3 dist = entity.getBoundingBox().getCenter().subtract(entityMovement).subtract(sourceBox.getCenter());

					dist = new Vec3(dist.x() / (sourceBox.getXsize()/2), dist.y() / (sourceBox.getYsize()/2), dist.z() / (sourceBox.getZsize()/2));

					dist = rotate(dist, rotation);
					dist = dist.multiply(targetBox.getXsize()/2, targetBox.getYsize()/2, targetBox.getZsize()/2);

					AABB entityBox = getBoundingBoxIncludingPassengers(entity);

					Vec3 targetPos = getTarget(targetBox, dist, entity);

					var centeredBox = entityBox.move(entity.position().scale(-1));
					if (!targetDim.noCollision(centeredBox.move(targetPos))) {
						targetPos = getTarget(targetBox, dist, entity);
						if (!targetDim.noCollision(centeredBox.move(targetPos))) {
							if (entity instanceof ServerPlayer player) {
								player.displayClientMessage(Component.literal("Could not deposit you safely on the other side"), true);
							}
							return entity;
						}
					}

					var facing = rotateYawPitch(
							entity.getYRot(), entity.getXRot(), getRotationToPortal(portal.portalFacing, entity)
					);

					newEntity = TeleportHelper.teleportEntity(
							entity,
							new TeleportTransition(
									targetDim, targetPos, velocity, facing.yaw, facing.pitch,
									TeleportTransition.DO_NOTHING
							)
					);
				} else {
					newEntity = teleportNoFacing(entity, target);
				}
			} else {
				newEntity = teleportNoFacing(entity, target);
			}
		} else if (entity instanceof ServerPlayer player) {
			player.displayClientMessage(Component.literal(targetRes.error().map(DataResult.Error::message).orElse("missingno")), true);
		}
		return newEntity;
	}

}
