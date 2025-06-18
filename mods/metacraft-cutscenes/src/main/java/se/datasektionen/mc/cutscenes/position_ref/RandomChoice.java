package se.datasektionen.mc.cutscenes.position_ref;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.predicate.FluidPredicate;
import net.minecraft.predicate.NumberRange;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DataPool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.floatprovider.FloatProvider;
import net.minecraft.util.shape.VoxelShape;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.registry.PositionRefRegistry;
import se.datasektionen.mc.metacraft_lib.util.ExtraCodecs;

import java.util.Optional;
import java.util.OptionalDouble;

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
	public Optional<Vec3d> get(@Nullable ServerPlayerEntity player, CutsceneInstance cutscene) {
		return positions.getDataOrEmpty(cutscene.getRandom()).flatMap(pos -> pos.get(player, cutscene));
	}

	@Override
	public PositionRefType<?> getType() {
		return PositionRefRegistry.RANDOM_CHOICE;
	}
}
