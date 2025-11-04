package nu.metacraft.lib.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.lib.util.helper.CustomNameHelper;

@Mixin(ClientboundPlayerInfoUpdatePacket.Entry.class)
public class MixinPlayerListS2CPacketEntry {

	@Mutable
	@Shadow @Final @Nullable private GameProfile profile;

	@Mutable
	@Shadow @Final @Nullable private Component displayName;

	@Inject(method = "<init>(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("RETURN"))
	public void init(ServerPlayer player, CallbackInfo ci) {
		var name = CustomNameHelper.getCustomName(player);
		if (name.isPresent()) {
			if (profile != null) {
				var oldProfile = profile;
				profile = new GameProfile(
						oldProfile.id(), name.get(), new PropertyMap(
								oldProfile.properties()
						)
				);
			}
			if (displayName != null) {
				displayName = Component.literal(name.get());
			}
		}
	}

}
