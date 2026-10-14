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
 * A Chained Ghast shoots only while its spear piglin is alive (stabbing it is what makes it shoot). Vanilla's goal
 * checks this to start and to go on, so a charge stops when the spear piglin dies.
 */
@Mixin(targets = "net.minecraft.world.entity.monster.Ghast$GhastShootFireballGoal")
public abstract class GhastShootFireballGoalMixin {

	@Shadow
	@Final
	private Ghast ghast;

	@Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
	private void netherRifts$onlyWithSpear(CallbackInfoReturnable<Boolean> cir) {
		if (ghast instanceof WarGhast warGhast && !warGhast.shoots()) {
			cir.setReturnValue(false);
		}
	}
}
