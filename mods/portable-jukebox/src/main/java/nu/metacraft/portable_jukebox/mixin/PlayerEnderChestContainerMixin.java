package nu.metacraft.portable_jukebox.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import nu.metacraft.portable_jukebox.ender_chest.PlayerEnderChestContainerExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEnderChestContainer.class)
public class PlayerEnderChestContainerMixin implements PlayerEnderChestContainerExtension {

	@Unique
	private Player player;

	@Override
	public void metacraft$setPlayer(Player player) {
		this.player = player;
	}

	@Override
	public Player metacraft$getPlayer() {
		return player;
	}
}
