package nu.metacraft.resource_packs.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import nu.metacraft.resource_packs.extension.ConnectionExtension;
import nu.metacraft.resource_packs.extension.ServerPlayerExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.server.network.config.PrepareSpawnTask$Ready")
public class PrepareSpawnTaskReadyMixin {

	@Inject(
		method = "spawn",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/server/players/PlayerList;placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)V"
		)
	)
	public void spawn(
			Connection connection, CommonListenerCookie cookie,
			CallbackInfoReturnable<ServerPlayer> cir,
			@Local ServerPlayer player
	) {
		((ConnectionExtension) connection).metacraft$getAddedPacks().forEach(pack -> {
			((ServerPlayerExtension) player).metacraft$updatePackData(packs -> {
				return packs.addPack(pack, false);
			});
		});
	}

}
