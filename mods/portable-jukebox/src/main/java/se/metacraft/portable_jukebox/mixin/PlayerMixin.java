package se.metacraft.portable_jukebox.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.level.Level;
import se.metacraft.portable_jukebox.ender_chest.PlayerEnderChestContainerExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class PlayerMixin {

	@Shadow
	protected PlayerEnderChestContainer enderChestInventory;

	@Inject(method = "<init>", at = @At("RETURN"))
	public void init(Level level, GameProfile gameProfile, CallbackInfo ci) {
		((PlayerEnderChestContainerExtension) enderChestInventory).metacraft$setPlayer((Player) (Object) this);
	}

}
