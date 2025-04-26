import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.datasektionen.mc.metacraft_dungeons.METAcraftDungeons;
import se.datasektionen.mc.metacraft_dungeons.block.block_entities.DungeonEntranceEntity;
import se.datasektionen.mc.metacraft_dungeons.dungeons.datablocks.MultiDataBlock;
import se.datasektionen.mc.metacraft_dungeons.dungeons.datablocks.PortalDeeper;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;
import se.datasektionen.mc.metacraft_lib.util.helper.TestHelper;

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
		List<DungeonEntranceEntity.DataMultiBlockEntry<?>> blocks = new ArrayList<>();
		for (var pos : BlockPos.iterateOutwards(new BlockPos(0, 0, 0), 32, 32, 32)) {
			blocks.add(
				new DungeonEntranceEntity.DataMultiBlockEntry<>(
					pos.toImmutable(), null, new PortalDeeper(
							Optional.empty(), Optional.empty(),
						Optional.empty(), Optional.empty(),
						Optional.empty()
					)
				)
			);
		}
		for (var pos : BlockPos.iterateOutwards(new BlockPos(1000, 0, 0), 32, 32, 32)) {
			blocks.add(
				new DungeonEntranceEntity.DataMultiBlockEntry<>(
					pos.toImmutable(), null, new PortalDeeper(
							Optional.empty(), Optional.empty(),
						Optional.empty(), Optional.empty(),
						Optional.empty()
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
