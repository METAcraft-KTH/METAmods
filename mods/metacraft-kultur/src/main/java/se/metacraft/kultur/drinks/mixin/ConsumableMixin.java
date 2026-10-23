package se.metacraft.kultur.drinks.mixin;

import se.metacraft.kultur.drinks.DrinkEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.metacraft.kultur.drinks.DrinksMixinPlugin;

/**
 * Finishing a drink, bread or milk: there is no Fabric event for it. At the head, while the stack is
 * still whole and before its own consume effects (as Brewery's drink runs before its effects; milk's
 * clearing then takes Inebriated off until its next refresh, like any other effect). Applied only
 * without Brewery ({@link DrinksMixinPlugin}).
 */
@Mixin(Consumable.class)
public abstract class ConsumableMixin {
	@Inject(method = "onConsume", at = @At("HEAD"))
	private void kultur$drink(Level level, LivingEntity user, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
		if (!level.isClientSide() && user instanceof ServerPlayer player) DrinkEffects.consumed(player, stack);
	}
}
