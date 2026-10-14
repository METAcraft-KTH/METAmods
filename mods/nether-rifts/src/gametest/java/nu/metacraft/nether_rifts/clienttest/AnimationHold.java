package nu.metacraft.nether_rifts.clienttest;

import de.tomalbrc.bil.api.AnimatedEntity;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Test only: holds a mob's one-shot animation on one frame (BIL plays 20 frames a second), so a screenshot
 * can't miss a swing that lasts a tick or two. Every server tick puts the animation back on that frame. BIL's
 * own pause won't do: a paused animation lets go of the bones and idle takes them back.
 */
final class AnimationHold {

	private static volatile Runnable held;
	private static boolean listening;

	private AnimationHold() {
	}

	/** Plays {@code animation} on every {@code type} mob in the overworld (one at least) and holds it on {@code frame}. */
	static void hold(TestServerContext server, EntityType<? extends Entity> type, String animation, int frame) {
		server.runOnServer(s -> {
			if (!listening) {
				listening = true;
				ServerTickEvents.END_SERVER_TICK.register(ticked -> {
					Runnable h = held;
					if (h != null) h.run();
				});
			}
			var mobs = s.overworld().getEntities(type, e -> true);
			if (mobs.isEmpty()) throw new AssertionError("no " + type);
			var animators = mobs.stream().map(mob -> ((AnimatedEntity) mob).getHolder().getAnimator()).toList();
			for (var animator : animators) {
				animator.playAnimation(animation, 10, true);
				animator.setAnimationFrame(animation, frame);
			}
			held = () -> animators.forEach(animator -> animator.setAnimationFrame(animation, frame));
		});
	}

	/** Lets the held animation play out. */
	static void release(TestServerContext server) {
		server.runOnServer(s -> held = null);
	}
}
