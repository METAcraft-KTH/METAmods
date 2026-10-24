package se.metacraft.nether_rifts.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import se.metacraft.nether_rifts.mob.WarGhast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A Chained Ghast's armour is for show: its enchantments (protection, thorns and the rest) do nothing for it. Every
 * enchantment effect of what a mob wears is looked up here.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {

	@WrapOperation(
			method = "runIterationOnEquipment",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;")
	)
	private static ItemStack netherRifts$armourForShow(LivingEntity owner, EquipmentSlot slot, Operation<ItemStack> original) {
		return WarGhast.wornForShow(owner, slot) ? ItemStack.EMPTY : original.call(owner, slot);
	}
}
