package se.metacraft.portalopening.rifts;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import se.metacraft.portalopening.EntityData;
import se.metacraft.portalopening.WorldData;
import se.metacraft.portalopening.raid.Wave;

import java.util.*;
import java.util.function.Predicate;

public class PortalRift {

	private static final String BLOCKS = "Blocks";
	private static final String AXIS = "Axis";
	private static final String LAUNCH_DIRECTION = "LaunchDirection";
	private static final String LAUNCH_STRENGTH = "LaunchStrength";
	private static final String OFFSET_FACTOR = "OffsetFactor";

	private static final Codec<List<BlockPos>> POSITIONS_CODEC = Codec.LONG_STREAM.xmap(
			positions -> positions.mapToObj(BlockPos::fromLong).toList(),
			positions -> positions.stream().mapToLong(BlockPos::asLong)
	);

	public static Codec<PortalRift> createCodec(ServerWorld world) {
		return RecordCodecBuilder.create(instance -> instance.group(
				POSITIONS_CODEC.fieldOf(BLOCKS).forGetter(r -> r.blocks),
				Direction.Axis.CODEC.optionalFieldOf(AXIS).forGetter(r -> Optional.ofNullable(r.axis)),
				Direction.CODEC.optionalFieldOf(LAUNCH_DIRECTION).forGetter(r -> Optional.ofNullable(r.launchDirection)),
				Codec.DOUBLE.fieldOf(LAUNCH_STRENGTH).forGetter(r -> r.launchStrength),
				Codec.DOUBLE.fieldOf(OFFSET_FACTOR).forGetter(r -> r.offsetFactor)
		).apply(
				instance,
				(blocks, axis, direction, strength, factor) -> new PortalRift(
						world, blocks, axis.orElse(null), direction.orElse(null), strength, factor
				)
		));
	}

	protected final ServerWorld world;
	protected List<BlockPos> blocks = new ArrayList<>();
	protected Set<BlockPos> blocksChecker = new HashSet<>();
	protected Direction.Axis axis;
	protected Direction launchDirection = null;
	protected double launchStrength = 1;
	protected double offsetFactor = 0.5;

	protected Set<Entity> mobs = new HashSet<>();

	protected Runnable shouldSave;

	protected int delay = 0;

	private PortalRift(
			ServerWorld world, List<BlockPos> blocks,
			Direction.Axis axis, Direction launchDirection,
			double launchStrength, double offsetFactor
	) {
		this.world = world;
		this.blocks.addAll(blocks);
		this.blocksChecker.addAll(blocks);
		this.axis = axis;
		this.launchDirection = launchDirection;
		this.launchStrength = launchStrength;
		this.offsetFactor = offsetFactor;
	}

	public PortalRift(
			ServerWorld world, BlockPos pos, int size, Direction.Axis axis, Runnable shouldSave
	) {
		this.world = world;
		this.shouldSave = shouldSave;
		choosePositions(pos, size, axis);
	}

	public PortalRift(ServerWorld world, BlockPos pos1, BlockPos pos2, Runnable shouldSave) {
		this.world = world;
		if (pos1.getX() == pos2.getX()) {
			axis = Direction.Axis.X;
		}
		if (pos1.getZ() == pos2.getZ()) {
			axis = Direction.Axis.Z;
		}
		this.shouldSave = shouldSave;
		BlockPos.iterate(pos1, pos2).forEach(pos -> {
			var foundPos = pos.toImmutable();
			blocks.add(foundPos);
			blocksChecker.add(foundPos);
		});
	}

	public PortalRift(
			ServerWorld world, BlockPos pos, int maxSize,
			Direction.Axis axis, Predicate<BlockState> blockChecker,
			Runnable shouldSave
	) {
		this.world = world;
		this.shouldSave = shouldSave;
		assert axis != Direction.Axis.Y;
		this.axis = axis;
		var positions = BlockPos.iterateOutwards(
				pos, axis == Direction.Axis.X ? maxSize : 0,
				maxSize, axis == Direction.Axis.Z ? maxSize : 0
		);
		for (BlockPos target : positions) {
			if (blockChecker.test(world.getBlockState(target))) {
				var foundPos = target.toImmutable();
				blocks.add(foundPos);
				this.blocksChecker.add(foundPos);
			}
		}
	}

	public void setSaveCallback(Runnable shouldSave) {
		this.shouldSave = shouldSave;
	}

	public boolean successful() {
		return !blocks.isEmpty();
	}

	private void choosePositions(BlockPos center, int size, Direction.Axis chosenAxis) {
		assert chosenAxis != Direction.Axis.Y;
		Vec3i[] offsets = new Vec3i[4];
		if (chosenAxis != null) {
			this.axis = chosenAxis;
		} else {
			this.axis = world.random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
		}
		offsets[0] = Direction.UP.getVector();
		offsets[1] = Direction.DOWN.getVector();
		offsets[2] = Direction.from(axis, Direction.AxisDirection.POSITIVE).getVector();
		offsets[3] = Direction.from(axis, Direction.AxisDirection.NEGATIVE).getVector();

		BlockPos.Mutable pos = new BlockPos.Mutable().set(center);
		for (int i = 0; i < size * size; i++) {
			if (isReplaceableBlock(pos)) {
				var newPos = pos.toImmutable();
				blocks.add(newPos);
				blocksChecker.add(newPos);
			}
			pos.set(pos, offsets[world.getRandom().nextInt(offsets.length)]);
		}
	}

	public void replaceBlocks(BlockState state) {
		for (BlockPos pos : blocks) {
			((WorldData) world).portalOpening$setBlockNoTrigger(pos, state);
		}
	}

	public void addBlocksFromRift(PortalRift rift) {
		this.blocks.addAll(rift.blocks);
		this.blocksChecker.addAll(rift.blocksChecker);
		markDirty();
	}

	public Optional<Direction.Axis> getAxis() {
		return Optional.ofNullable(axis);
	}

	public void removeAxis() {
		axis = null;
	}

	public boolean isReplaceableBlock(BlockPos pos) {
		return world.getBlockState(pos).isAir();
	}

	public boolean containsPos(BlockPos pos) {
		return blocksChecker.contains(pos);
	}

	public BlockPos getRandomPos() {
		return blocks.get(world.getRandom().nextInt(blocks.size()));
	}

	public void addEntity(Entity entity) {
		mobs.add(entity);
		((EntityData) entity).portalOpening$setRift(this);
	}

	public void removeEntity(Entity entity) {
		mobs.remove(entity);
		((EntityData) entity).portalOpening$setRift(null);
	}

	public void attemptLaunch(Entity entity) {
		if (launchDirection != null) {
			List<Direction.Axis> axes = new ArrayList<>(List.of(Direction.Axis.values()));
			axes.remove(launchDirection.getAxis());
			var facing = Vec3d.of(launchDirection.getVector()).add(
					Vec3d.of(launchDirection.rotateClockwise(axes.getFirst()).getVector()).multiply(
							entity.getRandom().nextGaussian() * offsetFactor
					)
			).add(
					Vec3d.of(launchDirection.rotateClockwise(axes.getLast()).getVector()).multiply(
							entity.getRandom().nextGaussian() * offsetFactor
					)
			).normalize().multiply(launchStrength);
			entity.setVelocity(facing);
		}
	}

	public void setLaunch(Direction direction, double strength, double offset) {
		launchDirection = direction;
		launchStrength = strength;
		this.offsetFactor = offset;
		markDirty();
	}

	public void nextWave(Wave wave) {
		delay = wave.autoSpawns().map(spawns -> spawns.spawnDelay().get(world.getRandom())).orElse(0);
		clearMobs();
		markDirty();
	}

	public void tick(Wave wave, boolean isMainRift) {
		wave.autoSpawns().ifPresent(autoSpawns -> {
			if (delay > 0) {
				delay--;
				markDirty();
			} else if (mobs.size() < autoSpawns.maxMobs()) {
				int toSpawn = 0;
				if (!isMainRift) {
					if (world.getRandom().nextDouble() > autoSpawns.entry().probabilityToSpawnOtherRift()) {
						delay = autoSpawns.spawnDelay().get(world.getRandom());
						return;
					}
					toSpawn = autoSpawns.entry().amountPerSpawnOtherRifts().map(num -> num.get(world.getRandom())).orElse(0);
				} else {
					toSpawn = autoSpawns.entry().amountPerSpawn().get(world.getRandom());
				}
				toSpawn = Math.min(toSpawn, autoSpawns.maxMobs() - mobs.size());
				for (int i = 0; i < toSpawn; i++) {
					autoSpawns.spawnMobsFromNBT(world, getRandomPos(), this, this::attemptLaunch);
				}
				delay = autoSpawns.spawnDelay().get(world.getRandom());
				markDirty();
			}
		});
	}

	public void clearMobs() {
		mobs.forEach(entity -> {
			((EntityData) entity).portalOpening$setRift(null);
		});
		mobs.clear();
	}

	public void markDirty() {
		shouldSave.run();
	}

}
