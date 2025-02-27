package se.datasektionen.mc.metacraft_lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.metacraft_lib.extensions.ServerPlayerEntityExtensions;

@Mixin(PlayerAdvancementTracker.class)
public class MixinPlayerAdvancementTracker {

	@Shadow private ServerPlayerEntity owner;

	@ModifyExpressionValue(
		method = "method_53637", //Lambda inside grantCriterion
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/world/GameRules;getBoolean(Lnet/minecraft/world/GameRules$Key;)Z"
		)
	)
	public boolean grantCriterion(boolean original) {
		if (!((ServerPlayerEntityExtensions) owner).metacraft_lib$getAnnounceAdvancements()) {
			return false;
		}
		return original;
	}

}
