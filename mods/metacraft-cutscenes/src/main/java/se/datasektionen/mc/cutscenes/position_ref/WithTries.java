package se.datasektionen.mc.cutscenes.position_ref;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.Vec3d;
import se.datasektionen.mc.cutscenes.registry.PositionRefRegistry;
import se.datasektionen.mc.cutscenes.util.RefContext;

import java.util.Optional;

public record WithTries(
		PositionRef position,
		int tries
) implements PositionRef {

	public static final MapCodec<WithTries> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.lazyInitialized(() ->PositionRefRegistry.CODEC).fieldOf("position").forGetter(WithTries::position),
					Codec.INT.fieldOf("tries").forGetter(WithTries::tries)
			).apply(instance, WithTries::new)
	);

	@Override
	public Optional<Vec3d> get(RefContext ctx) {
		for (int i = 0; i < tries; i++) {
			var pos = position.get(ctx);
			if (pos.isPresent()) {
				return pos;
			}
		}
		return Optional.empty();
	}

	@Override
	public PositionRefType<?> getType() {
		return PositionRefRegistry.WITH_TRIES;
	}
}
