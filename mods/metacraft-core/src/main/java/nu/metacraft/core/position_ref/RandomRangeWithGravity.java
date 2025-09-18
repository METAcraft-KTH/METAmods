package nu.metacraft.core.position_ref;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.predicate.FluidPredicate;
import net.minecraft.predicate.NumberRange;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.floatprovider.FloatProvider;
import net.minecraft.util.shape.VoxelShape;
import nu.metacraft.core.registry.PositionRefRegistry;
import nu.metacraft.core.util.RefContext;
import nu.metacraft.lib.util.ExtraCodecs;

import java.util.Optional;
import java.util.OptionalDouble;

public record RandomRangeWithGravity(
		PositionRef center, Box hitbox, Optional<FluidPredicate> validFluids,
		NumberRange.IntRange verticalRange, FloatProvider horizontalRange
) implements PositionRef {

	public static final MapCodec<RandomRangeWithGravity> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.lazyInitialized(() -> PositionRefRegistry.CODEC).fieldOf("center").forGetter(RandomRangeWithGravity::center),
					ExtraCodecs.BOX_CODEC.fieldOf("hitbox").forGetter(RandomRangeWithGravity::hitbox),
					FluidPredicate.CODEC.optionalFieldOf("valid_fluids").forGetter(RandomRangeWithGravity::validFluids),
					NumberRange.IntRange.CODEC.fieldOf("vertical_range").forGetter(RandomRangeWithGravity::verticalRange),
					FloatProvider.createValidatedCodec(0, Float.MAX_VALUE).fieldOf("horizontal_range").forGetter(RandomRangeWithGravity::horizontalRange)
			).apply(instance, RandomRangeWithGravity::new)
	);

	@Override
	public Optional<Vec3d> get(RefContext ctx) {
		return center.get(ctx).flatMap(centerPos -> {
			return findCandidatePos(ctx, centerPos);
		});
	}
	
	private OptionalDouble findValidY(ServerWorld world, double x, double z, BlockPos pos) {
		var state = world.getBlockState(pos);
		if (state.isFullCube(world, pos)) return OptionalDouble.empty();
		var shape = state.getCollisionShape(world, pos);
		if (shape.isEmpty()) {
			return isValidPos(world, x, pos.getY(), z, shape) ? OptionalDouble.of(pos.getY()) : OptionalDouble.empty();
		} else {
			var box = shape.getBoundingBox();
			return box.maxY < 1 && isValidPos(world, x, pos.getY() + shape.getBoundingBox().maxY, z, shape) ?
					OptionalDouble.of(pos.getY() + shape.getBoundingBox().maxY) : OptionalDouble.empty();
		}
	}
	
	private boolean isValidPos(ServerWorld world, double x, double y, double z, VoxelShape posShape) {
		var box = hitbox.offset(x, y, z);
		if (world.isSpaceEmpty(box)) {
			if (validFluids.isPresent()) {
				for (var pos : BlockPos.iterate(box)) {
					if (!validFluids.get().test(world, pos)) {
						return false;
					}
				}
			}
			if (!posShape.isEmpty()) {
				return true;
			} else {
				int minX = MathHelper.floor(box.minX);
				int minZ = MathHelper.floor(box.minZ);
				int maxX = MathHelper.floor(box.maxX);
				int maxZ = MathHelper.floor(box.maxZ);
				int yInt = MathHelper.floor(y)-1;
				for (var pos : BlockPos.iterate(minX, yInt, minZ, maxX, yInt, maxZ)) {
					if (world.getBlockState(pos).isFullCube(world, pos)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private Optional<Vec3d> findCandidatePos(RefContext ctx, Vec3d centerPos) {
		var angle = ctx.getRandom().nextDouble() * Math.PI * 2;
		double length = horizontalRange.get(ctx.getRandom());
		double xOffset = Math.cos(angle) * length;
		double zOffset = Math.sin(angle) * length;
		var targetPos = centerPos.add(xOffset, 0, zOffset);
		BlockPos.Mutable reusedBlockPos = new BlockPos.Mutable();
		reusedBlockPos.set(targetPos.getX(), targetPos.getY(), targetPos.getZ());
		int startY = reusedBlockPos.getY();
		int minY = verticalRange.bounds().min().map(y -> y + startY).orElse(ctx.getWorld().getBottomY());
		int maxY = verticalRange.bounds().max().map(y -> y + startY).orElse(ctx.getWorld().getTopYInclusive());
		int count = Math.max(startY - minY, maxY - startY);
		for (int i = 0; i < count; i++) {
			if (verticalRange.test(-i)) {
				reusedBlockPos.setY(startY - i);
				var y = findValidY(ctx.getWorld(), targetPos.getX(), targetPos.getZ(), reusedBlockPos);
				if (y.isPresent()) {
					return Optional.of(new Vec3d(targetPos.getX(), y.getAsDouble(), targetPos.getZ()));
				}
			}
			if (verticalRange.test(i)) {
				reusedBlockPos.setY(startY + i);
				var y = findValidY(ctx.getWorld(), targetPos.getX(), targetPos.getZ(), reusedBlockPos);
				if (y.isPresent()) {
					return Optional.of(new Vec3d(targetPos.getX(), y.getAsDouble(), targetPos.getZ()));
				}
			}
		}
		return Optional.empty();
	}

	@Override
	public PositionRefType<?> getType() {
		return PositionRefRegistry.RANDOM_RANGE_WITH_GRAVITY;
	}
}
