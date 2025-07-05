import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.dungeons.METAcraftDungeons;
import nu.metacraft.dungeons.dungeons.datablocks.DataBlock;
import nu.metacraft.dungeons.dungeons.datablocks.MultiDataBlock;
import nu.metacraft.dungeons.dungeons.datablocks.PortalDeeper;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.util.helper.TestHelper;

import java.util.*;

public class MultiDataBlockMergeTest {

	@BeforeAll
	static void init() {
		TestHelper.init(
				METAcraftLib::new,
				METAcraftCore::new,
				METAcraftDungeons::new
		);
	}

	@Test
	void test() {
		List<DataBlock.DataMultiBlockEntry<?>> blocks = new ArrayList<>();
		for (var pos : BlockPos.iterateOutwards(new BlockPos(0, 0, 0), 32, 32, 32)) {
			blocks.add(
				new DataBlock.DataMultiBlockEntry<>(
					pos.toImmutable(), null, new PortalDeeper(
							Optional.empty(), Optional.empty(),
						Optional.empty(), Optional.empty(),
						Optional.empty(), Optional.empty()
					)
				)
			);
		}
		for (var pos : BlockPos.iterateOutwards(new BlockPos(1000, 0, 0), 32, 32, 32)) {
			blocks.add(
				new DataBlock.DataMultiBlockEntry<>(
					pos.toImmutable(), null, new PortalDeeper(
							Optional.empty(), Optional.empty(),
						Optional.empty(), Optional.empty(),
						Optional.empty(), Optional.empty()
					)
				)
			);
		}
		Collections.shuffle(blocks);
		var map = MultiDataBlock.merge(blocks);
		var set = new HashSet<>(blocks);
		for (var value : map.values()) {
			assert set.contains(value);
		}
		assert map.values().size() == blocks.size();
		assert map.keySet().size() == 2;
	}

}
