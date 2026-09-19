package metacraft.ovvar.mixin;

import metacraft.ovvar.content.Ownership;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Somebody else's ovve does not go on: the two questions vanilla asks before an armour slot takes an
 * item answer no. {@link LivingEntity#isEquippableInSlot} is what {@code ArmorSlot.mayPlace} asks,
 * so it covers every inventory path — dragging it in, clicking it in, shift-clicking it (the quick
 * move only fills slots that may place it), a hopper or another mod using the slot — and
 * {@code canEquipWithDispenser} covers a dispenser aimed at a player. Right-clicking it is
 * {@link metacraft.ovvar.content.OvveItem#use}, which refuses there with a word in chat; anything
 * that skips all three ({@code /item replace}, {@code setItemSlot}) is caught by the wearer's next
 * tick, which takes it off again.
 *
 * Only players are asked: an armour stand or a mannequin wears anybody's ovve, which is what makes
 * a sewing stand and a showcase work.
 */
@Mixin(LivingEntity.class)
public abstract class ForeignOvveMixin {
	@Inject(method = "isEquippableInSlot", at = @At("HEAD"), cancellable = true)
	private void ovvar$refuseForeignOvve(ItemStack stack, EquipmentSlot slot, CallbackInfoReturnable<Boolean> info) {
		if ((Object) this instanceof Player player && Ownership.blocksWearing(player, stack)) info.setReturnValue(false);
	}

	@Inject(method = "canEquipWithDispenser", at = @At("HEAD"), cancellable = true)
	private void ovvar$refuseDispensedForeignOvve(ItemStack stack, CallbackInfoReturnable<Boolean> info) {
		if ((Object) this instanceof Player player && Ownership.blocksWearing(player, stack)) info.setReturnValue(false);
	}
}
