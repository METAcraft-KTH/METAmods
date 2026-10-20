package metacraft.ovvar.compat.danse.mixin;

import de.tomalbrc.danse.GestureController;
import de.tomalbrc.danse.poly.GestureCameraHolder;
import metacraft.ovvar.compat.danse.DanseHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The end of a gesture: put the ovve back.
 *
 * <p>Danse finishes by re-sending the player's <em>raw</em> equipment to every watcher. For an ovve
 * wearer with nothing on their feet that means an empty feet slot — and the feet slot is where
 * ovvar smuggles the legs' second dye channel as virtual cuffs. Ovvar's cuff cache still believes
 * those watchers have them, so the leg patches riding in that channel would stay dark. Forgetting
 * the wearer makes the next tick send the cuffs again, and the equipment re-send puts the ovve and
 * the companion top back pointing at the right assets.
 */
@Mixin(value = GestureController.class, remap = false)
public abstract class GestureControllerMixin {

	@Inject(method = "onStop(Lde/tomalbrc/danse/poly/GestureCameraHolder;)V", at = @At("TAIL"))
	private static void ovvar$dressAgain(GestureCameraHolder camera, CallbackInfo ci) {
		DanseHooks.gestureEnded(camera.getPlayer());
	}
}
