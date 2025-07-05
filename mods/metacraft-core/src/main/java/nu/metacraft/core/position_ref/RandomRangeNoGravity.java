package nu.metacraft.core.position_ref;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.predicate.FluidPredicate;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.floatprovider.FloatProvider;
import nu.metacraft.core.registry.PositionRefRegistry;
import nu.metacraft.core.util.RefContext;
import nu.metacraft.lib.util.ExtraCodecs;

import java.util.Optional;

public record RandomRangeNoGravity(
		PositionRef center, Box hitbox, Optional<FluidPredicate> validFluids,
		FloatProvider range
) implements PositionRef {

	public static final MapCodec<RandomRangeNoGravity> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.lazyInitialized(() -> PositionRefRegistry.CODEC).fieldOf("center").forGetter(RandomRangeNoGravity::center),
					ExtraCodecs.BOX_CODEC.fieldOf("hitbox").forGetter(RandomRangeNoGravity::hitbox),
					FluidPredicate.CODEC.optionalFieldOf("valid_fluids").forGetter(RandomRangeNoGravity::validFluids),
					FloatProvider.createValidatedCodec(0, Float.MAX_VALUE).fieldOf("range").forGetter(RandomRangeNoGravity::range)
			).apply(instance, RandomRangeNoGravity::new)
	);

	@Override
	public Optional<Vec3d> get(RefContext ctx) {
		return center.get(ctx).flatMap(centerPos -> {
			return findCandidatePos(ctx, centerPos);
		});
	}
	
	private boolean isValidPos(ServerWorld world, double x, double y, double z) {
		var box = hitbox.offset(x, y, z);
		if (world.isSpaceEmpty(box)) {
			if (validFluids.isPresent()) {
				for (var pos : BlockPos.iterate(box)) {
					if (!validFluids.get().test(world, pos)) {
						return false;
					}
				}
			}
			return true;
		}
		return false;
	}

	private Optional<Vec3d> findCandidatePos(RefContext ctx, Vec3d centerPos) {
		var angle = ctx.getRandom().nextDouble() * Math.PI * 2;
		var heightAngle = ctx.getRandom().nextDouble() * Math.PI - Math.PI/2;
		double length = range.get(ctx.getRandom());

		double initialZ = Math.cos(angle);
		double initialX = Math.sin(angle);
		double horizontal = Math.cos(heightAngle);
		double initialY = Math.sin(heightAngle);
		var offset = new Vec3d(initialX * horizontal, -initialY, initialZ * horizontal).multiply(length);

		var targetPos = centerPos.add(offset);
		if (isValidPos(ctx.getWorld(), targetPos.getX(), targetPos.getY(), targetPos.getZ())) {
			return Optional.of(targetPos);
		}
		return Optional.empty();
	}

	@Override
	public PositionRefType<?> getType() {
		return PositionRefRegistry.RANDOM_RANGE_NO_GRAVITY;
	}
}
