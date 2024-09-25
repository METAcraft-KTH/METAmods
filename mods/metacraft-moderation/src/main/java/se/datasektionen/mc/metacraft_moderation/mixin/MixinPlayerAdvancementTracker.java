package se.datasektionen.mc.metacraft_moderation.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.metacraft_moderation.PlayerModerationState;

@Mixin(PlayerAdvancementTracker.class)
public class MixinPlayerAdvancementTracker {

	@Shadow private ServerPlayerEntity owner;

	@ModifyExpressionValue(
		method = "method_53637",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/advancement/AdvancementDisplay;shouldAnnounceToChat()Z"
		)
	)
	public boolean skipAnnounceForModerator(boolean original) {
		var state = PlayerModerationState.getPlayerState(owner);
		if (original && state.isPresent()) {
			return state.get().getDef().announceAdvancements();
		}
		return original;
	}

}
