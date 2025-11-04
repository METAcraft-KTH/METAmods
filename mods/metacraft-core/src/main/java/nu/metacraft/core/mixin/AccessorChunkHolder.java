package nu.metacraft.core.mixin;

import net.minecraft.server.level.ChunkHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkHolder.class)
public interface AccessorChunkHolder {

	@Accessor
	ChunkHolder.PlayerProvider getPlayerProvider();

}
