package nu.metacraft.dungeons.dungeons.datablocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import nu.metacraft.core.block.entities.PortalEntity;
import nu.metacraft.dungeons.dungeons.portal_data.Dungeon;

public abstract class DataBlock {


	protected PortalEntity entrance = null;
	protected Dungeon.Parameters parameters;

	public void initialise(PortalEntity entity, Dungeon.Parameters parameters) {
		entrance = entity;
		this.parameters = parameters;
	}

	public abstract DataBlockRegistry.DataBlockType<?> getType();


	public abstract void processDataBlock(BlockPos pos, StructurePiece piece);

	public record DataBlockEntry<T extends DataBlock>(BlockPos pos, PoolElementStructurePiece piece, T datablock) {}

	public record DataMultiBlockEntry<T extends DataBlock & MultiDataBlock>(BlockPos pos, PoolElementStructurePiece piece, T datablock) {}

}
