package nu.metacraft.portable_jukebox.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.portable_jukebox.entity.PortableJukeboxEntity;

@Mixin(EntityTrackerEntry.class)
public class MixinEntityTrackerEntry {

	@Shadow @Final private Entity entity;

	@WrapWithCondition(
		method = "startTracking",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"
		)
	)
	public boolean startTracking(ServerPlayNetworkHandler instance, Packet packet) {
		return !(entity instanceof PortableJukeboxEntity);
	}

	@WrapWithCondition(
			method = "stopTracking",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"
			)
	)
	public boolean stopTracking(ServerPlayNetworkHandler instance, Packet packet) {
		return !(entity instanceof PortableJukeboxEntity);
	}

}
