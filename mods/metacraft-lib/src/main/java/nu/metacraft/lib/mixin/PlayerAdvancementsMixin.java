package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.lib.extensions.ServerPlayerEntityExtensions;

@Mixin(PlayerAdvancements.class)
public class PlayerAdvancementsMixin {

	@Shadow private ServerPlayer player;

	@ModifyExpressionValue(
		method = "method_53637", //Lambda inside grantCriterion
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"
		)
	)
	public boolean grantCriterion(boolean original) {
		if (!((ServerPlayerEntityExtensions) player).metacraft_lib$getAnnounceAdvancements()) {
			return false;
		}
		return original;
	}

}
