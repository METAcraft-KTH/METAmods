package se.datasektionen.mc.metacraft_lib.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.metacraft_lib.util.helper.PlayerDataHelper;

@Mixin(ServerPlayNetworkHandler.class)
public class MixinServerPlayNetworkHandler {

	@Shadow public ServerPlayerEntity player;

	@WrapWithCondition(
		method = "cleanUp",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/server/PlayerManager;broadcast(Lnet/minecraft/text/Text;Z)V"
		)
	)
	private boolean shouldAnnounceLeave(PlayerManager instance, Text message, boolean overlay) {
		return PlayerDataHelper.getAnnounceJoinLeave(this.player);
	}

}
