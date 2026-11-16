package se.metacraft.ovvar.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import se.metacraft.ovvar.OvvarConfig;
import se.metacraft.ovvar.content.MobOvvar;

/**
 * Mob ovvar for piglins, whose spawn roll is their own: the golden leggings or chestplate a piglin
 * would put on is sometimes an ovve or a frack instead.
 */
@Mixin(Piglin.class)
public abstract class PiglinOvvarMixin {
	@WrapOperation(method = "maybeWearArmor", at = @At(value = "INVOKE", target =
			"Lnet/minecraft/world/entity/monster/piglin/Piglin;setItemSlot(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V"))
	private void ovvar$mobOvvar(Piglin self, EquipmentSlot slot, ItemStack stack, Operation<Void> original, @Local(argsOnly = true) RandomSource random) {
		original.call(self, slot, MobOvvar.spawnPiece(self, slot, stack, random, OvvarConfig.get().mobOvvar()));
	}
}
