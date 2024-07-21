package se.datasektionen.mc.metacraft_core.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.Entity;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.metacraft_core.entity.entities.PlayerMusicPoint;

import java.util.function.Consumer;

@Mixin(EntityTrackerEntry.class)
public class MixinEntityTrackerEntry {

	@Shadow @Final private Entity entity;

	@WrapWithCondition(
			method = "startTracking",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/network/EntityTrackerEntry;sendPackets(Lnet/minecraft/server/network/ServerPlayerEntity;Ljava/util/function/Consumer;)V"
			)
	)
	public boolean startTrackingPacketSend(
			EntityTrackerEntry instance, ServerPlayerEntity player, Consumer<Packet<ClientPlayPacketListener>> sender
	) {
		return !(this.entity instanceof PlayerMusicPoint);
	}

	@WrapWithCondition(
		method = "stopTracking",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"
		)
	)
	public boolean stopTrackingPacketSend(
			ServerPlayNetworkHandler instance, Packet<?> packet,
			@Local(argsOnly = true) ServerPlayerEntity player
	) {
		return !(this.entity instanceof PlayerMusicPoint);
	}

}
