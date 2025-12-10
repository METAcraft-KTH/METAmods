package nu.metacraft.cutscenes.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTaskDispatcher;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {

	@Accessor
	@Mutable
	void setPoiManager(PoiManager poiStorage);


	@Mixin(ChunkMap.TrackedEntity.class)
	interface TrackedEntity {
		@Accessor
		@Mutable
		void setServerEntity(ServerEntity entry);
	}

	@Mutable
	@Accessor
	void setLightEngine(ThreadedLevelLightEngine lightingProvider);

	@Mutable
	@Accessor
	void setDistanceManager(ChunkMap.DistanceManager lightingProvider);

	@Accessor
	ChunkTaskDispatcher getLightTaskDispatcher();

}
