package se.datasektionen.mc.metacraft_lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.metacraft_lib.util.helper.CustomNameHelper;
import se.datasektionen.mc.metacraft_lib.util.helper.PlayerDataHelper;

@Mixin(PlayerManager.class)
public class MixinPlayerManager {

	@ModifyExpressionValue(
		method = "getPlayer(Ljava/lang/String;)Lnet/minecraft/server/network/ServerPlayerEntity;",
		at = @At(
			value = "INVOKE",
			target = "Ljava/lang/String;equalsIgnoreCase(Ljava/lang/String;)Z"
		)
	)
	public boolean checkPlayer(boolean original, String name, @Local ServerPlayerEntity player) {
		var customName = CustomNameHelper.getCustomName(player);
		if (customName.isPresent() && name.equalsIgnoreCase(customName.get())) {
			return true;
		}
		return original;
	}

	@WrapWithCondition(
		method = "onPlayerConnect",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/server/PlayerManager;broadcast(Lnet/minecraft/text/Text;Z)V"
		)
	)
	public boolean shouldAnnounceJoin(
			PlayerManager manager, Text message, boolean overlay, @Local(argsOnly = true) ServerPlayerEntity player
	) {
		return PlayerDataHelper.getAnnounceJoinLeave(player);
	}

}
