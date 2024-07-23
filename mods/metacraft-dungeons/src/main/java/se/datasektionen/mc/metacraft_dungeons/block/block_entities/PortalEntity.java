package se.datasektionen.mc.metacraft_dungeons.block.block_entities;

import com.mojang.datafixers.util.Pair;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
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
import se.datasektionen.mc.metacraft_dungeons.METAcraftDungeons;
import se.datasektionen.mc.metacraft_dungeons.block.DungeonBlocks;
import se.datasektionen.mc.metacraft_dungeons.block.DungeonsBlockEntities;
import se.datasektionen.mc.metacraft_dungeons.dungeons.DungeonData;
import se.datasektionen.mc.metacraft_dungeons.dungeons.TeleportPredicate;
import se.datasektionen.mc.metacraft_dungeons.util.Teleporter;

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
	protected Direction portalFacing;
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

	public void setPortalFacing(Direction facing) {
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
			portalFacing = Direction.byName(nbt.getString(PORTAL_FACING));
		} else {
			portalFacing = null;
		}
		if (nbt.contains(TELEPORT_PETS)) {
			teleportPets = nbt.getBoolean(TELEPORT_PETS);
		}
		if (nbt.contains(SHOULD_TELEPORT)) {
			TeleportPredicate.LIST_CODEC.parse(NbtOps.INSTANCE, nbt.get(SHOULD_TELEPORT)).resultOrPartial(
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
			nbt.putString(PORTAL_FACING, portalFacing.getName());
		}
		nbt.putBoolean(TELEPORT_PETS, teleportPets);

		TeleportPredicate.LIST_CODEC.encodeStart(NbtOps.INSTANCE, shouldTeleport).resultOrPartial(
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
						portalFacing = facing;
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

	private static Pair<Float, Float> fix(Pair<Float, Float> toFix) {
		if (Math.abs(toFix.getSecond()) > 90) {
			var pitchOffset = toFix.getSecond() < 0 ? -90 : 90;
			var diff = toFix.getSecond() - pitchOffset;
			return Pair.of(MathHelper.wrapDegrees((toFix.getFirst() + 180)), pitchOffset - diff);
		}
		if (Math.abs(toFix.getFirst()) > 180) {
			return Pair.of(MathHelper.wrapDegrees(toFix.getFirst()), toFix.getSecond());
		}
		return toFix;
	}

	private float getAngleBetweenDirections(Direction source, Direction target) {
		if (source == target) {
			return 0;
		} else if (source == target.getOpposite()) {
			return 180;
		} else {
			if (source.getAxis().isVertical()) {
				return -source.getDirection().offset() * 90;
			} else if (target.getAxis().isVertical()) {
				return -source.getDirection().offset() * 90;
			} else {
				if (source.rotateClockwise(Direction.Axis.Y) == target) {
					return 90;
				} else {
					return -90;
				}
			}
		}
	}

	private Pair<Float, Float> getTargetFacing(Direction sourceDirection, Direction targetDirection, float yaw, float pitch) {
		float angle = getAngleBetweenDirections(sourceDirection, targetDirection);
		if (sourceDirection.getAxis().isHorizontal() && targetDirection.getAxis().isHorizontal()) {
			return fix(Pair.of(yaw + angle, pitch));
		} else if (sourceDirection.getAxis().isVertical() && targetDirection.getAxis().isHorizontal()) {
			float horisontalAngle = getAngleBetweenDirections(Direction.fromRotation(yaw), targetDirection);
			return fix(Pair.of(yaw + horisontalAngle, pitch + angle));
		} else if (sourceDirection.getAxis().isHorizontal() && targetDirection.getAxis().isVertical()) {
			float horisontalAngle = getAngleBetweenDirections(Direction.fromRotation(yaw), sourceDirection);
			return fix(Pair.of(yaw, pitch + angle - horisontalAngle));
		} else {
			return fix(Pair.of(yaw, pitch + angle));
		}
	}


	private Pair<Float, Float> getRotationToPortal(
			Direction otherFacing, Entity entity
	) {
		if (portalFacing == null || otherFacing == null) {
			return Pair.of(entity.getYaw(), entity.getPitch());
		}
		if (getUnit(entity.getPos().subtract(Vec3d.ofBottomCenter(pos)).getComponentAlongAxis(portalFacing.getAxis())) == portalFacing.getDirection().offset()) {
			return getTargetFacing(portalFacing.getOpposite(), otherFacing, entity.getYaw(), entity.getPitch());
		} else {
			return getTargetFacing(portalFacing, otherFacing, entity.getYaw(), entity.getPitch());
		}
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
					x * portalFacing.getOffsetX(),
					y * portalFacing.getOffsetY(),
					z * portalFacing.getOffsetZ()
			);
			box = box.stretch(
					x * -portalFacing.getOffsetX(),
					y * -portalFacing.getOffsetY(),
					z * -portalFacing.getOffsetZ()
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

	private int getUnit(double num) {
		if (num == 0) return 0;
		return num < 0 ? -1 : 1;
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
					var rotation = getRotationToPortal(portal.portalFacing, entity);

					//Rotate velocity.
					Vec3d velocity = entity.getVelocity();
					velocity = velocity.rotateY(-(float) Math.toRadians(rotation.getFirst() - entity.getYaw()));
					velocity = velocity.rotateX((float) Math.toRadians(rotation.getSecond() - entity.getPitch()));

					//Find position to place the player on other portal.
					var sourceBox = getBoundingBox();
					var targetBox = portal.getBoundingBox();
					Vec3d dist = entity.getPos().subtract(sourceBox.getCenter());
					dist = new Vec3d(dist.getX() / (sourceBox.getLengthX()/2), dist.getY() / (sourceBox.getLengthY()/2), dist.getZ() / (sourceBox.getLengthZ()/2));
					dist = dist.rotateY(-(float) Math.toRadians(rotation.getFirst() - entity.getYaw()));
					dist = dist.rotateX((float) Math.toRadians(rotation.getSecond() - entity.getPitch()));
					dist = dist.multiply(targetBox.getLengthX()/2, targetBox.getLengthY()/2, targetBox.getLengthZ()/2);

					Box entityBox = getBoundingBoxIncludingPassengers(entity);

					switch (portal.portalFacing.getAxis()) {
						case X -> {
							dist = dist.add(entityBox.getLengthX() * -getUnit(dist.getX()), 0, 0);
						}
						case Y -> {
							dist = dist.add(0, entityBox.getLengthY() * -getUnit(dist.getY()), 0);
						}
						case Z -> {
							dist = dist.add(0, 0, entityBox.getLengthZ() * -getUnit(dist.getZ()));
						}
					}

					Vec3d targetPos = targetBox.getCenter().add(dist);

					newEntity = entity.teleportTo(
							new TeleportTarget(
									targetDim, targetPos, velocity, rotation.getFirst(), rotation.getSecond(),
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
