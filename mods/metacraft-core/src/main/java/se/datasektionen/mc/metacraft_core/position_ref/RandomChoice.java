package se.datasektionen.mc.metacraft_core.position_ref;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.collection.Pool;
import net.minecraft.util.math.Vec3d;
import se.datasektionen.mc.metacraft_core.registry.PositionRefRegistry;
import se.datasektionen.mc.metacraft_core.util.RefContext;

import java.util.Optional;

public record RandomChoice(
		Pool<PositionRef> positions
) implements PositionRef {

	private static final Codec<Pool<PositionRef>> POSITION_POOL_CODEC = Codec.lazyInitialized(
			() -> Codec.withAlternative(
					Pool.createNonEmptyCodec(PositionRefRegistry.CODEC), PositionRefRegistry.CODEC.listOf(),
					list -> {
						var builder = Pool.<PositionRef>builder();
						list.forEach(builder::add);
						return builder.build();
					}
			)
	);

	public static final MapCodec<RandomChoice> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					POSITION_POOL_CODEC.fieldOf("positions").forGetter(RandomChoice::positions)
			).apply(instance, RandomChoice::new)
	);

	@Override
	public Optional<Vec3d> get(RefContext ctx) {
		return positions.get(ctx.getRandom()).get(ctx);
	}

	@Override
	public PositionRefType<?> getType() {
		return PositionRefRegistry.RANDOM_CHOICE;
	}
}
