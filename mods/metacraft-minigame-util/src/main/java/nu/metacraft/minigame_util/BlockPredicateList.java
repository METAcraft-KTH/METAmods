package nu.metacraft.minigame_util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.advancements.criterion.BlockPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;

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

	public boolean test(ServerLevel world, BlockPos pos) {
		if (!world.isLoaded(pos)) return false;
		return test(new BlockInWorld(world, pos, false));
	}

	public boolean test(BlockInWorld pos) {
		for (var block : blocks) {
			if (block.matches(pos)) {
				return !allowMode;
			}
		}
		return allowMode;
	}

	public static BlockPredicateList createEmpty() {
		return new BlockPredicateList(new ArrayList<>(), false);
	}

}
