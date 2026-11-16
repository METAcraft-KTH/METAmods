package se.metacraft.ovvarclient;

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import se.metacraft.ovvarclient.combo.ComboAssets;

/**
 * On every connection this client registers a configuration-phase receiver on
 * {@code ovvar:draws_combos/v<protocol>}, and Fabric lists it to the server before the join packs go out.
 * An ovvar server on the same protocol then sends it whole combinations and no combination pack; any other
 * server ignores the channel. Nothing is ever sent on it. If the asset manager's mixin never ran
 * ({@link ClientCombos#ready}) there is no handshake, and the server pushes the pack as it does for vanilla.
 */
final class Handshake {
	private Handshake() {}

	/** Never sent; only its registration matters. */
	record DrawsCombos() implements CustomPacketPayload {
		static final Type<DrawsCombos> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ComboAssets.NAMESPACE, ComboAssets.CHANNEL_PATH));
		static final StreamCodec<FriendlyByteBuf, DrawsCombos> CODEC = StreamCodec.unit(new DrawsCombos());

		@Override
		public Type<DrawsCombos> type() {
			return TYPE;
		}
	}

	static void init() {
		PayloadTypeRegistry.clientboundConfiguration().register(DrawsCombos.TYPE, DrawsCombos.CODEC);
		ClientConfigurationConnectionEvents.INIT.register((handler, client) -> {
			if (!ClientCombos.ready()) {
				ClientCombos.LOGGER.warn("[ovvar-client] the equipment asset hook is not active; ovvar servers will send their combination pack");
				return;
			}
			ClientConfigurationNetworking.registerReceiver(DrawsCombos.TYPE, (payload, context) -> {});
		});
	}
}
