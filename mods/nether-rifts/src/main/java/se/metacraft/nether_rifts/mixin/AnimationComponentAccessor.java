package se.metacraft.nether_rifts.mixin;

import de.tomalbrc.bil.core.component.AnimationComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * BIL's animator has no getter for where an animation is: its players, by name, so WarGhast can start one loop in step
 * with another ({@link AnimationPlayerAccessor}).
 */
@Mixin(value = AnimationComponent.class, remap = false)
public interface AnimationComponentAccessor {
	@Accessor("animationMap")
	Map<String, Object> netherRifts$players();
}
