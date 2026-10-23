package se.metacraft.nether_rifts.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Ghast;
import se.metacraft.nether_rifts.raid.RiftMobs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A rift ghast floats and shoots on its own AI. Vanilla would hand control to its piglin rider, whose ground
 * pathing can't fly a ghast, and the ghast would stall.
 */
@Mixin(Mob.class)
public abstract class MobMixin {

	@Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true)
	private void netherRifts$riftGhastsSteerThemselves(CallbackInfoReturnable<LivingEntity> cir) {
		if ((Object) this instanceof Ghast && RiftMobs.isRiftMob((Entity) (Object) this)) {
			cir.setReturnValue(null);
		}
	}
}
