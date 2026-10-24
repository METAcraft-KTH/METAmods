package se.metacraft.moredyes.mixin;

import com.mojang.authlib.GameProfile;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import se.metacraft.moredyes.armor.PackClock;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Notes when a client says it has loaded our pack: that is when its animated textures start from their first frame. */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
	@Shadow
	protected abstract GameProfile playerProfile();

	@Inject(method = "handleResourcePackResponse", at = @At("HEAD"))
	private void moredyes$packLoaded(ServerboundResourcePackPacket packet, CallbackInfo ci) {
		if (packet.action() == ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED && packet.id().equals(PolymerResourcePackUtils.getMainUuid())) {
			PackClock.loaded(playerProfile().id(), System.currentTimeMillis());
		}
	}
}
