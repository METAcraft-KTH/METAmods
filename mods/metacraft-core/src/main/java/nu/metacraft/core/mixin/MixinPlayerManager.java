package nu.metacraft.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import nu.metacraft.core.preferences.PreferenceData;

@Mixin(PlayerList.class)
public class MixinPlayerManager {

	@Inject(
		method = "placeNewPlayer", at = @At("RETURN")
	)
	public void onPlayerConnect(Connection connection, ServerPlayer player, CommonListenerCookie clientData, CallbackInfo ci) {
		PreferenceData.getForPlayer(player).initDefaultValues(player);
	}

}
