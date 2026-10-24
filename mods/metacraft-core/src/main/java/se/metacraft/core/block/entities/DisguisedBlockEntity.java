package se.metacraft.core.block.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import se.metacraft.core.block.blocks.BlockWithDisguise;

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
		}
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
