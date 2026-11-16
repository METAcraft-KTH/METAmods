package se.metacraft.ovvar.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import se.metacraft.ovvar.content.enchantment.OvveEnchantments;
import se.metacraft.ovvar.content.enchantment.PatchworkProtection;

import java.util.stream.Stream;

/**
 * A switched-off ovve enchantment (or the curse without Brewery) is never picked from a pool of
 * candidates: the enchanting table and {@code enchant_with_levels} both draw from
 * {@code getAvailableEnchantmentResults}.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {
	@ModifyVariable(method = "getAvailableEnchantmentResults", at = @At("HEAD"), argsOnly = true)
	private static Stream<Holder<Enchantment>> ovvar$onlyOffered(Stream<Holder<Enchantment>> source) {
		return source.filter(OvveEnchantments::offered);
	}

	/** Patchwork Protection adds to vanilla's protection sum; {@code CombatRules} caps the total at 20. */
	@ModifyReturnValue(method = "getDamageProtection", at = @At("RETURN"))
	private static float ovvar$patchwork(float protection, ServerLevel level, LivingEntity entity, DamageSource source) {
		return protection + PatchworkProtection.protection(entity, source);
	}
}
