package nu.metacraft.nether_rifts.mixin;

import net.minecraft.world.entity.monster.Ghast;
import nu.metacraft.nether_rifts.mob.WarGhast;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Chained Ghast never shoots by vanilla's goal: its fireballs are its tactics' volleys ({@link
 * nu.metacraft.nether_rifts.mob.GhastTactics}), only while its spear piglin is alive (stabbing it is what makes it
 * shoot), and never while it bombs. Vanilla's goal checks this to start and to go on.
 */
@Mixin(targets = "net.minecraft.world.entity.monster.Ghast$GhastShootFireballGoal")
public abstract class GhastShootFireballGoalMixin {

	@Shadow
	@Final
	private Ghast ghast;

	@Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
	private void netherRifts$notForWarGhasts(CallbackInfoReturnable<Boolean> cir) {
		if (ghast instanceof WarGhast) {
			cir.setReturnValue(false);
		}
	}
}
