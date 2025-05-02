package se.datasektionen.mc.metacraft_dungeons.dungeons.datablocks;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.util.math.Direction;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface MultiDataBlock {

	void processDataBlocks(Collection<DataBlock.DataMultiBlockEntry<?>> blocks);

	int getPriority();

	default boolean shouldMergeWith(MultiDataBlock other) {
		return this.getClass().equals(other.getClass());
	}

	/**
	 * Takes a list of block data entries and returns a multimap where blocks adjacent to each other have the same index.
	 * This algorithm is relatively fast, but might run out of memory with default settings if multiBlockDataBlocks contains more than 2 million entries.
	 * @param multiBlockDataBlocks The list of data entries to merge.
	 * @return A multimap containing all the same values. Note that the key indexes are not guaranteed to follow any pattern.
	 */
	static Multimap<Integer, DataBlock.DataMultiBlockEntry<?>> merge(
			List<DataBlock.DataMultiBlockEntry<?>> multiBlockDataBlocks
	) {
		//Used for fast adjacency checks.
		Long2ObjectMap<DataBlock.DataMultiBlockEntry<?>> positions = new Long2ObjectOpenHashMap<>(multiBlockDataBlocks.size());
		for (var data : multiBlockDataBlocks) {
			positions.put(data.pos().asLong(), data);
		}

		int topIndex = 0;
		Multimap<Integer, DataBlock.DataMultiBlockEntry<?>> dataBlockSets = MultimapBuilder.hashKeys().hashSetValues().build();
		Map<DataBlock.DataMultiBlockEntry<?>, Integer> dataBlockLookup = new HashMap<>();

		for (var entry : multiBlockDataBlocks) {
			for (Direction dir : Direction.values()) {
				long pos = entry.pos().add(dir.getVector()).asLong();
				if (positions.containsKey(pos)) {
					var otherEntry = positions.get(pos);
					if (!entry.datablock().shouldMergeWith(otherEntry.datablock())) {
						continue;
					}
					if (dataBlockLookup.containsKey(entry)) {
						if (dataBlockLookup.containsKey(otherEntry)) {
							int firstSetIndex = dataBlockLookup.get(entry);
							int secondSetIndex = dataBlockLookup.get(otherEntry);
							if (firstSetIndex != secondSetIndex) {
								//Merge 2 sets of blocks together.
								//Take values from the smaller one and put them in the bigger one.
								if (dataBlockSets.get(firstSetIndex).size() < dataBlockSets.get(secondSetIndex).size()) {
									var intermediary = firstSetIndex;
									firstSetIndex = secondSetIndex;
									secondSetIndex = intermediary;
								}
								var secondSet = dataBlockSets.get(secondSetIndex);
								dataBlockSets.putAll(firstSetIndex, secondSet);
								for (var secondSetEntry : secondSet) {
									dataBlockLookup.put(secondSetEntry, firstSetIndex);
								}
								dataBlockSets.removeAll(secondSetIndex);
							}
						} else {
							//Add new entry to group.
							int num = dataBlockLookup.get(entry);
							dataBlockSets.get(num).add(otherEntry);
							dataBlockLookup.put(otherEntry, num);
						}
					} else if (dataBlockLookup.containsKey(otherEntry)) {
						//Add new entry to group.
						int num = dataBlockLookup.get(otherEntry);
						dataBlockSets.get(num).add(entry);
						dataBlockLookup.put(entry, num);
					} else {
						//Add 2 new entries to new group.
						dataBlockSets.put(topIndex, entry);
						dataBlockSets.put(topIndex, otherEntry);
						dataBlockLookup.put(entry, topIndex);
						dataBlockLookup.put(otherEntry, topIndex);
						topIndex++;
					}
				}
			}
		}

		return dataBlockSets;
	}

}
