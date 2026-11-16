package se.metacraft.ovvar.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.metacraft.ovvar.content.enchantment.SpilledDrink;

/**
 * Curse of the Spilled Drink for Brewery's drinks, which carry no {@code potion_contents} (SpilledDrinkMixin):
 * while {@code DrinkItem.finishUsingItem} runs, the effects its drink type gives through
 * {@code ConsumptionEffect$Potion} are collected, and when it returns they are handed to the curse like a
 * potion's. By name, {@link Pseudo}: no compile dependency on Brewery, and nothing applies without it.
 */
@Pseudo
@Mixin(targets = "eu.pb4.brewery.item.DrinkItem")
public abstract class BreweryDrinkMixin {
	@Inject(method = "finishUsingItem", at = @At("HEAD"))
	private void ovvar$drinkStarts(ItemStack stack, Level level, LivingEntity drinker, CallbackInfoReturnable<ItemStack> info) {
		SpilledDrink.breweryDrinkStarts();
	}

	@Inject(method = "finishUsingItem", at = @At("RETURN"))
	private void ovvar$drinkEnds(ItemStack stack, Level level, LivingEntity drinker, CallbackInfoReturnable<ItemStack> info) {
		SpilledDrink.breweryDrinkEnds(drinker);
	}

	/** One of a Brewery drink type's potion effects, as it is given. */
	@Pseudo
	@Mixin(targets = "eu.pb4.brewery.drink.ConsumptionEffect$Potion")
	public abstract static class PotionEffect {
		@WrapOperation(method = "apply", at = @At(value = "INVOKE",
				target = "Lnet/minecraft/world/entity/LivingEntity;forceAddEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)V"))
		private void ovvar$effect(LivingEntity entity, MobEffectInstance effect, Entity source, Operation<Void> original) {
			SpilledDrink.breweryEffect(effect);
			original.call(entity, effect, source);
		}
	}
}
