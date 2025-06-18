package se.datasektionen.mc.cutscenes.position_ref;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.collection.DataPool;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.registry.PositionRefRegistry;

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
	public Optional<Vec3d> get(@Nullable ServerPlayerEntity player, CutsceneInstance cutscene) {
		return referencePoint.get(player, cutscene).flatMap(
				referencePoint -> targets.stream().map(
						point -> point.get(player, cutscene)
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
