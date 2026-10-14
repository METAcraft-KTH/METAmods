package metacraft.ovvar.clienttest.mixin;

import eu.pb4.polymer.networking.impl.client.ClientPacketRegistry;
import eu.pb4.polymer.networking.impl.packets.HelloS2CPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The test client has Polymer on its classpath, and a server treats a client as Polymer-aware the
 * moment it answers the server's hello. Swallowing the hello keeps the server on the vanilla path
 * for this client — the real pack, the real shaders — which is what the screenshots must judge.
 * Test source set only; never ships.
 */
@Mixin(ClientPacketRegistry.class)
public abstract class PolymerHelloMixin {
	@Inject(method = "handleHello", at = @At("HEAD"), cancellable = true)
	private static void ovvar$stayVanilla(Minecraft client, ClientCommonPacketListenerImpl listener, HelloS2CPayload payload, CallbackInfo ci) {
		ci.cancel();
	}
}
