package nu.metacraft.core.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.level.Level;
import nu.metacraft.core.gamerules.METAcraftGameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FireworkRocketItem.class)
public class FireworkRocketItemMixin {

	@Inject(
			method = "use",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/player/Player;dropAllLeashConnections(Lnet/minecraft/world/entity/player/Player;)Z"
			),
			cancellable = true
	)
	public void use(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (level instanceof ServerLevel s && !s.getGameRules().get(METAcraftGameRules.FIREWORK_BOOSTING)) {
			player.displayClientMessage(
					Component.translatableWithFallback(
							"message.metacraft.gamerule.firework_boosting_disabled",
							"Firework boosting is disabled on this server"
					), true
			);
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}

}
