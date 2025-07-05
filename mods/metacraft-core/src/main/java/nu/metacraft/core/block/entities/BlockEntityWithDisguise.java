package nu.metacraft.core.block.entities;

import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;

public interface BlockEntityWithDisguise {

	BlockState getBlockState();

	void updateClient(ServerPlayerEntity player);

}
