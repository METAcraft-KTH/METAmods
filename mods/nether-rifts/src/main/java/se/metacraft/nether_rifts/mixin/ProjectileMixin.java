package se.metacraft.nether_rifts.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import se.metacraft.nether_rifts.mob.CrewHitbox;
import se.metacraft.nether_rifts.mob.GhastPart;
import se.metacraft.nether_rifts.mob.WarGhast;
import se.metacraft.nether_rifts.raid.RiftMobs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * What a Chained Ghast and its crew shoot (fireballs, bombs, the crossbow piglin's bolts) passes through the ghast, its
 * deck and its crew; a rift ghast's crossbow bolts pass through every rift mob too.
 */
@Mixin(Projectile.class)
public abstract class ProjectileMixin {

	@Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
	private void netherRifts$passOwnGhast(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		var owner = ((Projectile) (Object) this).getOwner();
		var ghast = owner instanceof WarGhast w ? w : owner instanceof GhastPart part ? part.ghast() : null;
		if (ghast == null) {
			return;
		}
		if (entity == ghast || entity instanceof GhastPart part && part.ghast() == ghast
				|| owner instanceof CrewHitbox && RiftMobs.isRiftMob(ghast) && RiftMobs.isRiftMob(entity)) {
			cir.setReturnValue(false);
		}
	}
}
