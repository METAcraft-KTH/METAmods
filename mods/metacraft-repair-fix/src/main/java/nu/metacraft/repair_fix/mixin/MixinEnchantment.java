package nu.metacraft.repair_fix.mixin;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.entry.RegistryEntry;
import nu.metacraft.repair_fix.RepairFixConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Enchantment.class)
public class MixinEnchantment {

	@Inject(method = "canBeCombined", at = @At("HEAD"), cancellable = true)
	private static void anvilBugfixIsCompatibleWith(
			RegistryEntry<Enchantment> first, RegistryEntry<Enchantment> second,
			CallbackInfoReturnable<Boolean> cir
	) {
		RepairFixConfig.getConfig().enchantmentConfigReader.getFirstMatch(
				first, second
		).ifPresent(match -> {
			cir.setReturnValue(match.result().shouldAllow());
		});
	}

}
