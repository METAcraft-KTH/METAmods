package nu.metacraft.pause.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import nu.metacraft.pause.PauseData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public class PlayerMixin {

	@WrapOperation(
			method = "rideTick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/player/Player;stopRiding()V"
			)
	)
	public void stopRiding(Player player, Operation<Void> original) {
		Entity vehicleToRemount = null;
		if (!player.level().isClientSide() && (Object) this instanceof ServerPlayer) {
			var data = PauseData.getInstance(player.level().getServer());
			if (data.isPaused() && PauseData.shouldFreezeWhenPaused((ServerPlayer) player)) {
				vehicleToRemount = player.getVehicle();
			}
		}
		original.call(player);
		if (vehicleToRemount != null) {
			player.startRiding(vehicleToRemount);
		}
	}

}
