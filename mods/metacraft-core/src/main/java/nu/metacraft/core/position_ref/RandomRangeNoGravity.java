package nu.metacraft.core.position_ref;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.core.registry.PositionRefRegistry;
import nu.metacraft.core.util.RefContext;
import nu.metacraft.lib.util.METACodecs;

import java.util.Optional;
import net.minecraft.advancements.critereon.FluidPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record RandomRangeNoGravity(
		PositionRef center, AABB hitbox, Optional<FluidPredicate> validFluids,
		FloatProvider range
) implements PositionRef {

	public static final MapCodec<RandomRangeNoGravity> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.lazyInitialized(() -> PositionRefRegistry.CODEC).fieldOf("center").forGetter(RandomRangeNoGravity::center),
					METACodecs.BOX_CODEC.fieldOf("hitbox").forGetter(RandomRangeNoGravity::hitbox),
					FluidPredicate.CODEC.optionalFieldOf("valid_fluids").forGetter(RandomRangeNoGravity::validFluids),
					FloatProvider.codec(0, Float.MAX_VALUE).fieldOf("range").forGetter(RandomRangeNoGravity::range)
			).apply(instance, RandomRangeNoGravity::new)
	);

	@Override
	public Optional<Vec3> get(RefContext ctx) {
		return center.get(ctx).flatMap(centerPos -> {
			return findCandidatePos(ctx, centerPos);
		});
	}
	
	private boolean isValidPos(ServerLevel world, double x, double y, double z) {
		var box = hitbox.move(x, y, z);
		if (world.noCollision(box)) {
			if (validFluids.isPresent()) {
				for (var pos : BlockPos.betweenClosed(box)) {
					if (!validFluids.get().matches(world, pos)) {
						return false;
					}
				}
			}
			return true;
		}
		return false;
	}

	private Optional<Vec3> findCandidatePos(RefContext ctx, Vec3 centerPos) {
		var angle = ctx.random().nextDouble() * Math.PI * 2;
		var heightAngle = ctx.random().nextDouble() * Math.PI - Math.PI/2;
		double length = range.sample(ctx.random());

		double initialZ = Math.cos(angle);
		double initialX = Math.sin(angle);
		double horizontal = Math.cos(heightAngle);
		double initialY = Math.sin(heightAngle);
		var offset = new Vec3(initialX * horizontal, -initialY, initialZ * horizontal).scale(length);

		var targetPos = centerPos.add(offset);
		if (isValidPos(ctx.world(), targetPos.x(), targetPos.y(), targetPos.z())) {
			return Optional.of(targetPos);
		}
		return Optional.empty();
	}

	@Override
	public PositionRefType<?> getType() {
		return PositionRefRegistry.RANDOM_RANGE_NO_GRAVITY;
	}
}
