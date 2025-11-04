package nu.metacraft.portable_jukebox.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.portable_jukebox.entity.PortableJukeboxEntity;

@Mixin(ServerEntity.class)
public class MixinEntityTrackerEntry {

	@Shadow @Final private Entity entity;

	@WrapWithCondition(
		method = "addPairing",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"
		)
	)
	public boolean startTracking(ServerGamePacketListenerImpl instance, Packet packet) {
		return !(entity instanceof PortableJukeboxEntity);
	}

	@WrapWithCondition(
			method = "removePairing",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"
			)
	)
	public boolean stopTracking(ServerGamePacketListenerImpl instance, Packet packet) {
		return !(entity instanceof PortableJukeboxEntity);
	}

}
