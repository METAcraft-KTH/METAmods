package se.metacraft.resource_packs;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.server.MinecraftServer;

import java.util.function.Consumer;

/**
 * Like {@link EarlyPacksCallback}, for packs that are not in the config (see {@link ServePackCallback}):
 * the packets are sent while the player joins, together with the configured packs, and are not
 * remembered in the player's pack data.
 */
public interface EarlyPackPacketsCallback {

	Event<EarlyPackPacketsCallback> EVENT = EventFactory.createArrayBacked(
			EarlyPackPacketsCallback.class,
			events -> (server, profile, packetAdder) -> {
				for (var event : events) {
					event.addPackets(server, profile, packetAdder);
				}
			}
	);

	void addPackets(MinecraftServer server, GameProfile profile, Consumer<ClientboundResourcePackPushPacket> packetAdder);

}
