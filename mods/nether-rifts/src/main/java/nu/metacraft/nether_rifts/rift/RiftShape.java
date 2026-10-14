package nu.metacraft.nether_rifts.rift;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The blocks a rift fills, the plane they lie on, and the frame around them: every in-plane neighbour of a
 * rift block that isn't one, with the block types it was made of when the rift formed.
 */
public record RiftShape(Set<BlockPos> blocks, Direction.Axis axis, Set<BlockPos> frame, Set<Block> frameMaterials) {

	public static final int MIN_AREA = 6;
	public static final int MAX_SIDE = 21;

	private static final Codec<Set<BlockPos>> POSITIONS = Codec.LONG_STREAM.xmap(
			longs -> longs.mapToObj(BlockPos::of).collect(Collectors.toUnmodifiableSet()),
			set -> set.stream().mapToLong(BlockPos::asLong)
	);

	public static final Codec<RiftShape> CODEC = RecordCodecBuilder.create(i -> i.group(
			POSITIONS.fieldOf("blocks").forGetter(RiftShape::blocks),
			Direction.Axis.CODEC.fieldOf("axis").forGetter(RiftShape::axis),
			POSITIONS.fieldOf("frame").forGetter(RiftShape::frame),
			BuiltInRegistries.BLOCK.byNameCodec().listOf().xmap(Set::copyOf, List::copyOf).fieldOf("frame_materials").forGetter(RiftShape::frameMaterials)
	).apply(i, RiftShape::new));

	/** Whether the block at a position frames a rift. */
	@FunctionalInterface
	public interface FrameTest {
		boolean isFrame(LevelReader level, BlockPos pos);

		static FrameTest of(Predicate<BlockState> test) {
			return (level, pos) -> test.test(level.getBlockState(pos));
		}
	}

	public RiftShape {
		blocks = Set.copyOf(blocks);
		frame = Set.copyOf(frame);
		frameMaterials = Set.copyOf(frameMaterials);
	}

	public int size() {
		return blocks.size();
	}

	public Vec3 centre() {
		double x = 0, y = 0, z = 0;
		for (var p : blocks) {
			x += p.getX();
			y += p.getY();
			z += p.getZ();
		}
		int n = blocks.size();
		return new Vec3(x / n + 0.5, y / n + 0.5, z / n + 0.5);
	}

	public BlockPos centreBlock() {
		return BlockPos.containing(centre());
	}

	/** Every frame position still holds one of the blocks the frame was built from. */
	public boolean frameIntact(BlockGetter level) {
		for (var pos : frame) {
			if (!frameMaterials.contains(level.getBlockState(pos).getBlock())) {
				return false;
			}
		}
		return true;
	}

	public static List<Direction> inPlane(Direction.Axis axis) {
		return List.of(
				Direction.UP, Direction.DOWN,
				Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE),
				Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE)
		);
	}

	/** A rift over the given blocks, if every in-plane neighbour outside them is frame. */
	public static Optional<RiftShape> of(LevelReader level, Collection<BlockPos> positions, Direction.Axis axis, FrameTest isFrame) {
		Set<BlockPos> blocks = positions.stream().map(BlockPos::immutable).collect(Collectors.toSet());
		if (blocks.isEmpty()) {
			return Optional.empty();
		}
		Set<BlockPos> frame = new HashSet<>();
		Set<Block> materials = new HashSet<>();
		for (var pos : blocks) {
			for (var dir : inPlane(axis)) {
				var n = pos.relative(dir);
				if (blocks.contains(n)) {
					continue;
				}
				if (!isFrame.isFrame(level, n)) {
					return Optional.empty();
				}
				frame.add(n);
				materials.add(level.getBlockState(n).getBlock());
			}
		}
		return Optional.of(new RiftShape(blocks, axis, frame, materials));
	}

	/**
	 * Flood-fills the open space at {@code start} on the X plane, then the Z plane; the shape may be at most
	 * {@link #MAX_SIDE} blocks wide and {@link #MAX_SIDE} high, wherever in it {@code start} lies.
	 */
	public static Optional<RiftShape> fill(LevelReader level, BlockPos start, FrameTest isFrame) {
		return fill(level, start, Direction.Axis.X, isFrame).or(() -> fill(level, start, Direction.Axis.Z, isFrame));
	}

	private static Optional<RiftShape> fill(LevelReader level, BlockPos start, Direction.Axis axis, FrameTest isFrame) {
		if (!isOpen(level.getBlockState(start))) {
			return Optional.empty();
		}
		var inside = new HashSet<BlockPos>();
		var queue = new ArrayDeque<BlockPos>();
		inside.add(start.immutable());
		queue.add(start.immutable());
		int minY = start.getY(), maxY = start.getY();
		int minA = start.get(axis), maxA = start.get(axis);
		while (!queue.isEmpty()) {
			var pos = queue.poll();
			for (var dir : inPlane(axis)) {
				var n = pos.relative(dir);
				if (inside.contains(n) || isFrame.isFrame(level, n)) {
					continue;
				}
				if (!isOpen(level.getBlockState(n))) {
					return Optional.empty();
				}
				minY = Math.min(minY, n.getY());
				maxY = Math.max(maxY, n.getY());
				minA = Math.min(minA, n.get(axis));
				maxA = Math.max(maxA, n.get(axis));
				if (maxY - minY + 1 > MAX_SIDE || maxA - minA + 1 > MAX_SIDE || inside.size() >= MAX_SIDE * MAX_SIDE) {
					return Optional.empty();
				}
				inside.add(n);
				queue.add(n);
			}
		}
		if (inside.size() < MIN_AREA) {
			return Optional.empty();
		}
		return of(level, inside, axis, isFrame);
	}

	private static boolean isOpen(BlockState state) {
		return state.isAir() || state.is(BlockTags.FIRE);
	}
}
