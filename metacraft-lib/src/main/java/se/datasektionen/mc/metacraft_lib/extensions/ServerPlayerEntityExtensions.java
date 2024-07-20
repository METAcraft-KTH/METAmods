package se.datasektionen.mc.metacraft_lib.extensions;

import net.minecraft.server.network.ServerPlayerEntity;
import se.datasektionen.mc.metacraft_lib.util.helper.CustomNameHelper;

public interface ServerPlayerEntityExtensions {

	/**
	 * Please use {@link CustomNameHelper#setCustomName(ServerPlayerEntity, String)} instead.
	 * @param customName The custom name to set.
	 */
	void METAcraft_Moderation$setCustomName(String customName);

	/**
	 * Please use {@link CustomNameHelper#getCustomName(ServerPlayerEntity)} instead.
	 * @return The custom name.
	 */
	String METAcraft_Moderation$getCustomName();

}
