package nu.metacraft.nether_rifts.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Where a BIL animation player is: its frame counter, which counts down to the animation's end (a loop's counter is
 * set back to its length there). Two players of animations as long, with the same counter, show the same frame.
 */
@Mixin(targets = "de.tomalbrc.bil.core.component.AnimationComponent$AnimationPlayer", remap = false)
public interface AnimationPlayerAccessor {
	@Accessor("frameCounter")
	int netherRifts$frameCounter();

	@Accessor("frameCounter")
	void netherRifts$setFrameCounter(int frameCounter);
}
