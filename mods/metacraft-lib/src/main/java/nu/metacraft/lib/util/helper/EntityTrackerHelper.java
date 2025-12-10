package nu.metacraft.lib.util.helper;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import nu.metacraft.lib.mixin.ChunkMapAccessor;

import java.util.Set;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerPlayerConnection;

/**
 * Note that when in the METAmods repository, you will have to set up an access widener for
 * {@link ChunkMap.TrackedEntity} in the module to use this.
 */
public class EntityTrackerHelper {

	/**
	 * Returns all entity trackers for the given world.
	 * Note that when in the METAmods repository, you will have to set up an access widener for
	 * {@link ChunkMap.TrackedEntity} in the module to use this.
	 * @param world The world.
	 * @return The indexed entity tracker map.
	 */
	public static Int2ObjectMap<ChunkMap.TrackedEntity> getEntityTrackers(
			ServerLevel world
	) {
		return getEntityTrackers(world.getChunkSource().chunkMap);
	}

	/**
	 * Returns all entity trackers for the given world.
	 * Note that when in the METAmods repository, you will have to set up an access widener for
	 * {@link ChunkMap.TrackedEntity} in the module to use this.
	 * @param manager The server chunkloading manager.
	 * @return The indexed entity tracker map.
	 */
	public static Int2ObjectMap<ChunkMap.TrackedEntity> getEntityTrackers(
			ChunkMap manager
	) {
		return ((ChunkMapAccessor) manager).getEntityMap();
	}

	/**
	 * Returns the entity tracker entry for your entity tracker.
	 * Note that when in the METAmods repository, you will have to set up an access widener for
	 * {@link ChunkMap.TrackedEntity} in the module to use this.
	 * @param entityTracker The entity tracker.
	 * @return The entity tracker entry.
	 */
	public static ServerEntity getEntry(ChunkMap.TrackedEntity entityTracker) {
		return ((ChunkMapAccessor.TrackedEntity) entityTracker).getServerEntity();
	}

	/**
	 * Returns all player associated network handlers for your entity tracker.
	 * Note that when in the METAmods repository, you will have to set up an access widener for
	 * {@link ChunkMap.TrackedEntity} in the module to use this.
	 * @param entityTracker The entity tracker.
	 * @return A set of all network listeners.
	 */
	public static Set<ServerPlayerConnection> getListeners(ChunkMap.TrackedEntity entityTracker) {
		return ((ChunkMapAccessor.TrackedEntity) entityTracker).getSeenBy();
	}

}
