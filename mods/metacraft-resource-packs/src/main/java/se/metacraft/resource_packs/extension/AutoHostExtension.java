package se.metacraft.resource_packs.extension;

import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;

import java.util.Set;

public interface AutoHostExtension {

	void metacraft$addPackets(Set<ClientboundResourcePackPushPacket> packets);

}
