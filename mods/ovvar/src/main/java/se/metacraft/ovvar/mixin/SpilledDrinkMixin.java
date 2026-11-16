package se.metacraft.ovvar.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.metacraft.ovvar.content.enchantment.SpilledDrink;

/** Curse of the Spilled Drink: after a potion is drunk, a cursed wearer's second dose is scheduled. */
@Mixin(PotionContents.class)
public abstract class SpilledDrinkMixin {
	@Inject(method = "onConsume", at = @At("TAIL"))
	private void ovvar$spilledDrink(Level level, LivingEntity entity, ItemStack stack, Consumable consumable, CallbackInfo ci) {
		SpilledDrink.onDrink(entity, (PotionContents) (Object) this, stack);
	}
}
