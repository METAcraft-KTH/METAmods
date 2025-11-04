package nu.metacraft.lib.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.network.ServerPlayerConnection;

@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {

	@Accessor
	Int2ObjectMap<ChunkMap.TrackedEntity> getEntityMap();

	@Mixin(ChunkMap.TrackedEntity.class)
	interface TrackedEntity {
		@Accessor
		ServerEntity getServerEntity();

		@Accessor
		Set<ServerPlayerConnection> getSeenBy();
	}

}
