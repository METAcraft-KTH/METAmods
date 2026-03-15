package nu.metacraft.player_specific_scoreboards.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import nu.metacraft.player_specific_scoreboards.PlayerScoreboardExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class PlayerListMixin {

	@Inject(method = "placeNewPlayer", at = @At("RETURN"))
	public void placeNewPlayer(
			Connection connection, ServerPlayer serverPlayer,
			CommonListenerCookie commonListenerCookie, CallbackInfo ci
	) {
		((PlayerScoreboardExtension) serverPlayer).metacraft$updateScoreboard();
	}

}
