package nu.metacraft.bosses.boss.attacks;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

public class PlaceBlocksAttack extends InstantAttack {

	public static final MapCodec<PlaceBlocksAttack> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					BlockStateProvider.CODEC.fieldOf("blocks").forGetter(a -> a.blocks),
					PlacementModifier.CODEC.listOf().fieldOf("positionsGetters").forGetter(a -> a.positionsGetters)
			).apply(instance, PlaceBlocksAttack::new)
	);

	private final BlockStateProvider blocks;
	private final List<PlacementModifier> positionsGetters;

	public PlaceBlocksAttack(
			BlockStateProvider blocks, List<PlacementModifier> positionsGetters
	) {
		this.blocks = blocks;
		this.positionsGetters = positionsGetters;
	}

	@Override
	public void trigger(BossContext<?> ctx) {
		Stream<BlockPos> positions = Stream.of(ctx.boss().blockPosition());
		for (var positionsGetter : this.positionsGetters) {
			positions = positions.flatMap(
				position -> positionsGetter.getPositions(
					new PlacementContext(
						ctx.getWorld(), ctx.getWorld().getChunkSource().getGenerator(),
						Optional.empty()
					),
					ctx.random(), position
				)
			);
		}
		positions.forEach(pos -> {
			ctx.getWorld().setBlockAndUpdate(pos, blocks.getState(ctx.random(), pos));
		});
	}

	@Override
	public AttackType getType() {
		return AttackRegistry.PLACE_BLOCKS;
	}
}
