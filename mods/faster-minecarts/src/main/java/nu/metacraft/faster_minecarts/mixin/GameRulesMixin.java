package nu.metacraft.faster_minecarts.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.faster_minecarts.FasterMinecartsConfig;

@Mixin(GameRules.class)
public class GameRulesMixin {

	@ModifyExpressionValue(
			method = "<clinit>",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/flag/FeatureFlagSet;of(Lnet/minecraft/world/flag/FeatureFlag;)Lnet/minecraft/world/flag/FeatureFlagSet;"
			)
	)
	private static FeatureFlagSet removeFeatureFlagRequirement(FeatureFlagSet original) {
		if (original.contains(FeatureFlags.MINECART_IMPROVEMENTS) && FasterMinecartsConfig.getConfig().experimentalMinecartMode().isEnabled()) {
			return original.subtract(FeatureFlagSet.of(FeatureFlags.MINECART_IMPROVEMENTS));
		}
		return original;
	}

}
