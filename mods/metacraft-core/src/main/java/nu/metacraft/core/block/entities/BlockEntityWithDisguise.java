package nu.metacraft.core.block.entities;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

public interface BlockEntityWithDisguise {

	BlockState getBlockState();

	void updateClient(ServerPlayer player);

}
