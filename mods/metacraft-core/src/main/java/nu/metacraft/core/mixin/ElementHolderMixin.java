package nu.metacraft.core.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

@Mixin(ElementHolder.class)
public class ElementHolderMixin {

	@Unique
	private static final int MAX = 4096;

	@WrapOperation(
			method = {"addElement", "startWatching(Lnet/minecraft/server/network/ServerGamePacketListenerImpl;)Z"},
			at = @At(
					value = "NEW",
					target = "(Ljava/lang/Iterable;)Lnet/minecraft/network/protocol/game/ClientboundBundlePacket;"
			)
	)
	public ClientboundBundlePacket skipBundleIfTooBig(
			Iterable<Packet<? super ClientGamePacketListener>> packets, Operation<ClientboundBundlePacket> original,
			@Local ArrayList<Packet<? super ClientGamePacketListener>> packetList
	) {
		if (packetList.size() > MAX) {
			return null;
		}
		return original.call(packets);
	}

	@WrapWithCondition(
		method = {"addElement", "startWatching(Lnet/minecraft/server/network/ServerGamePacketListenerImpl;)Z"},
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"
		)
	)
	public boolean splitPackets(
			ServerGamePacketListenerImpl handler, Packet<?> packet,
			@Local ArrayList<Packet<? super ClientGamePacketListener>> packets
	) {
		if (packet == null) {
			int count = packets.size() / MAX;
			for (int i = 0; i <= count; i++) {
				handler.send(new ClientboundBundlePacket(
						packets.subList(MAX * i, Math.min(MAX * (i+1), packets.size()))
				));
			}
			return false;
		}
		return true;
	}

}
