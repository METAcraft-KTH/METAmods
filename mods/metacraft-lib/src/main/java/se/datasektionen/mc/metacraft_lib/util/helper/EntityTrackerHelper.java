package se.datasektionen.mc.metacraft_lib.util.helper;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.PlayerAssociatedNetworkHandler;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.server.world.ServerWorld;
import se.datasektionen.mc.metacraft_lib.mixin.AccessorServerChunkLoadingManager;

import java.util.Set;

/**
 * Note that when in the METAmods repository, you will have to set up an access widener for
 * {@link ServerChunkLoadingManager.EntityTracker} in the module to use this.
 */
public class EntityTrackerHelper {

	/**
	 * Returns all entity trackers for the given world.
	 * Note that when in the METAmods repository, you will have to set up an access widener for
	 * {@link ServerChunkLoadingManager.EntityTracker} in the module to use this.
	 * @param world The world.
	 * @return The indexed entity tracker map.
	 */
	public static Int2ObjectMap<ServerChunkLoadingManager.EntityTracker> getEntityTrackers(
			ServerWorld world
	) {
		return getEntityTrackers(world.getChunkManager().chunkLoadingManager);
	}

	/**
	 * Returns all entity trackers for the given world.
	 * Note that when in the METAmods repository, you will have to set up an access widener for
	 * {@link ServerChunkLoadingManager.EntityTracker} in the module to use this.
	 * @param manager The server chunkloading manager.
	 * @return The indexed entity tracker map.
	 */
	public static Int2ObjectMap<ServerChunkLoadingManager.EntityTracker> getEntityTrackers(
			ServerChunkLoadingManager manager
	) {
		return ((AccessorServerChunkLoadingManager) manager).getEntityTrackers();
	}

	/**
	 * Returns the entity tracker entry for your entity tracker.
	 * Note that when in the METAmods repository, you will have to set up an access widener for
	 * {@link ServerChunkLoadingManager.EntityTracker} in the module to use this.
	 * @param entityTracker The entity tracker.
	 * @return The entity tracker entry.
	 */
	public static EntityTrackerEntry getEntry(ServerChunkLoadingManager.EntityTracker entityTracker) {
		return ((AccessorServerChunkLoadingManager.EntityTracker) entityTracker).getEntry();
	}

	/**
	 * Returns all player associated network handlers for your entity tracker.
	 * Note that when in the METAmods repository, you will have to set up an access widener for
	 * {@link ServerChunkLoadingManager.EntityTracker} in the module to use this.
	 * @param entityTracker The entity tracker.
	 * @return A set of all network listeners.
	 */
	public static Set<PlayerAssociatedNetworkHandler> getListeners(ServerChunkLoadingManager.EntityTracker entityTracker) {
		return ((AccessorServerChunkLoadingManager.EntityTracker) entityTracker).getListeners();
	}

}
