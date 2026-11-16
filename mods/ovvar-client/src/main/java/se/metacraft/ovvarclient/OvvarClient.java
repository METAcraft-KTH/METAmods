package se.metacraft.ovvarclient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The client side of ovvar's combinations: {@link ClientCombos} draws them, {@link Handshake} tells an ovvar
 * server so. The handshake needs Fabric API's networking; without it the mod still patches shaderpacks and
 * fills in any combination a pack lacks, and the server pushes its pack as it does for a vanilla client.
 */
public final class OvvarClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		if (!FabricLoader.getInstance().isModLoaded("fabric-networking-api-v1")) {
			ClientCombos.LOGGER.info("[ovvar-client] no Fabric API: ovvar servers will send their combination pack as usual");
			return;
		}
		try {
			Handshake.init();
		} catch (RuntimeException | LinkageError e) {
			// Never fail startup: without the handshake the server just pushes the pack.
			ClientCombos.LOGGER.warn("[ovvar-client] could not set up the ovvar handshake", e);
		}
	}
}
