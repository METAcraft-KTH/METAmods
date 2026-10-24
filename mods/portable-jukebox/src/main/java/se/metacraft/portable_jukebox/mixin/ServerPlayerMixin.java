package se.metacraft.portable_jukebox.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import se.metacraft.portable_jukebox.ender_chest.PlayerEnderChestContainerExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Player {

	public ServerPlayerMixin(Level level, GameProfile gameProfile) {
		super(level, gameProfile);
	}

	@Inject(method = "restoreFrom", at = @At("RETURN"))
	public void restoreFrom(ServerPlayer oldPlayer, boolean restoreAll, CallbackInfo ci) {
		((PlayerEnderChestContainerExtension) enderChestInventory).metacraft$setPlayer(this);
	}

}
