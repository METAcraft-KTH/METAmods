package nu.metacraft.core.mixin;

import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.core.preferences.PreferenceData;

@Mixin(PlayerManager.class)
public class MixinPlayerManager {

	@Inject(
		method = "onPlayerConnect", at = @At("RETURN")
	)
	public void onPlayerConnect(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData clientData, CallbackInfo ci) {
		PreferenceData.getForPlayer(player).initDefaultValues(player);
	}

}
