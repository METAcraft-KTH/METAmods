package se.datasektionen.mc.metacraft_core.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;

@Mixin(ElementHolder.class)
public class MixinElementHolder {

	@Unique
	private static final int MAX = 4096;

	@WrapOperation(
			method = {"addElement", "startWatching(Lnet/minecraft/server/network/ServerPlayNetworkHandler;)Z"},
			at = @At(
					value = "NEW",
					target = "(Ljava/lang/Iterable;)Lnet/minecraft/network/packet/s2c/play/BundleS2CPacket;"
			)
	)
	public BundleS2CPacket skipBundleIfTooBig(
			Iterable<Packet<? super ClientPlayPacketListener>> packets, Operation<BundleS2CPacket> original,
			@Local ArrayList<Packet<? super ClientPlayPacketListener>> packetList
	) {
		if (packetList.size() > MAX) {
			return null;
		}
		return original.call(packets);
	}

	@WrapWithCondition(
		method = {"addElement", "startWatching(Lnet/minecraft/server/network/ServerPlayNetworkHandler;)Z"},
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"
		)
	)
	public boolean splitPackets(
			ServerPlayNetworkHandler handler, Packet<?> packet,
			@Local ArrayList<Packet<? super ClientPlayPacketListener>> packets
	) {
		if (packet == null) {
			int count = packets.size() / MAX;
			for (int i = 0; i <= count; i++) {
				handler.sendPacket(new BundleS2CPacket(
						packets.subList(MAX * i, Math.min(MAX * (i+1), packets.size()))
				));
			}
			return false;
		}
		return true;
	}

}
