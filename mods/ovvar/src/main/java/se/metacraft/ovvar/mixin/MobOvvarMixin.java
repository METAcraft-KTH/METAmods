package se.metacraft.ovvar.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import se.metacraft.ovvar.OvvarConfig;
import se.metacraft.ovvar.content.MobOvvar;

/**
 * Mob ovvar: the armour vanilla's spawn roll hands a zombie, skeleton or the like is sometimes an
 * ovve (leggings) or a frack (chestplate) instead, rolled with the same random the spawn uses.
 */
@Mixin(Mob.class)
public abstract class MobOvvarMixin {
	@WrapOperation(method = "populateDefaultEquipmentSlots", at = @At(value = "INVOKE", target =
			"Lnet/minecraft/world/entity/Mob;setItemSlot(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V"))
	private void ovvar$mobOvvar(Mob self, EquipmentSlot slot, ItemStack stack, Operation<Void> original, @Local(argsOnly = true) RandomSource random) {
		original.call(self, slot, MobOvvar.spawnPiece(self, slot, stack, random, OvvarConfig.get().mobOvvar()));
	}

	/**
	 * A mob's ovve or frack stays on it: picking up better armour (a piglin's gold included, which
	 * calls this too) must not swap it out, or the garment would end up on the ground.
	 */
	@Inject(method = "equipItemIfPossible", at = @At("HEAD"), cancellable = true)
	private void ovvar$keepGarment(ServerLevel level, ItemStack candidate, CallbackInfoReturnable<ItemStack> cir) {
		Mob self = (Mob) (Object) this;
		EquipmentSlot slot = self.getEquipmentSlotForItem(candidate);
		if ((slot == EquipmentSlot.LEGS || slot == EquipmentSlot.CHEST) && MobOvvar.is(self.getItemBySlot(slot))) {
			cir.setReturnValue(ItemStack.EMPTY);
		}
	}

	/** The patches a mob wore sometimes drop when a player kills it. */
	@Inject(method = "dropCustomDeathLoot", at = @At("HEAD"))
	private void ovvar$dropPatches(ServerLevel level, DamageSource source, boolean killedByPlayer, CallbackInfo ci) {
		MobOvvar.onDeath((Mob) (Object) this, level, source, killedByPlayer);
	}
}
