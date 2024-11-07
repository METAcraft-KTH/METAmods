package nu.metacraft.minigame_util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.pattern.CachedBlockPosition;
import net.minecraft.predicate.BlockPredicate;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class BlockPredicateList {

	private final List<BlockPredicate> blocks;
	private boolean allowMode;

	public static final Codec<BlockPredicateList> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					BlockPredicate.CODEC.listOf().fieldOf("blocks").forGetter(BlockPredicateList::getBlocks),
					Codec.BOOL.optionalFieldOf("allow_mode", false).forGetter(BlockPredicateList::getAllowMode)
			).apply(instance, BlockPredicateList::new)
	);

	protected BlockPredicateList(List<BlockPredicate> blocks, boolean allowMode) {
		this.blocks = blocks;
		this.allowMode = allowMode;
	}

	public List<BlockPredicate> getBlocks() {
		return blocks;
	}

	public boolean getAllowMode() {
		return allowMode;
	}

	public void setAllowMode(boolean allowMode) {
		this.allowMode = allowMode;
	}

	public boolean test(ServerWorld world, BlockPos pos) {
		if (!world.isPosLoaded(pos)) return false;
		return test(new CachedBlockPosition(world, pos, false));
	}

	public boolean test(CachedBlockPosition pos) {
		for (var block : blocks) {
			if (block.test(pos)) {
				return !allowMode;
			}
		}
		return allowMode;
	}

	public static BlockPredicateList createEmpty() {
		return new BlockPredicateList(new ArrayList<>(), false);
	}

}
