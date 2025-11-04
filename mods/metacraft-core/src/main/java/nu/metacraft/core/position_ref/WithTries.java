package nu.metacraft.core.position_ref;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.core.registry.PositionRefRegistry;
import nu.metacraft.core.util.RefContext;

import java.util.Optional;
import net.minecraft.world.phys.Vec3;

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
	public Optional<Vec3> get(RefContext ctx) {
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
