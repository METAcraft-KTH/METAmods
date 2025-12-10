package nu.metacraft.cutscenes.cutscene.world;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

public class CutsceneChunk extends LevelChunk {

	private final Set<BlockPos> changedBlocks = new HashSet<>();

	protected final CutsceneLevel world;

	public CutsceneChunk(LevelChunk chunk, CutsceneLevel world) {
		super(world, chunk.getPos());
		this.world = world;
		var data = new ClientboundLevelChunkPacketData(chunk);
		this.replaceWithPacketData(data.getReadBuffer(), data.getHeightmaps(), data.getBlockEntitiesTagsConsumer(chunk.getPos().x, chunk.getPos().z));
		this.setFullStatus(chunk::getFullStatus);
		setLoaded(true);
		registerAllBlockEntitiesAfterLevelLoad();
	}

	@Override
	public BlockState setBlockState(BlockPos pos, BlockState state, int flags) {
		changedBlocks.add(pos);
		return super.setBlockState(pos, state, flags);
	}

	@Override
	public void setBlockEntity(BlockEntity blockEntity) {
		changedBlocks.add(blockEntity.getBlockPos());
		super.setBlockEntity(blockEntity);
	}

	@Override
	public void removeBlockEntity(BlockPos pos) {
		changedBlocks.add(pos);
		super.removeBlockEntity(pos);
	}

	@Override
	public void clearAllBlockEntities() {
		changedBlocks.clear();
		super.clearAllBlockEntities();
	}

	public Set<BlockPos> getChangedBlocks() {
		return changedBlocks;
	}

	private Entity copyEntityAndPassengers(Entity entity) {
		var existing = world.getEntities().get(entity.getUUID());
		if (existing != null) {
			return null;
		}
		var newEntity = entity.getType().create(world, EntitySpawnReason.LOAD);
		if (newEntity == null) return null;
		var id = newEntity.getUUID();
		newEntity.restoreFrom(entity);
		newEntity.setUUID(id);
		world.getEntityManager().addEntityToHide(entity.getUUID());
		world.addDuringTeleport(newEntity);
		if (entity.isVehicle()) {
			for (var passenger : entity.getPassengers()) {
				var newPassenger = copyEntityAndPassengers(passenger);
				if (newPassenger != null) {
					newPassenger.startRiding(newEntity, true, false);
				}
			}
		}
		return newEntity;
	}

	public void fetchEntitiesFromActualWorld() {
		world.getActualWorld().getEntitiesOfClass(
				Entity.class, AABB.encapsulatingFullBlocks(
						new BlockPos(getPos().getMinBlockX(), world.getMinY(), getPos().getMinBlockZ()),
						new BlockPos(getPos().getMaxBlockX(), world.getMaxY(), getPos().getMaxBlockZ())
				),
				entity -> !entity.isPassenger() && entity.chunkPosition().equals(this.getPos())
		).forEach(this::copyEntityAndPassengers);
	}
}
