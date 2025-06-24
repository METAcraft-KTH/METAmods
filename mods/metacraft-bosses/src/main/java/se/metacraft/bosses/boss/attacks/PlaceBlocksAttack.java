package se.metacraft.bosses.boss.attacks;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.gen.feature.FeaturePlacementContext;
import net.minecraft.world.gen.placementmodifier.PlacementModifier;
import net.minecraft.world.gen.stateprovider.BlockStateProvider;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class PlaceBlocksAttack extends InstantAttack {

	public static final MapCodec<PlaceBlocksAttack> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					BlockStateProvider.TYPE_CODEC.fieldOf("blocks").forGetter(a -> a.blocks),
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
		Stream<BlockPos> positions = Stream.of(ctx.boss().getBlockPos());
		for (var positionsGetter : this.positionsGetters) {
			positions = positions.flatMap(
				position -> positionsGetter.getPositions(
					new FeaturePlacementContext(
						ctx.getWorld(), ctx.getWorld().getChunkManager().getChunkGenerator(),
						Optional.empty()
					),
					ctx.random(), position
				)
			);
		}
		positions.forEach(pos -> {
			ctx.getWorld().setBlockState(pos, blocks.get(ctx.random(), pos));
		});
	}

	@Override
	public AttackType getType() {
		return AttackRegistry.PLACE_BLOCKS;
	}
}
