package se.metacraft.nether_rifts.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import se.metacraft.nether_rifts.mob.GhastPart;
import se.metacraft.nether_rifts.mob.WarGhast;
import se.metacraft.nether_rifts.raid.RiftMobs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A rift's shots fly through its own side: what its mobs shoot (bolts, fireballs, bombs; what the Piglin General turns
 * back is his) passes through the rift's mobs and its Chained Ghasts' decks and crews, and with its shooter gone, a
 * fireball or bomb marked with the rift still does. What a Chained Ghast and its crew shoot passes through the ghast,
 * its deck and its crew, in a rift or not.
 */
@Mixin(Projectile.class)
public abstract class ProjectileMixin {

	@Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
	private void netherRifts$passOwnSide(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		var projectile = (Projectile) (Object) this;
		var owner = projectile.getOwner();
		if (RiftMobs.sameRift(owner != null ? owner : projectile, entity)) {
			cir.setReturnValue(false);
			return;
		}
		var ghast = owner instanceof WarGhast w ? w : owner instanceof GhastPart part ? part.ghast() : null;
		if (ghast != null && (entity == ghast || entity instanceof GhastPart part && part.ghast() == ghast)) {
			cir.setReturnValue(false);
		}
	}
}
