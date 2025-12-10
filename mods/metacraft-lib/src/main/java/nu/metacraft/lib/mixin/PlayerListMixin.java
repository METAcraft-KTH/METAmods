package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.lib.util.helper.CustomNameHelper;
import nu.metacraft.lib.util.helper.PlayerDataHelper;

@Mixin(PlayerList.class)
public class PlayerListMixin {

	@ModifyExpressionValue(
		method = "getPlayerByName(Ljava/lang/String;)Lnet/minecraft/server/level/ServerPlayer;",
		at = @At(
			value = "INVOKE",
			target = "Ljava/lang/String;equalsIgnoreCase(Ljava/lang/String;)Z"
		)
	)
	public boolean checkPlayer(boolean original, String name, @Local ServerPlayer player) {
		var customName = CustomNameHelper.getCustomName(player);
		if (customName.isPresent() && name.equalsIgnoreCase(customName.get())) {
			return true;
		}
		return original;
	}

	@WrapWithCondition(
		method = "placeNewPlayer",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"
		)
	)
	public boolean shouldAnnounceJoin(
			PlayerList manager, Component message, boolean overlay, @Local(argsOnly = true) ServerPlayer player
	) {
		return PlayerDataHelper.getAnnounceJoinLeave(player);
	}

}
