package nu.metacraft.core.block.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import nu.metacraft.core.block.blocks.BlockWithDisguise;
import nu.metacraft.core.mixin.ChunkHolderAccessor;
import nu.metacraft.core.mixin.ServerChunkCacheAccessor;

public class DisguisedBlockEntity extends BlockEntity implements BlockEntityWithDisguise {

	protected static final String BLOCK_STATE = "BlockState";

	protected BlockState state = Blocks.BARRIER.defaultBlockState();

	protected DisguisedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public BlockState getDisplayedBlockState() {
		return state;
	}

	public void setBlockState(BlockState state) {
		if (state.getBlock() instanceof BlockWithDisguise) {
			state = Blocks.BARRIER.defaultBlockState();
		}
		if (this.state != state) {
			this.state = state;
			setChanged();
			if (level instanceof ServerLevel sw) {
				var cPos = ChunkPos.containing(worldPosition);
				var holder = ((ServerChunkCacheAccessor) sw.getChunkSource()).callGetVisibleChunkIfPresent(
						cPos.pack()
				);
				var players = ((ChunkHolderAccessor) holder).getPlayerProvider().getPlayers(
						cPos, false
				);
				for (var player : players) {
					updateClient(player);
				}
			}
		}
	}

	@Override
	public void updateClient(ServerPlayer player) {
		player.connection.send(new ClientboundBlockUpdatePacket(worldPosition, state));
	}

	@Override
	protected void loadAdditional(ValueInput nbt) {
		super.loadAdditional(nbt);
		setBlockState(nbt.read(BLOCK_STATE, BlockState.CODEC).orElse(Blocks.BARRIER.defaultBlockState()));
	}

	@Override
	protected void saveAdditional(ValueOutput nbt) {
		super.saveAdditional(nbt);
		nbt.store(BLOCK_STATE, BlockState.CODEC, state);
	}
}
