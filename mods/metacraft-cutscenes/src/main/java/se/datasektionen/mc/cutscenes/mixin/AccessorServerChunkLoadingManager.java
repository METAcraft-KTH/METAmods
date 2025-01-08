package se.datasektionen.mc.cutscenes.mixin;

import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.world.ChunkTaskScheduler;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.world.poi.PointOfInterestStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerChunkLoadingManager.class)
public interface AccessorServerChunkLoadingManager {

	@Accessor
	@Mutable
	void setPointOfInterestStorage(PointOfInterestStorage poiStorage);


	@Mixin(ServerChunkLoadingManager.EntityTracker.class)
	interface EntityTracker {
		@Accessor
		@Mutable
		void setEntry(EntityTrackerEntry entry);
	}

	@Mutable
	@Accessor
	void setLightingProvider(ServerLightingProvider lightingProvider);

	@Accessor
	ChunkTaskScheduler getLightScheduler();

}
