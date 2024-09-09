package se.datasektionen.mc.resource_packs.mixin;

import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.resource_packs.ResourcePackConfig;
import se.datasektionen.mc.resource_packs.ResourcePackHelper;
import se.datasektionen.mc.resource_packs.ServerPlayerEntityExtension;

@Mixin(PlayerManager.class)
public class MixinPlayerManager {

	@Inject(method = "onPlayerConnect", at = @At("RETURN"))
	public void onPlayerConnect8(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData clientData, CallbackInfo ci) {
		var config = ResourcePackConfig.getConfig();
		((ServerPlayerEntityExtension) player).metacraft$getResourcePacks().removeIf(pack -> {
			if (config.resourcePackExists(pack)) {
				ResourcePackHelper.enableResourcePack(player, pack);
				return false;
			} else {
				return true;
			}
		});
	}

}
