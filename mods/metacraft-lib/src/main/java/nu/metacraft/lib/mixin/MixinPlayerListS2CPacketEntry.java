package nu.metacraft.lib.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.lib.util.helper.CustomNameHelper;

@Mixin(PlayerListS2CPacket.Entry.class)
public class MixinPlayerListS2CPacketEntry {

	@Mutable
	@Shadow @Final @Nullable private GameProfile profile;

	@Mutable
	@Shadow @Final @Nullable private Text displayName;

	@Inject(method = "<init>(Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("RETURN"))
	public void init(ServerPlayerEntity player, CallbackInfo ci) {
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
				displayName = Text.literal(name.get());
			}
		}
	}

}
