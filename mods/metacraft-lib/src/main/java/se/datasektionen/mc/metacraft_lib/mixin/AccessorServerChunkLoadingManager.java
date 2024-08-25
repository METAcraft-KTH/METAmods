package se.datasektionen.mc.metacraft_lib.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.PlayerAssociatedNetworkHandler;
import net.minecraft.server.world.ServerChunkLoadingManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(ServerChunkLoadingManager.class)
public interface AccessorServerChunkLoadingManager {

	@Accessor
	WorldGenerationProgressListener getWorldGenerationProgressListener();

	@Accessor
	Int2ObjectMap<ServerChunkLoadingManager.EntityTracker> getEntityTrackers();

	@Mixin(ServerChunkLoadingManager.EntityTracker.class)
	interface EntityTracker {
		@Accessor
		EntityTrackerEntry getEntry();

		@Accessor
		Set<PlayerAssociatedNetworkHandler> getListeners();
	}

}
