package nu.metacraft.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.datafix.fixes.GameRuleRegistryFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.throwables.MixinError;

@Mixin(GameRuleRegistryFix.class)
public abstract class GameRuleRegistryFixMixin {

	@Shadow
	private static Dynamic<?> convertBoolean(Dynamic<?> dynamic) {
		throw new MixinError("Not applied");
	}

	@ModifyReturnValue(
			method = "lambda$makeRule$9",
			at = @At("RETURN")
	)
	private static Dynamic<?> addRules(Dynamic<?> original) {
		return original.renameAndFixField(
				"doArmorDamage", "metacraft:armor_damage",
				GameRuleRegistryFixMixin::convertBoolean
		).renameAndFixField(
				"fireworkBoostingEnabled", "metacraft:firework_boosting",
				GameRuleRegistryFixMixin::convertBoolean
		);
	}

}
