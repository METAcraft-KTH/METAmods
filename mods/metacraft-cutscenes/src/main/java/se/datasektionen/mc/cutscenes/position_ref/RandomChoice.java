package se.datasektionen.mc.cutscenes.position_ref;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.collection.DataPool;
import net.minecraft.util.math.Vec3d;
import se.datasektionen.mc.cutscenes.registry.PositionRefRegistry;
import se.datasektionen.mc.cutscenes.util.RefContext;

import java.util.Optional;

public record RandomChoice(
		DataPool<PositionRef> positions
) implements PositionRef {

	private static final Codec<DataPool<PositionRef>> POSITION_POOL_CODEC = Codec.lazyInitialized(
			() -> Codec.withAlternative(
					DataPool.createCodec(PositionRefRegistry.CODEC), PositionRefRegistry.CODEC.listOf(),
					list -> {
						var builder = DataPool.<PositionRef>builder();
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
		return positions.getDataOrEmpty(ctx.getRandom()).flatMap(pos -> pos.get(ctx));
	}

	@Override
	public PositionRefType<?> getType() {
		return PositionRefRegistry.RANDOM_CHOICE;
	}
}
