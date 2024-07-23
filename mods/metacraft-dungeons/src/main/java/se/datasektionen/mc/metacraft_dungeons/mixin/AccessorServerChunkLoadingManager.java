package se.datasektionen.mc.metacraft_dungeons.mixin;

import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ServerChunkLoadingManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerChunkLoadingManager.class)
public interface AccessorServerChunkLoadingManager {

	@Accessor
	WorldGenerationProgressListener getWorldGenerationProgressListener();

}
