package se.metacraft.nether_rifts.mixin;

import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.ItemStack;
import se.metacraft.nether_rifts.raid.RiftMobs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rift piglins (and the Ghast Bombardier, rift or not) don't barter: gold handed to one neither pleases it nor takes it
 * out of the fight.
 */
@Mixin(PiglinAi.class)
public abstract class PiglinAiMixin {

	@Inject(method = "canAdmire", at = @At("HEAD"), cancellable = true)
	private static void netherRifts$noBartering(Piglin piglin, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (RiftMobs.keepsRiftRules(piglin)) {
			cir.setReturnValue(false);
		}
	}
}
