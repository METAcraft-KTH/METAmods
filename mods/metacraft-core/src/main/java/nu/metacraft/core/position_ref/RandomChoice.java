package nu.metacraft.core.position_ref;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.core.registry.PositionRefRegistry;
import nu.metacraft.core.util.RefContext;

import java.util.Optional;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.phys.Vec3;

public record RandomChoice(
		WeightedList<PositionRef> positions
) implements PositionRef {

	private static final Codec<WeightedList<PositionRef>> POSITION_POOL_CODEC = Codec.lazyInitialized(
			() -> Codec.withAlternative(
					WeightedList.nonEmptyCodec(PositionRefRegistry.CODEC), PositionRefRegistry.CODEC.listOf(),
					list -> {
						var builder = WeightedList.<PositionRef>builder();
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
	public Optional<Vec3> get(RefContext ctx) {
		return positions.getRandomOrThrow(ctx.random()).get(ctx);
	}

	@Override
	public PositionRefType<?> getType() {
		return PositionRefRegistry.RANDOM_CHOICE;
	}
}
