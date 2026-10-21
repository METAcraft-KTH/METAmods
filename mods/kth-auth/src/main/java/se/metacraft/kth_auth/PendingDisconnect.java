package se.metacraft.kth_auth;

import net.minecraft.network.chat.Component;

/**
 * Implemented on {@link net.minecraft.server.network.ServerCommonPacketListenerImpl} via mixin.
 */
public interface PendingDisconnect {
	/**
	 * Disconnect the client with the given reason once it responds to a {@link KTHAuthTask#DISCONNECT_PING_ID} ping.
	 */
	void kth_auth$disconnectOnPong(Component reason);
}
