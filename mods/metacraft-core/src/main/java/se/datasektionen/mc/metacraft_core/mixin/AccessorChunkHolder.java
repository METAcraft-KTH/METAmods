package se.datasektionen.mc.metacraft_core.mixin;

import net.minecraft.server.world.ChunkHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkHolder.class)
public interface AccessorChunkHolder {

	@Accessor
	ChunkHolder.PlayersWatchingChunkProvider getPlayersWatchingChunkProvider();

}
