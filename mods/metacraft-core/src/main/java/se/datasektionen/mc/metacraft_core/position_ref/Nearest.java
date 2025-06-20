package se.datasektionen.mc.metacraft_core.position_ref;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.Vec3d;
import se.datasektionen.mc.metacraft_core.registry.PositionRefRegistry;
import se.datasektionen.mc.metacraft_core.util.RefContext;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record Nearest(
		List<PositionRef> targets,
		PositionRef referencePoint
) implements PositionRef {

	public static final MapCodec<Nearest> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.lazyInitialized(PositionRefRegistry.CODEC::listOf).fieldOf("targets").forGetter(Nearest::targets),
					Codec.lazyInitialized(() -> PositionRefRegistry.CODEC).fieldOf("reference_point").forGetter(Nearest::referencePoint)
			).apply(instance, Nearest::new)
	);

	@Override
	public Optional<Vec3d> get(RefContext ctx) {
		return referencePoint.get(ctx).flatMap(
				referencePoint -> targets.stream().map(
						point -> point.get(ctx)
				).filter(Optional::isPresent).map(Optional::get).min(
						Comparator.comparing(referencePoint::squaredDistanceTo)
				)
		);
	}

	@Override
	public PositionRefType<?> getType() {
		return PositionRefRegistry.NEAREST;
	}
}
