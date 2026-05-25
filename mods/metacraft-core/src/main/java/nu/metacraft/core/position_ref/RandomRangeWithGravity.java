package nu.metacraft.core.position_ref;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.valueproviders.FloatProviders;
import nu.metacraft.core.registry.PositionRefRegistry;
import nu.metacraft.core.util.RefContext;
import nu.metacraft.lib.util.METACodecs;

import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.advancements.criterion.FluidPredicate;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public record RandomRangeWithGravity(
		PositionRef center, AABB hitbox, Optional<FluidPredicate> validFluids,
		MinMaxBounds.Ints verticalRange, FloatProvider horizontalRange
) implements PositionRef {

	public static final MapCodec<RandomRangeWithGravity> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.lazyInitialized(() -> PositionRefRegistry.CODEC).fieldOf("center").forGetter(RandomRangeWithGravity::center),
					METACodecs.BOX_CODEC.fieldOf("hitbox").forGetter(RandomRangeWithGravity::hitbox),
					FluidPredicate.CODEC.optionalFieldOf("valid_fluids").forGetter(RandomRangeWithGravity::validFluids),
					MinMaxBounds.Ints.CODEC.fieldOf("vertical_range").forGetter(RandomRangeWithGravity::verticalRange),
					FloatProviders.codec(0, Float.MAX_VALUE).fieldOf("horizontal_range").forGetter(RandomRangeWithGravity::horizontalRange)
			).apply(instance, RandomRangeWithGravity::new)
	);

	@Override
	public Optional<Vec3> get(RefContext ctx) {
		return center.get(ctx).flatMap(centerPos -> {
			return findCandidatePos(ctx, centerPos);
		});
	}
	
	private OptionalDouble findValidY(ServerLevel world, double x, double z, BlockPos pos) {
		var state = world.getBlockState(pos);
		if (state.isCollisionShapeFullBlock(world, pos)) return OptionalDouble.empty();
		var shape = state.getCollisionShape(world, pos);
		if (shape.isEmpty()) {
			return isValidPos(world, x, pos.getY(), z, shape) ? OptionalDouble.of(pos.getY()) : OptionalDouble.empty();
		} else {
			var box = shape.bounds();
			return box.maxY < 1 && isValidPos(world, x, pos.getY() + shape.bounds().maxY, z, shape) ?
					OptionalDouble.of(pos.getY() + shape.bounds().maxY) : OptionalDouble.empty();
		}
	}
	
	private boolean isValidPos(ServerLevel world, double x, double y, double z, VoxelShape posShape) {
		var box = hitbox.move(x, y, z);
		if (world.noCollision(box)) {
			if (validFluids.isPresent()) {
				for (var pos : BlockPos.betweenClosed(box)) {
					if (!validFluids.get().matches(world, pos)) {
						return false;
					}
				}
			}
			if (!posShape.isEmpty()) {
				return true;
			} else {
				int minX = Mth.floor(box.minX);
				int minZ = Mth.floor(box.minZ);
				int maxX = Mth.floor(box.maxX);
				int maxZ = Mth.floor(box.maxZ);
				int yInt = Mth.floor(y)-1;
				for (var pos : BlockPos.betweenClosed(minX, yInt, minZ, maxX, yInt, maxZ)) {
					if (world.getBlockState(pos).isCollisionShapeFullBlock(world, pos)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private Optional<Vec3> findCandidatePos(RefContext ctx, Vec3 centerPos) {
		var angle = ctx.random().nextDouble() * Math.PI * 2;
		double length = horizontalRange.sample(ctx.random());
		double xOffset = Math.cos(angle) * length;
		double zOffset = Math.sin(angle) * length;
		var targetPos = centerPos.add(xOffset, 0, zOffset);
		BlockPos.MutableBlockPos reusedBlockPos = new BlockPos.MutableBlockPos();
		reusedBlockPos.set(targetPos.x(), targetPos.y(), targetPos.z());
		int startY = reusedBlockPos.getY();
		int minY = verticalRange.bounds().min().map(y -> y + startY).orElse(ctx.world().getMinY());
		int maxY = verticalRange.bounds().max().map(y -> y + startY).orElse(ctx.world().getMaxY());
		int count = Math.max(startY - minY, maxY - startY);
		for (int i = 0; i < count; i++) {
			if (verticalRange.matches(-i)) {
				reusedBlockPos.setY(startY - i);
				var y = findValidY(ctx.world(), targetPos.x(), targetPos.z(), reusedBlockPos);
				if (y.isPresent()) {
					return Optional.of(new Vec3(targetPos.x(), y.getAsDouble(), targetPos.z()));
				}
			}
			if (verticalRange.matches(i)) {
				reusedBlockPos.setY(startY + i);
				var y = findValidY(ctx.world(), targetPos.x(), targetPos.z(), reusedBlockPos);
				if (y.isPresent()) {
					return Optional.of(new Vec3(targetPos.x(), y.getAsDouble(), targetPos.z()));
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
