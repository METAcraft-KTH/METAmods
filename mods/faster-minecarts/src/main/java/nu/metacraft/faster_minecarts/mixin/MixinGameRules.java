package nu.metacraft.faster_minecarts.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.faster_minecarts.FasterMinecartsConfig;

@Mixin(GameRules.class)
public class MixinGameRules {

	@ModifyExpressionValue(
			method = "<clinit>",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/resource/featuretoggle/FeatureSet;of(Lnet/minecraft/resource/featuretoggle/FeatureFlag;)Lnet/minecraft/resource/featuretoggle/FeatureSet;"
			)
	)
	private static FeatureSet removeFeatureFlagRequirement(FeatureSet original) {
		if (original.contains(FeatureFlags.MINECART_IMPROVEMENTS) && FasterMinecartsConfig.getConfig().experimentalMinecartMode().isEnabled()) {
			return original.subtract(FeatureSet.of(FeatureFlags.MINECART_IMPROVEMENTS));
		}
		return original;
	}

}
