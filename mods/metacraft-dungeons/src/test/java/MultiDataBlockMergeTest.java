import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.metacraft.core.METAcraftCore;
import se.metacraft.dungeons.METAcraftDungeons;
import se.metacraft.dungeons.dungeons.datablocks.DataBlock;
import se.metacraft.dungeons.dungeons.datablocks.MultiDataBlock;
import se.metacraft.dungeons.dungeons.datablocks.PortalDeeper;
import se.metacraft.lib.METAcraftLib;
import se.metacraft.lib.util.helper.TestHelper;

import java.util.*;
import net.minecraft.core.BlockPos;

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
		for (var pos : BlockPos.withinManhattan(new BlockPos(0, 0, 0), 32)) {
			blocks.add(
				new DataBlock.DataMultiBlockEntry<>(
					pos.immutable(), null, new PortalDeeper(
							Optional.empty(), Optional.empty(),
						Optional.empty(), Optional.empty(),
						Optional.empty(), Optional.empty()
					)
				)
			);
		}
		for (var pos : BlockPos.withinManhattan(new BlockPos(1000, 0, 0), 32)) {
			blocks.add(
				new DataBlock.DataMultiBlockEntry<>(
					pos.immutable(), null, new PortalDeeper(
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
