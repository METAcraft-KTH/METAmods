package se.datasektionen.mc.metacraft_dungeons.block.block_entities;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.enums.Orientation;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.collection.ArrayListDeque;
import net.minecraft.util.math.*;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.joml.*;
import se.datasektionen.mc.metacraft_dungeons.METAcraftDungeons;
import se.datasektionen.mc.metacraft_dungeons.block.DungeonBlocks;
import se.datasektionen.mc.metacraft_dungeons.block.DungeonsBlockEntities;
import se.datasektionen.mc.metacraft_dungeons.dungeons.DungeonData;
import se.datasektionen.mc.metacraft_dungeons.dungeons.TeleportPredicate;
import se.datasektionen.mc.metacraft_dungeons.util.Teleporter;
import se.datasektionen.mc.metacraft_lib.util.ExtraCodecs;
import se.datasektionen.mc.metacraft_lib.util.helper.OrientationHelper;

import java.lang.Math;
import java.util.*;

public class PortalEntity extends BlockEntity {

	private static final String TARGET_DIM = "TargetDim";
	private static final String TARGET_POS = "TargetPos";
	private static final String PORTAL_FACING = "PortalFacing";
	private static final String TELEPORT_PETS = "TeleportPets";
	private static final String SHOULD_TELEPORT = "ShouldTeleport";

	public static final int MAX_SEARCH_BLOCKS = 100;
	private static final int RANGE_CHECK = 10;

	protected RegistryKey<World> targetDim;
	protected BlockPos targetPos;
	protected Orientation portalFacing;
	protected boolean teleportPets = true;
	protected final List<TeleportPredicate> shouldTeleport = new ArrayList<>();

	public PortalEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public PortalEntity(BlockPos pos, BlockState state) {
		super(DungeonsBlockEntities.PORTAL, pos, state);
	}

	public void setTargetPos(BlockPos pos) {
		targetPos = pos;
		markDirty();
	}

	public void setTargetDim(RegistryKey<World> dim) {
		this.targetDim = dim;
		markDirty();
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

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapperLookup) {
		super.readNbt(nbt, wrapperLookup);
		if (nbt.contains(TARGET_DIM)) {
			targetDim = World.CODEC.parse(NbtOps.INSTANCE, nbt.get(TARGET_DIM)).resultOrPartial(
					METAcraftDungeons.LOGGER::error
			).orElse(null);
		} else {
			targetDim = null;
		}
		if (nbt.contains(TARGET_POS)) {
			targetPos = NbtHelper.toBlockPos(nbt, TARGET_POS).orElse(null);
		} else {
			targetPos = null;
		}
		if (nbt.contains(PORTAL_FACING)) {
			portalFacing = ExtraCodecs.ORIENTATION_CODEC.parse(NbtOps.INSTANCE, nbt.get(PORTAL_FACING)).resultOrPartial(
					METAcraftDungeons.LOGGER::error
			).orElse(null);
		} else {
			portalFacing = null;
		}
		if (nbt.contains(TELEPORT_PETS)) {
			teleportPets = nbt.getBoolean(TELEPORT_PETS);
		}
		if (nbt.contains(SHOULD_TELEPORT)) {
			TeleportPredicate.LIST_CODEC.parse(wrapperLookup.getOps(NbtOps.INSTANCE), nbt.get(SHOULD_TELEPORT)).resultOrPartial(
					METAcraftDungeons.LOGGER::error
			).ifPresent(this.shouldTeleport::addAll);
		}
	}

	@Override
	public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapperLookup) {
		super.writeNbt(nbt, wrapperLookup);
		if (targetDim != null) {
			World.CODEC.encodeStart(NbtOps.INSTANCE, targetDim).resultOrPartial(
					METAcraftDungeons.LOGGER::error
			).ifPresent(dim -> {
				nbt.put(TARGET_DIM, dim);
			});
		}
		if (targetPos != null) {
			nbt.put(TARGET_POS, NbtHelper.fromBlockPos(targetPos));
		}
		if (portalFacing != null) {
			ExtraCodecs.ORIENTATION_CODEC.encodeStart(NbtOps.INSTANCE, portalFacing).resultOrPartial(
					METAcraftDungeons.LOGGER::error
			).ifPresent(facing -> {
				nbt.put(PORTAL_FACING, facing);
			});
		}
		nbt.putBoolean(TELEPORT_PETS, teleportPets);

		TeleportPredicate.LIST_CODEC.encodeStart(wrapperLookup.getOps(NbtOps.INSTANCE), shouldTeleport).resultOrPartial(
				METAcraftDungeons.LOGGER::error
		).ifPresent(shouldTeleport -> {
			nbt.put(SHOULD_TELEPORT, shouldTeleport);
		});
	}

	public ServerWorld getTargetDim() {
		if (world == null || world.isClient()) return null;
		if (targetDim != null) {
			var dim = world.getServer().getWorld(targetDim);
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
		return forAllNearbyPortals(world, pos, MAX_SEARCH_BLOCKS);
	}

	public static Iterable<BlockPos> forAllNearbyPortals(World world, BlockPos pos) {
		return forAllNearbyPortals(world, pos, MAX_SEARCH_BLOCKS);
	}

	public static Iterable<BlockPos> forAllNearbyPortals(World world, BlockPos pos, int maxBlocks) {
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
								world.getBlockState(checker).isOf(DungeonBlocks.DUMMY_PORTAL) ||
								world.getBlockEntity(checker) instanceof PortalEntity
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

	public void computeTargetFacing() {
		if (targetPos != null) {
			var targetDim = getTargetDim();
			if (targetDim.getBlockEntity(targetPos) instanceof PortalEntity portal) {
				if (portal.portalFacing != null) return;
				portal.computeFacing();
			}
		}
	}

	private Entity teleportNoFacing(Entity entity) {
		var targetDim = getTargetDim();
		return entity.teleportTo(
				new TeleportTarget(
						targetDim, Vec3d.ofBottomCenter(targetPos), entity.getVelocity(), entity.getYaw(), entity.getPitch(),
						teleportPets ? Teleporter.getTeleportPets((ServerWorld) this.getWorld()) : TeleportTarget.NO_OP
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
			return new Quaterniond().rotateXYZ(0, angle, 0);
		} else if (OrientationHelper.isVertical(sourceDirection) && OrientationHelper.isHorizontal(targetDirection)) {
			int offsetAngle = entityFacing != null ? -getAngleBetweenDirections(
					entityFacing, sourceDirection.getRotation()
			) : 0;
			var horizontalAngle = -getAngleBetweenDirections(sourceDirection.getRotation(), targetDirection.getFacing());
			return new Quaterniond().rotateXYZ(angle, 0, Math.toRadians(horizontalAngle + offsetAngle));
		} else if (OrientationHelper.isHorizontal(sourceDirection) && OrientationHelper.isVertical(targetDirection)) {
			var horizontalAngle = Math.toRadians(-getAngleBetweenDirections(sourceDirection.getFacing(), targetDirection.getRotation()));
			return new Quaterniond().rotateXYZ(angle, horizontalAngle, 0);
		} else {
			var horizontalAngle = Math.toRadians(getAngleBetweenDirections(sourceDirection.getRotation(), targetDirection.getRotation()));
			return new Quaterniond().rotateXYZ(angle, horizontalAngle, 0);
		}
	}

	private Angles rotateYawPitch(float yaw, float pitch, Quaterniond quaternion, boolean fixPitch) {
		var angles = quaternion.getEulerAnglesXYZ(new Vector3d()).mul(MathHelper.DEGREES_PER_RADIAN);
		//TODO Handle angles.z
		return fix(new Angles(
				(float) -angles.y + yaw, //Why fixPitch? Because sometimes the negative pitch is the correct one and sometimes the positive one... This code needs more refinement.
				(float) (fixPitch ? -angles.x : angles.x) + pitch
		));
	}

	private Quaterniond getRotationToPortal(Orientation otherFacing, Entity entity) {
		if (portalFacing == null || otherFacing == null) {
			return new Quaterniond();
		}
		return getQuaternion(Orientation.byDirections(portalFacing.getFacing().getOpposite(), portalFacing.getRotation()), otherFacing, entity != null ? entity.getHorizontalFacing() : null);
	}

	public Box getBoundingBox() {
		return Box.from(BlockBox.encompassPositions(forAllNearbyPortals()).orElse(BlockBox.create(pos, pos)));
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

	public void onCollision(BlockState state, World world, BlockPos pos, Entity entity) {
		if (entity.hasVehicle()) return;
		Box box = getBoundingBox();
		Box entityBox = getBoundingBoxIncludingPassengers(entity);
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
			world.getServer().execute(() -> {
				Entity toTP = entity;
				if (toTP.getPortalCooldown() <= 0) {
					toTP = teleport(toTP);
				}
				toTP.setPortalCooldown(2);
			});
		}
	}

	private static int getUnit(double num) {
		if (num == 0) return 0;
		return num < 0 ? -1 : 1;
	}

	private static Vec3d rotate(Vec3d vec, Quaterniond quaternion) {
		Vector3d rotatable = new Vector3d(vec.getX(), vec.getY(), vec.getZ());
		rotatable.rotate(quaternion);
		return new Vec3d(rotatable.x, rotatable.y, rotatable.z);
	}

	private boolean fixPitch(Orientation source, Orientation target) {
		if (OrientationHelper.isVertical(source) && OrientationHelper.isHorizontal(target)) {
			return true;
		}
		return false;
	}

	public Entity teleport(Entity entity) {
		if (!shouldTeleport(entity)) {
			onTeleportFail(entity);
			return entity;
		}
		if (portalFacing == null) {
			computeFacing();
		}
		Entity newEntity = entity;
		if (targetPos != null) {
			computeTargetFacing();
			var targetDim = getTargetDim();
			if (targetDim != world && DungeonData.getIfPresent(targetDim).map(DungeonData::isResetting).orElse(false)) {
				if (entity instanceof ServerPlayerEntity player) {
					player.sendMessage(Text.literal("Dungeon dimension resetting, please wait."));
				}
				return entity;
			}
			if (targetDim.getBlockEntity(targetPos) instanceof PortalEntity portal) {
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

					double boxLength = switch (portalFacing.getFacing().getAxis()) {
						case X -> entityBox.getLengthX();
						case Y -> entityBox.getLengthY();
						case Z -> entityBox.getLengthZ();
					};

					switch (portal.portalFacing.getFacing().getAxis()) {
						case X -> {
							dist = dist.add(boxLength * -getUnit(dist.getX()), 0, 0);
						}
						case Y -> {
							dist = dist.add(0, boxLength * -getUnit(dist.getY()), 0);
						}
						case Z -> {
							dist = dist.add(0, 0, boxLength * -getUnit(dist.getZ()));
						}
					}


					Vec3d targetPos = targetBox.getCenter().add(dist).subtract(0, entity.getHeight()/2, 0);

					var facing = rotateYawPitch(
							entity.getYaw(), entity.getPitch(), getRotationToPortal(portal.portalFacing, entity),
							fixPitch(portalFacing, portal.portalFacing)
					);

					newEntity = entity.teleportTo(
							new TeleportTarget(
									targetDim, targetPos, velocity, facing.yaw, facing.pitch,
									teleportPets ? Teleporter.getTeleportPets((ServerWorld) this.getWorld()) : TeleportTarget.NO_OP
							)
					);

				} else {
					newEntity = teleportNoFacing(entity);
				}
			} else {
				newEntity = teleportNoFacing(entity);
			}
		} else if (entity instanceof ServerPlayerEntity player) {
			player.sendMessage(Text.literal("Portal had no target"), true);
		}
		return newEntity;
	}

}
