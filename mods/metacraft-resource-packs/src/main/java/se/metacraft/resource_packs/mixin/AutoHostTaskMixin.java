package se.metacraft.resource_packs.mixin;

import eu.pb4.polymer.autohost.impl.AutoHostTask;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import se.metacraft.resource_packs.extension.AutoHostExtension;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@Pseudo
@Mixin(AutoHostTask.class)
public class AutoHostTaskMixin implements AutoHostExtension {

	@Shadow
	@Final
	private Set<UUID> waitingFor;
	@Unique
	private Set<ClientboundResourcePackPushPacket> extraPacks;

	@Inject(method = "start", at = @At(value = "TAIL"))
	public void addPacks(Consumer<Packet<?>> sender, CallbackInfo ci) {
		sendExtraPacks(sender);
	}

	/**
	 * If Polymer's pack wasn't ready yet, start returns early and the packs are sent from tick instead,
	 * once, right before hasDelayed is set to false.
	 */
	@Inject(method = "tick", at = @At(value = "FIELD", target = "Leu/pb4/polymer/autohost/impl/AutoHostTask;hasDelayed:Z", opcode = Opcodes.PUTFIELD))
	public void addDelayedPacks(Consumer<Packet<?>> sender, CallbackInfo ci) {
		sendExtraPacks(sender);
	}

	@Unique
	private void sendExtraPacks(Consumer<Packet<?>> sender) {
		if (extraPacks != null) {
			for (var pack : extraPacks) {
				sender.accept(pack);
			}
		}
	}

	@Override
	public void metacraft$addPackets(Set<ClientboundResourcePackPushPacket> packets) {
		this.extraPacks = packets;
		for (var pack : packets) {
			waitingFor.add(pack.id());
		}
	}
}
