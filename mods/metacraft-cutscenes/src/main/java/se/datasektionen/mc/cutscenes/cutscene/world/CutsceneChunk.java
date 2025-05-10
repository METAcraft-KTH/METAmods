package se.datasektionen.mc.cutscenes.cutscene.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashSet;
import java.util.Set;

public class CutsceneChunk extends WorldChunk {

	private final Set<BlockPos> changedBlocks = new HashSet<>();

	public CutsceneChunk(WorldChunk chunk, World world) {
		super(world, chunk.getPos());
		var data = new ChunkData(chunk);
		this.loadFromPacket(data.getSectionsDataBuf(), data.getHeightmap(), data.getBlockEntities(chunk.getPos().x, chunk.getPos().z));
		this.setLevelTypeProvider(chunk::getLevelType);
		setLoadedToWorld(true);
		updateAllBlockEntities();
	}

	@Override
	public BlockState setBlockState(BlockPos pos, BlockState state, boolean moved) {
		changedBlocks.add(pos);
		return super.setBlockState(pos, state, moved);
	}

	@Override
	public void setBlockEntity(BlockEntity blockEntity) {
		changedBlocks.add(blockEntity.getPos());
		super.setBlockEntity(blockEntity);
	}

	@Override
	public void removeBlockEntity(BlockPos pos) {
		changedBlocks.add(pos);
		super.removeBlockEntity(pos);
	}

	@Override
	public void clear() {
		changedBlocks.clear();
		super.clear();
	}

	public Set<BlockPos> getChangedBlocks() {
		return changedBlocks;
	}
}
