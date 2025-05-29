package se.datasektionen.mc.cutscenes.cutscene.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashSet;
import java.util.Set;

public class CutsceneChunk extends WorldChunk {

	private final Set<BlockPos> changedBlocks = new HashSet<>();

	protected final CutsceneWorld world;

	public CutsceneChunk(WorldChunk chunk, CutsceneWorld world) {
		super(world, chunk.getPos());
		this.world = world;
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

	private Entity copyEntityAndPassengers(Entity entity) {
		var existing = world.getEntityLookup().get(entity.getUuid());
		if (existing != null) {
			return null;
		}
		var newEntity = entity.getType().create(world, SpawnReason.LOAD);
		if (newEntity == null) return null;
		var id = newEntity.getUuid();
		newEntity.copyFrom(entity);
		newEntity.setUuid(id);
		world.getEntityManager().addEntityToHide(entity.getUuid());
		world.onDimensionChanged(newEntity);
		if (entity.hasPassengers()) {
			for (var passenger : entity.getPassengerList()) {
				var newPassenger = copyEntityAndPassengers(passenger);
				if (newPassenger != null) {
					newPassenger.startRiding(newEntity, true);
				}
			}
		}
		return newEntity;
	}

	public void fetchEntitiesFromActualWorld() {
		world.getActualWorld().getEntitiesByClass(
				Entity.class, Box.enclosing(
						new BlockPos(getPos().getStartX(), world.getBottomY(), getPos().getStartZ()),
						new BlockPos(getPos().getEndX(), world.getTopYInclusive(), getPos().getEndZ())
				),
				entity -> !entity.hasVehicle() && entity.getChunkPos().equals(this.getPos())
		).forEach(this::copyEntityAndPassengers);
	}
}
