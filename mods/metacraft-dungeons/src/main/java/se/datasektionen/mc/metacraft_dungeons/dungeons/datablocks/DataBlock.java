package se.datasektionen.mc.metacraft_dungeons.dungeons.datablocks;

import net.minecraft.structure.StructurePiece;
import net.minecraft.util.math.BlockPos;
import se.datasektionen.mc.metacraft_dungeons.block.block_entities.DungeonEntranceEntity;

public abstract class DataBlock {


	protected DungeonEntranceEntity entrance = null;
	protected DungeonEntranceEntity.Parameters parameters;

	public void initialise(DungeonEntranceEntity entity, DungeonEntranceEntity.Parameters parameters) {
		entrance = entity;
		this.parameters = parameters;
	}

	public abstract DataBlockRegistry.DataBlockType<?> getType();


	public abstract void processDataBlock(BlockPos pos, StructurePiece piece);

}
