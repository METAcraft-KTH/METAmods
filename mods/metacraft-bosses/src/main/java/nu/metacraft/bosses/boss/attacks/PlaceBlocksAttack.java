package nu.metacraft.bosses.boss.attacks;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
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

	private final Holder<BlockStateProvider> blocks;
	private final List<PlacementModifier> positionsGetters;

	public PlaceBlocksAttack(
			Holder<BlockStateProvider> blocks, List<PlacementModifier> positionsGetters
	) {
		this.blocks = blocks;
		this.positionsGetters = positionsGetters;
	}

	@Override
	public void trigger(BossContext<?> ctx) {
		// Very copied from FeaturePlacer
		if (positionsGetters.isEmpty()) {
			place(ctx.boss().blockPosition(), ctx);
			return;
		}
		var context = new PlacementContext(
				ctx.getWorld(), ctx.getWorld().getChunkSource().getGenerator(),
				Optional.empty()
		);
		List<BlockPos> positions = new ArrayList<>();
		positions.add(ctx.boss().blockPosition());
		List<BlockPos> nextPositions = new ArrayList<>();
		IntList modifierIndices = new IntArrayList();
		modifierIndices.add(0);
		for (; !positions.isEmpty(); nextPositions.clear()) {
			var pos = positions.removeLast();
			int index = modifierIndices.removeInt(modifierIndices.size()-1);
			var positionGetter = positionsGetters.get(index);
			positionGetter.modify(context, ctx.random(), pos, nextPositions::add);
			int nextIndex = index+1;
			if (nextIndex < positionsGetters.size()) {
				for (int i = nextPositions.size() - 1; i >= 0; i--) {
					positions.add(nextPositions.get(i));
					modifierIndices.add(nextIndex);
				}
			} else {
				for (var nextPos : nextPositions) {
					place(nextPos, ctx);
				}
			}
		}
	}

	private void place(BlockPos pos, BossContext<?> ctx) {
		ctx.getWorld().setBlockAndUpdate(pos, blocks.value().getState(ctx.getWorld(), ctx.random(), pos));
	}

	@Override
	public AttackType getType() {
		return AttackRegistry.PLACE_BLOCKS;
	}
}
