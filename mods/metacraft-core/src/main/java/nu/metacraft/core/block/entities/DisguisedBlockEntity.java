package nu.metacraft.core.block.entities;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import nu.metacraft.core.mixin.AccessorChunkHolder;
import nu.metacraft.core.mixin.AccessorServerChunkManager;

public class DisguisedBlockEntity extends BlockEntity implements BlockEntityWithDisguise {

	protected static final String BLOCK_STATE = "BlockState";

	protected BlockState state = Blocks.BARRIER.getDefaultState();

	protected DisguisedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public BlockState getBlockState() {
		return state;
	}

	public void setBlockState(BlockState state) {
		if (this.state != state) {
			this.state = state;
			markDirty();
			if (world instanceof ServerWorld sw) {
				var cPos = new ChunkPos(pos);
				var holder = ((AccessorServerChunkManager) sw.getChunkManager()).callGetChunkHolder(
						cPos.toLong()
				);
				var players = ((AccessorChunkHolder) holder).getPlayersWatchingChunkProvider().getPlayersWatchingChunk(
						cPos, false
				);
				for (var player : players) {
					updateClient(player);
				}
			}
		}
	}

	@Override
	public void updateClient(ServerPlayerEntity player) {
		player.networkHandler.sendPacket(new BlockUpdateS2CPacket(pos, state));
	}

	@Override
	protected void readData(ReadView nbt) {
		super.readData(nbt);
		setBlockState(nbt.read(BLOCK_STATE, BlockState.CODEC).orElse(Blocks.BARRIER.getDefaultState()));
	}

	@Override
	protected void writeData(WriteView nbt) {
		super.writeData(nbt);
		nbt.put(BLOCK_STATE, BlockState.CODEC, state);
	}
}
