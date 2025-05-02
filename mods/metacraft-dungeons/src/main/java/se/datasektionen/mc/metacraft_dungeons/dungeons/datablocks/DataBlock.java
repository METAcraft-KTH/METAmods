package se.datasektionen.mc.metacraft_dungeons.dungeons.datablocks;

import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.math.BlockPos;
import se.datasektionen.mc.metacraft_core.block.entities.PortalEntity;
import se.datasektionen.mc.metacraft_dungeons.dungeons.portal_data.Dungeon;

public abstract class DataBlock {


	protected PortalEntity entrance = null;
	protected Dungeon.Parameters parameters;

	public void initialise(PortalEntity entity, Dungeon.Parameters parameters) {
		entrance = entity;
		this.parameters = parameters;
	}

	public abstract DataBlockRegistry.DataBlockType<?> getType();


	public abstract void processDataBlock(BlockPos pos, StructurePiece piece);

	public record DataBlockEntry<T extends DataBlock>(BlockPos pos, PoolStructurePiece piece, T datablock) {}

	public record DataMultiBlockEntry<T extends DataBlock & MultiDataBlock>(BlockPos pos, PoolStructurePiece piece, T datablock) {}

}
