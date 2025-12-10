package nu.metacraft.core.mixin;

import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundBlockUpdatePacket.class)
public interface ClientboundBlockUpdatePacketAccessor {

	@Accessor
	@Mutable
	void setBlockState(BlockState state);

}
