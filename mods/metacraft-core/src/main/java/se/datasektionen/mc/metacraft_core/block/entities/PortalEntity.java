package se.datasektionen.mc.metacraft_core.block.entities;

import com.google.common.collect.Iterables;
import com.mojang.serialization.DataResult;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.enums.Orientation;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.ContainerLock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.ArrayListDeque;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.joml.*;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.datasektionen.mc.metacraft_core.METAcraftCoreTags;
import se.datasektionen.mc.metacraft_core.block.METAcraftBlockEntities;
import se.datasektionen.mc.metacraft_core.callbacks.PortalTargetValidEvent;
import se.datasektionen.mc.metacraft_core.portal.EmptyPortalTarget;
import se.datasektionen.mc.metacraft_core.portal.PortalTarget;
import se.datasektionen.mc.metacraft_core.portal.PortalTargetRegistry;
import se.datasektionen.mc.metacraft_core.portal.FixedPortalTarget;
import se.datasektionen.mc.metacraft_core.util.TeleportPredicate;
import se.datasektionen.mc.metacraft_lib.util.ExtraCodecs;
import se.datasektionen.mc.metacraft_lib.util.TaskScheduler;
import se.datasektionen.mc.metacraft_lib.util.helper.OrientationHelper;
import se.datasektionen.mc.metacraft_lib.util.helper.TeleportHelper;

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
	protected Orientation portalFacing;
	protected final List<TeleportPredicate> shouldTeleport = new ArrayList<>();
	protected ContainerLock lock = ContainerLock.EMPTY;

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
			markDirty();
		}
	}

	public @NotNull PortalTarget getTarget() {
		return target;
	}

	public void setPortalFacing(Orientation facing) {
		portalFacing = facing;
		markDirty();
	}

	public void setShouldTeleport(List<TeleportPredicate> shouldTeleport) {
		this.shouldTeleport.clear();
		this.shouldTeleport.addAll(shouldTeleport);
		markDirty();
	}

	private void notifyLocked(Stream<PlayerEntity> players) {
		players.forEach(p -> p.sendMessage(Text.literal("The portal is locked"), true));
		world.playSound(null, pos, SoundEvents.BLOCK_CHEST_LOCKED, SoundCategory.BLOCKS);
	}

	protected void onUnlocked(PlayerEntity player, Hand hand, ItemStack stack) {
		lock = ContainerLock.EMPTY;
		markDirty();
		var useRemainder = stack.get(DataComponentTypes.USE_REMAINDER);
		int c = stack.getCount();
		stack.decrementUnlessCreative(1, player);
		if (useRemainder != null) {
			player.setStackInHand(hand, useRemainder.convert(stack, c, player.isCreative(), player::giveOrDropStack));
		}
		world.playSound(null, pos, SoundEvents.BLOCK_VAULT_INSERT_ITEM, SoundCategory.BLOCKS);
		player.sendMessage(Text.literal("The portal is now unlocked!"), true);
		getTarget().initialize(this);
	}

	public ActionResult interactWithItem(
			ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player,
			Hand hand, BlockHitResult hit
	) {
		if (isLocked()) {
			if (lock.canOpen(stack)) {
				onUnlocked(player, hand, stack);
			} else {
				notifyLocked(Stream.of(player));
			}
			return ActionResult.SUCCESS_SERVER;
		}
		return ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION;
	}

	@Override
	public void setWorld(World world) {
		super.setWorld(world);
		if (!world.isClient() && target instanceof FixedPortalTarget(GlobalPos t)) {
			if (t.pos() == null) {
				target = EmptyPortalTarget.getInstance();
				return;
			}
			if (t.dimension() == null) {
				target = new FixedPortalTarget(
						GlobalPos.create(world.getRegistryKey(), t.pos())
				);
			}
		}
	}

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapperLookup) {
		super.readNbt(nbt, wrapperLookup);
		if (nbt.contains(TARGET)) {
			target = PortalTargetRegistry.CODEC.parse(
					wrapperLookup.getOps(NbtOps.INSTANCE), nbt.get(TARGET)
			).resultOrPartial(
					METAcraftCore.LOGGER::error
			).orElse(EmptyPortalTarget.getInstance());
		} else {
			target = EmptyPortalTarget.getInstance();
		}
		if (nbt.contains(TARGET_POS)) {
			RegistryKey<World> targetDim = null;
			var targetPos = NbtHelper.toBlockPos(nbt, TARGET_POS).orElse(null);
			if (nbt.contains(TARGET_DIM)) {
				targetDim = World.CODEC.parse(NbtOps.INSTANCE, nbt.get(TARGET_DIM)).resultOrPartial(
						METAcraftCore.LOGGER::error
				).orElse(null);
			}
			try {
				target = new FixedPortalTarget(
						GlobalPos.create(targetDim, targetPos)
				);
			} catch (NullPointerException e) {
				METAcraftCore.LOGGER.error(
						"Failed to update previous portal destination because some other mod added a weird mixin", e
				);
			}
		}
		if (nbt.contains(PORTAL_FACING)) {
			portalFacing = ExtraCodecs.ORIENTATION_CODEC.parse(NbtOps.INSTANCE, nbt.get(PORTAL_FACING)).resultOrPartial(
					METAcraftCore.LOGGER::error
			).orElse(null);
		} else {
			portalFacing = null;
		}
		if (nbt.contains(SHOULD_TELEPORT)) {
			TeleportPredicate.LIST_CODEC.parse(wrapperLookup.getOps(NbtOps.INSTANCE), nbt.get(SHOULD_TELEPORT)).resultOrPartial(
					METAcraftCore.LOGGER::error
			).ifPresent(this.shouldTeleport::addAll);
		}
		lock = ContainerLock.fromNbt(nbt, wrapperLookup);
	}

	public boolean isPartOfPortal() {
		return true;
	}

	public void setLock(ContainerLock lock) {
		this.lock = lock;
		markDirty();
	}

	@Override
	public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapperLookup) {
		super.writeNbt(nbt, wrapperLookup);
		if (target != EmptyPortalTarget.getInstance()) {
			PortalTargetRegistry.CODEC.encodeStart(
					wrapperLookup.getOps(NbtOps.INSTANCE), target
			).resultOrPartial(
					METAcraftCore.LOGGER::error
			).ifPresent(target -> {
				nbt.put(TARGET, target);
			});
		}
		if (portalFacing != null) {
			ExtraCodecs.ORIENTATION_CODEC.encodeStart(NbtOps.INSTANCE, portalFacing).resultOrPartial(
					METAcraftCore.LOGGER::error
			).ifPresent(facing -> {
				nbt.put(PORTAL_FACING, facing);
			});
		}

		TeleportPredicate.LIST_CODEC.encodeStart(wrapperLookup.getOps(NbtOps.INSTANCE), shouldTeleport).resultOrPartial(
				METAcraftCore.LOGGER::error
		).ifPresent(shouldTeleport -> {
			nbt.put(SHOULD_TELEPORT, shouldTeleport);
		});

		lock.writeNbt(nbt, wrapperLookup);
	}

	public ServerWorld getTargetDim(GlobalPos target) {
		if (world == null || world.isClient()) return null;
		if (target != null) {
			var dim = world.getServer().getWorld(target.dimension());
			if (dim != null) {
				return dim;
			}
		}
		return (ServerWorld) world;
	}

	protected boolean shouldTeleport(Entity entity) {
		return entity.canUsePortals(false) && TeleportPredicate.shouldTeleport(shouldTeleport, (ServerWorld) world, entity);
	}

	protected void onTeleportFail(Entity entity) {
		if (entity instanceof ServerPlayerEntity player) {
			player.sendMessage(Text.literal("This portal cannot teleport players."));
		}
	}

	private boolean isSpaceOpen(World world, BlockPos pos) {
		return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
	}

	public Iterable<BlockPos> forAllNearbyPortals() {
		return forAllNearbyPortals(world, pos, MAX_SEARCH_BLOCKS, false);
	}

	public static Iterable<BlockPos> forAllNearbyPortals(World world, BlockPos pos) {
		return forAllNearbyPortals(world, pos, false);
	}

	public static Optional<PortalEntity> findPortal(World world, BlockPos pos) {
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

	public static Iterable<BlockPos> forAllNearbyPortals(World world, BlockPos pos, boolean alwaysIncludeCore) {
		return forAllNearbyPortals(world, pos, MAX_SEARCH_BLOCKS, alwaysIncludeCore);
	}

	public static Iterable<BlockPos> forAllNearbyPortals(World world, BlockPos pos, int maxBlocks, boolean alwaysIncludeCore) {
		Set<BlockPos> visited = new HashSet<>();
		visited.add(pos);
		BlockPos.Mutable currentPos = new BlockPos.Mutable();
		currentPos.set(pos);
		return () -> new Iterator<>() {
			private final Queue<BlockPos> toSearch = new ArrayListDeque<>();


			private int stepsRemaining = maxBlocks;

			private void findNextPositions() {
				if (stepsRemaining <= 0) return;
				BlockPos.Mutable checker = new BlockPos.Mutable();
				for (Direction direction : Direction.values()) {
					checker.set(currentPos, direction);
					if (
							(
								world.getBlockState(checker).isIn(METAcraftCoreTags.PORTAL_PADDING) ||
								world.getBlockEntity(checker) instanceof PortalEntity p && (p.isPartOfPortal() || alwaysIncludeCore)
							) && !visited.contains(checker)
					) {
						stepsRemaining--;
						var pos = checker.toImmutable();
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
		if (world != null) {
			int currentBest = 0;
			for (Direction.AxisDirection direction : Direction.AxisDirection.values()) {
				for (Direction.Axis axis : axes) {
					var facing = Direction.from(axis, direction);
					var found = countEmptySpaces(facing);
					if (found > currentBest) {
						currentBest = found;
						portalFacing = OrientationHelper.fromDirection(facing);
						markDirty();
					}
				}
			}
		}
	}

	private int countEmptySpaces(Direction direction) {
		if (world == null) return 0;
		int spaces = 0;
		for (BlockPos pos : forAllNearbyPortals()) {
			BlockPos.Mutable check = new BlockPos.Mutable();
			check.set(pos, direction);
			if (isSpaceOpen(world, check)) {
				spaces++;
				for (int i = 0; i < RANGE_CHECK && isSpaceOpen(world, check); i++) {
					spaces++;
				}
			}
		}
		return spaces;
	}

	public void computeFacing() {
		Set<Direction.Axis> validAxes = new HashSet<>(List.of(Direction.Axis.values()));
		BlockPos.Mutable prevPos = new BlockPos.Mutable();
		prevPos.set(pos);
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
				new TeleportTarget(
						targetDim, Vec3d.ofBottomCenter(target.pos()), entity.getVelocity(), entity.getYaw(), entity.getPitch(),
						TeleportTarget.NO_OP
				)
		);
	}

	public record Angles(float yaw, float pitch) {}

	private static Angles fix(Angles toFix) {
		var yaw = MathHelper.wrapDegrees(toFix.yaw);
		var pitch = toFix.pitch % 360.0f;
		var absPitch = Math.abs(pitch);
		if (absPitch > 90 && absPitch < 270) {
			yaw = MathHelper.wrapDegrees(yaw + 180);
		}
		pitch = MathHelper.wrapDegrees(pitch);
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
				return -source.getDirection().offset() * 90;
			} else if (target.getAxis().isVertical()) {
				return target.getDirection().offset() * 90;
			} else {
				if (source.rotateClockwise(Direction.Axis.Y) == target) {
					return -90;
				} else {
					return 90;
				}
			}
		}
	}

	private static Quaterniond getQuaternion(
			Orientation sourceDirection, Orientation targetDirection, Direction entityFacing
	) {
		double angle = Math.toRadians(getAngleBetweenDirections(sourceDirection.getFacing(), targetDirection.getFacing()));

		if (OrientationHelper.isHorizontal(sourceDirection) && OrientationHelper.isHorizontal(targetDirection)) {
			return new Quaterniond().rotateYXZ(angle, 0, 0);
		} else if (OrientationHelper.isVertical(sourceDirection) && OrientationHelper.isHorizontal(targetDirection)) {
			int offsetAngle = entityFacing != null ? getAngleBetweenDirections(
					entityFacing, sourceDirection.getRotation().getOpposite()
			) : 0;
			var horizontalAngle = getAngleBetweenDirections(sourceDirection.getRotation().getOpposite(), targetDirection.getFacing());
			return new Quaterniond().rotateYXZ(Math.toRadians(horizontalAngle + offsetAngle), angle, Math.PI);
		} else if (OrientationHelper.isHorizontal(sourceDirection) && OrientationHelper.isVertical(targetDirection)) {
			var horizontalAngle = Math.toRadians(getAngleBetweenDirections(sourceDirection.getFacing(), targetDirection.getRotation().getOpposite()));
			return new Quaterniond().rotateYXZ(horizontalAngle, -angle, 0);
		} else {
			var horizontalAngle = Math.toRadians(getAngleBetweenDirections(sourceDirection.getRotation(), targetDirection.getRotation()));
			return new Quaterniond().rotateYXZ(horizontalAngle, -angle, 0);
		}
	}

	private Angles rotateYawPitch(float yaw, float pitch, Quaterniond quaternion) {
		var angles = quaternion.getEulerAnglesYXZ(new Vector3d());

		float yawOffset = MathHelper.sin((float) angles.z) * MathHelper.HALF_PI * MathHelper.DEGREES_PER_RADIAN;
		float pitchOffset = (MathHelper.HALF_PI - MathHelper.cos((float) angles.z) * MathHelper.HALF_PI) * MathHelper.DEGREES_PER_RADIAN;
		angles = angles.mul(MathHelper.DEGREES_PER_RADIAN);

		return fix(new Angles(
				(float) -angles.y + yaw + yawOffset,
				(float) angles.x + pitch + pitchOffset
		));
	}

	private Quaterniond getRotationToPortal(Orientation otherFacing, Entity entity) {
		if (portalFacing == null || otherFacing == null) {
			return new Quaterniond();
		}
		return getQuaternion(Orientation.byDirections(portalFacing.getFacing().getOpposite(), portalFacing.getRotation()), otherFacing, entity != null ? entity.getHorizontalFacing() : null);
	}

	public Box getBoundingBox() {
		return Box.from(BlockBox.encompassPositions(Iterables.concat(
				List.of(pos),
				forAllNearbyPortals()
		)).orElse(BlockBox.create(pos, pos)));
	}

	private Box getBoundingBoxIncludingPassengers(Entity entity) {
		var entityBox = entity.getBoundingBox();
		if (entity.hasPassengers()) {
			for (var passenger : entity.getPassengersDeep()) {
				entityBox = entityBox.union(passenger.getBoundingBox());
			}
		}
		return entityBox;
	}

	private static double getVectorPartForAxis(Vec3d pos, Direction.Axis axis) {
		return switch (axis) {
			case X -> pos.getX();
			case Y -> pos.getY();
			case Z -> pos.getZ();
		};
	}

	public void onCollision(BlockState state, World world, BlockPos pos, Entity entity) {
		if (entity.hasVehicle()) return;
		Box box = getBoundingBox();
		Box entityBox = getBoundingBoxIncludingPassengers(entity);
		if (isLocked() && !pushedAway.contains(entity)) {
			var axis = portalFacing.getFacing().getAxis();
			var entityPos = getVectorPartForAxis(entityBox.getCenter(), axis);
			var thisPos = getVectorPartForAxis(box.getCenter(), axis);
			Direction.AxisDirection pushDirection;
			if (thisPos > entityPos) {
				pushDirection = Direction.AxisDirection.NEGATIVE;
			} else {
				pushDirection = Direction.AxisDirection.POSITIVE;
			}

			var vector = Direction.from(axis, pushDirection).getDoubleVector().add(
					new Vec3d(
							0.25 * entity.getRandom().nextGaussian(),
							0.25 * entity.getRandom().nextGaussian(),
							0.25 * entity.getRandom().nextGaussian()
					)
			).normalize();
			entity.setVelocity(vector);
			entity.velocityDirty = true;
			entity.velocityModified = true;
			notifyLocked(entity.streamSelfAndPassengers().filter(e -> e instanceof PlayerEntity).map(p -> (PlayerEntity) p));
			if (pushedAway.isEmpty()) {
				TaskScheduler.schedule(world.getServer(), pushedAway::clear, 5);
			}
			pushedAway.add(entity);
			return;
		}
		if (portalFacing != null) {
			var x = Math.max(entityBox.getLengthX() - box.getLengthX(), 0) + Math.abs(entity.getX() - entity.prevX);
			var y = Math.max(entityBox.getLengthY() - box.getLengthY(), 0) + Math.abs(entity.getY() - entity.prevY);
			var z = Math.max(entityBox.getLengthZ() - box.getLengthZ(), 0) + Math.abs(entity.getZ() - entity.prevZ);
			box = box.stretch(
					x * portalFacing.getFacing().getOffsetX(),
					y * portalFacing.getFacing().getOffsetY(),
					z * portalFacing.getFacing().getOffsetZ()
			);
			box = box.stretch(
					x * -portalFacing.getFacing().getOffsetX(),
					y * -portalFacing.getFacing().getOffsetY(),
					z * -portalFacing.getFacing().getOffsetZ()
			);
		}
		if (box.union(entityBox).equals(box)) {
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

	private static Vec3d rotate(Vec3d vec, Quaterniond quaternion) {
		Vector3d rotatable = new Vector3d(vec.getX(), vec.getY(), vec.getZ());
		rotatable.rotate(quaternion);
		return new Vec3d(rotatable.x, rotatable.y, rotatable.z);
	}

	private Vec3d getTarget(Box targetBox, Vec3d dist, Entity entity) {
		return targetBox.getCenter().add(dist).subtract(0, entity.getHeight()/2, 0);
	}

	public boolean isLocked() {
		return lock != ContainerLock.EMPTY;
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

					Vec3d velocity = rotate(entity.getVelocity(), rotation);

					//Find position to place the player on other portal.
					var sourceBox = getBoundingBox();
					var targetBox = portal.getBoundingBox();
					Vec3d entityMovement = entity.getPos().subtract(entity.prevX, entity.prevY, entity.prevZ);
					if (entity.getVelocity().length() > entityMovement.length()) {
						entityMovement = entity.getVelocity();
					}
					Vec3d dist = entity.getBoundingBox().getCenter().subtract(entityMovement).subtract(sourceBox.getCenter());

					dist = new Vec3d(dist.getX() / (sourceBox.getLengthX()/2), dist.getY() / (sourceBox.getLengthY()/2), dist.getZ() / (sourceBox.getLengthZ()/2));

					dist = rotate(dist, rotation);
					dist = dist.multiply(targetBox.getLengthX()/2, targetBox.getLengthY()/2, targetBox.getLengthZ()/2);

					Box entityBox = getBoundingBoxIncludingPassengers(entity);

					Vec3d targetPos = getTarget(targetBox, dist, entity);

					var centeredBox = entityBox.offset(entity.getPos().multiply(-1));
					if (!targetDim.isSpaceEmpty(centeredBox.offset(targetPos))) {
						targetPos = getTarget(targetBox, dist, entity);
						if (!targetDim.isSpaceEmpty(centeredBox.offset(targetPos))) {
							if (entity instanceof ServerPlayerEntity player) {
								player.sendMessage(Text.literal("Could not deposit you safely on the other side"), true);
							}
							return entity;
						}
					}

					var facing = rotateYawPitch(
							entity.getYaw(), entity.getPitch(), getRotationToPortal(portal.portalFacing, entity)
					);

					newEntity = TeleportHelper.teleportEntity(
							entity,
							new TeleportTarget(
									targetDim, targetPos, velocity, facing.yaw, facing.pitch,
									TeleportTarget.NO_OP
							)
					);
				} else {
					newEntity = teleportNoFacing(entity, target);
				}
			} else {
				newEntity = teleportNoFacing(entity, target);
			}
		} else if (entity instanceof ServerPlayerEntity player) {
			player.sendMessage(Text.literal(targetRes.error().map(DataResult.Error::message).orElse("missingno")), true);
		}
		return newEntity;
	}

}
