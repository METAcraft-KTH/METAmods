package se.metacraft.ovvar.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import se.metacraft.ovvar.content.enchantment.OvveEnchantments;

import java.util.List;

/**
 * {@code enchant_randomly} (loot books, the wandering trader's) picks uniformly from its candidates:
 * a switched-off ovve enchantment, or the curse without Brewery, is taken out of the list first.
 */
@Mixin(EnchantRandomlyFunction.class)
public abstract class EnchantRandomlyFunctionMixin {
	@ModifyArg(method = "run", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/util/Util;getRandomSafe(Ljava/util/List;Lnet/minecraft/util/RandomSource;)Ljava/util/Optional;"), index = 0)
	private List<Holder<Enchantment>> ovvar$onlyOffered(List<Holder<Enchantment>> candidates) {
		for (Holder<Enchantment> candidate : candidates) {
			if (!OvveEnchantments.offered(candidate)) return candidates.stream().filter(OvveEnchantments::offered).toList();
		}
		return candidates;
	}
}
